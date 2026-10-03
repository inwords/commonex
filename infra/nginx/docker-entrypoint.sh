#!/bin/sh
set -eu

fail() {
    echo "nginx environment configuration: $*" >&2
    exit 1
}

validate_host() {
    host="$1"
    [ "${#host}" -le 253 ] || fail "hostname is too long"
    case "$host" in
        ''|*[!a-z0-9.-]*|.*|*.|*..*) fail "invalid DNS hostname" ;;
    esac
    previous_ifs="$IFS"
    IFS=.
    for label in $host; do
        case "$label" in -*|*-) fail "invalid DNS label" ;; esac
        [ "${#label}" -le 63 ] || fail "DNS label is too long"
    done
    IFS="$previous_ifs"
}

render() {
    template_directory="$1"
    output="$2"
    COMMONEX_WEB_HOSTS="${COMMONEX_WEB_HOSTS-commonex.ru www.commonex.ru}"
    COMMONEX_API_HOST="${COMMONEX_API_HOST-dev-api.commonex.ru}"
    COMMONEX_GRPC_HOST="${COMMONEX_GRPC_HOST-grpc.commonex.ru}"
    COMMONEX_GRAFANA_HOST="${COMMONEX_GRAFANA_HOST-gf.commonex.ru}"

    carriage_return="$(printf '\r')"
    for value in "$COMMONEX_WEB_HOSTS" "$COMMONEX_API_HOST" "$COMMONEX_GRPC_HOST" "$COMMONEX_GRAFANA_HOST"; do
        case "$value" in *'
'*|*"$carriage_return"*) fail "hostnames cannot contain line breaks" ;; esac
    done
    COMMONEX_API_HOST="$(printf '%s' "$COMMONEX_API_HOST" | tr '[:upper:]' '[:lower:]')"
    COMMONEX_GRPC_HOST="$(printf '%s' "$COMMONEX_GRPC_HOST" | tr '[:upper:]' '[:lower:]')"
    COMMONEX_GRAFANA_HOST="$(printf '%s' "$COMMONEX_GRAFANA_HOST" | tr '[:upper:]' '[:lower:]')"
    validate_host "$COMMONEX_API_HOST"
    validate_host "$COMMONEX_GRPC_HOST"
    validate_host "$COMMONEX_GRAFANA_HOST"
    [ "$COMMONEX_API_HOST" != "$COMMONEX_GRPC_HOST" ] || fail "API and gRPC hostnames conflict"
    [ "$COMMONEX_API_HOST" != "$COMMONEX_GRAFANA_HOST" ] || fail "API and Grafana hostnames conflict"
    [ "$COMMONEX_GRPC_HOST" != "$COMMONEX_GRAFANA_HOST" ] || fail "gRPC and Grafana hostnames conflict"

    normalized_web_hosts=''
    separate_api=1
    set -f
    for web_host in $COMMONEX_WEB_HOSTS; do
        web_host="$(printf '%s' "$web_host" | tr '[:upper:]' '[:lower:]')"
        validate_host "$web_host"
        case " $normalized_web_hosts " in *" $web_host "*) fail "duplicate web hostname" ;; esac
        [ "$web_host" != "$COMMONEX_GRPC_HOST" ] || fail "web and gRPC hostnames conflict"
        [ "$web_host" != "$COMMONEX_GRAFANA_HOST" ] || fail "web and Grafana hostnames conflict"
        if [ "$web_host" = "$COMMONEX_API_HOST" ]; then separate_api=0; fi
        normalized_web_hosts="${normalized_web_hosts:+$normalized_web_hosts }$web_host"
    done
    [ -n "$normalized_web_hosts" ] || fail "at least one web hostname is required"
    COMMONEX_WEB_HOSTS="$normalized_web_hosts"

    umask 077
    fragment_directory="$(mktemp -d "$(dirname "$output")/commonex-nginx.XXXXXX")"
    COMMONEX_API_CONFIG="$fragment_directory/api.conf"
    export COMMONEX_WEB_HOSTS COMMONEX_API_HOST COMMONEX_GRPC_HOST COMMONEX_GRAFANA_HOST COMMONEX_API_CONFIG
    substitution_variables='$COMMONEX_WEB_HOSTS $COMMONEX_API_HOST $COMMONEX_GRPC_HOST $COMMONEX_GRAFANA_HOST $COMMONEX_API_CONFIG'
    if [ "$separate_api" -eq 1 ]; then
        envsubst "$substitution_variables" < "$template_directory/api.conf.template" > "$COMMONEX_API_CONFIG"
    else
        : > "$COMMONEX_API_CONFIG"
    fi
    envsubst "$substitution_variables" < "$template_directory/nginx.conf.template" > "$fragment_directory/nginx.conf"
    mv -f "$fragment_directory/nginx.conf" "$output"
}

if [ "${1-}" = '--render-only' ]; then
    [ "$#" -eq 3 ] || fail 'usage: --render-only <template-directory> <output-file>'
    render "$2" "$3"
    exit 0
fi

if [ "${1-}" = nginx ]; then
    for argument in "$@"; do
        case "$argument" in -v|-V|-h|'-?') exec "$@" ;; esac
    done
    render /etc/nginx/templates /tmp/commonex-nginx.conf
    nginx -t -c /tmp/commonex-nginx.conf
fi
exec "$@"
