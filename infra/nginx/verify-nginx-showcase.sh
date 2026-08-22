#!/bin/sh

set -eu

config_source="${1:?usage: verify-nginx-showcase.sh <nginx-config> <openssl-binary>}"
openssl_binary="${2:?usage: verify-nginx-showcase.sh <nginx-config> <openssl-binary>}"
verification_root="$(mktemp -d)"
verification_config="$verification_root/nginx.conf"
access_log="$verification_root/access.log"
nginx_started=0

cleanup() {
    if [ "$nginx_started" -eq 1 ]; then
        nginx -s quit -c "$verification_config" >/dev/null 2>&1 || true
    fi
    rm -rf "$verification_root"
}

trap cleanup EXIT INT TERM

cp "$config_source" "$verification_config"
printf '[]\n' > "$verification_root/assetlinks.json"
chmod 0755 "$verification_root"
chmod 0644 "$verification_root/assetlinks.json"

sed -i \
    -e 's#modules/ngx_otel_module.so#/usr/lib/nginx/modules/ngx_otel_module.so#g' \
    -e 's/nest-backend-green/localhost/g' \
    -e 's/nest-backend-blue/localhost/g' \
    -e 's/next-web/localhost/g' \
    -e 's/grafana/localhost/g' \
    -e 's/otelcollector/localhost/g' \
    -e "s#/etc/nginx/ssl/live/commonex.ru/fullchain.pem#$verification_root/fullchain.pem#g" \
    -e "s#/etc/nginx/ssl/live/commonex.ru/privkey.pem#$verification_root/privkey.pem#g" \
    -e "s#/var/log/nginx/access.log#$access_log#g" \
    -e "s#/var/www/assetlinks.json#$verification_root/assetlinks.json#g" \
    "$verification_config"

OPENSSL_CONF=/dev/null "$openssl_binary" req \
    -x509 \
    -newkey rsa:2048 \
    -nodes \
    -days 1 \
    -subj '/CN=commonex.ru' \
    -keyout "$verification_root/privkey.pem" \
    -out "$verification_root/fullchain.pem" \
    >/dev/null 2>&1

if ! id nginx >/dev/null 2>&1; then
    addgroup -S nginx
    adduser -D -S -H -G nginx nginx
fi
mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp /var/run/nginx
chown -R nginx:nginx /var/cache/nginx /var/run/nginx

effective_config="$(nginx -T -c "$verification_config" 2>&1)"

require_config() {
    expected="$1"
    if ! printf '%s\n' "$effective_config" | grep -Fq -- "$expected"; then
        echo "nginx showcase verification: missing effective configuration: $expected" >&2
        exit 1
    fi
}

require_count() {
    expected="$1"
    pattern="$2"
    actual="$(printf '%s\n' "$effective_config" | grep -Ec -- "$pattern" || true)"
    if [ "$actual" -ne "$expected" ]; then
        echo "nginx showcase verification: expected $expected matches for $pattern, found $actual" >&2
        exit 1
    fi
}

require_config 'resolver 127.0.0.11 valid=10s ipv6=off;'
require_config 'resolver_timeout 5s;'
require_config 'max_headers 200;'
require_config 'log_format showcase escape=json'
require_config '$ssl_curve'
require_config '$ssl_sigalg'
require_config '$ssl_sigalgs'
require_config '$ssl_early_data'
require_config '$ssl_session_reused'
require_config '$http3'
require_config '$upstream_response_time'
require_count 4 '^[[:space:]]*zone [^;]* 64k;'
require_count 6 '^[[:space:]]*server .* resolve'
require_count 2 '^[[:space:]]*least_time header inflight;'

nginx -c "$verification_config"
nginx_started=1

handshake_output="$(
    "$openssl_binary" s_client \
        -connect 127.0.0.1:443 \
        -servername commonex.ru \
        -tls1_3 \
        -groups X25519MLKEM768 \
        -CAfile "$verification_root/fullchain.pem" \
        -verify_return_error \
        -brief \
        </dev/null \
        2>&1
)"

printf '%s\n' "$handshake_output" | grep -Fq 'Negotiated TLS1.3 group: X25519MLKEM768'

printf 'GET /.well-known/assetlinks.json HTTP/1.1\r\nHost: commonex.ru\r\nConnection: close\r\n\r\n' \
    | "$openssl_binary" s_client \
        -connect 127.0.0.1:443 \
        -servername commonex.ru \
        -tls1_3 \
        -groups X25519MLKEM768 \
        -CAfile "$verification_root/fullchain.pem" \
        -verify_return_error \
        -quiet \
        > "$verification_root/http-response.txt" \
        2>&1 || true

grep -Fq 'HTTP/1.1 200 OK' "$verification_root/http-response.txt"

{
    printf 'GET /.well-known/assetlinks.json HTTP/1.1\r\nHost: commonex.ru\r\n'
    header_number=1
    while [ "$header_number" -le 220 ]; do
        printf 'X-Showcase-%s: value\r\n' "$header_number"
        header_number=$((header_number + 1))
    done
    printf 'Connection: close\r\n\r\n'
} | "$openssl_binary" s_client \
    -connect 127.0.0.1:443 \
    -servername commonex.ru \
    -tls1_3 \
    -groups X25519MLKEM768 \
    -CAfile "$verification_root/fullchain.pem" \
    -verify_return_error \
    -quiet \
    > "$verification_root/header-limit-response.txt" \
    2>&1 || true

grep -Fq '400 Bad Request' "$verification_root/header-limit-response.txt"

nginx -s quit -c "$verification_config"

wait_number=1
while [ -e /var/run/nginx/nginx.pid ] && [ "$wait_number" -le 50 ]; do
    sleep 0.1
    wait_number=$((wait_number + 1))
done
nginx_started=0

grep -Fq '"tls_protocol":"TLSv1.3"' "$access_log"
grep -Fq '"tls_curve":"X25519MLKEM768"' "$access_log"
grep -Fq '"tls_sigalg":"' "$access_log"
grep -Fq '"tls_client_sigalgs":"' "$access_log"
grep -Fq '"tls_early_data":"' "$access_log"
grep -Fq '"tls_session_reused":"' "$access_log"

echo 'nginx showcase verification: PASS'
