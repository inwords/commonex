#!/bin/sh
set -eu

mv -f "$NGINX_ENVSUBST_OUTPUT_DIR/nginx.conf" /tmp/commonex-nginx.conf
nginx -t -c /tmp/commonex-nginx.conf
