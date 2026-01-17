#!/bin/sh
set -e

CONFIG_PATH="${HYPROX_CONFIG_PATH:-/config/hyprox.yaml}"
CERT_DIR="${HYPROX_CERT_DIR:-/config/certs}"
CERT_FILE="${HYPROX_CERT_FILE:-$CERT_DIR/proxy.crt}"
KEY_FILE="${HYPROX_KEY_FILE:-$CERT_DIR/proxy.key}"
CERT_DAYS="${HYPROX_CERT_DAYS:-365}"
CERT_SUBJECT="${HYPROX_CERT_SUBJECT:-/CN=hyprox}"

if [ ! -f "$CERT_FILE" ] || [ ! -f "$KEY_FILE" ]; then
    mkdir -p "$CERT_DIR"
    if ! command -v openssl >/dev/null 2>&1; then
        echo "openssl is required to generate self-signed certs" >&2
        exit 1
    fi
    echo "Generating self-signed certificate at $CERT_FILE"
    openssl req -x509 -newkey rsa:2048 -nodes \
        -keyout "$KEY_FILE" \
        -out "$CERT_FILE" \
        -days "$CERT_DAYS" \
        -subj "$CERT_SUBJECT"
fi

if [ "$#" -eq 0 ]; then
    set -- --config "$CONFIG_PATH"
fi

exec java -cp /app/hyprox.jar:/app/runtime/* net.spookly.hyprox.HyproxMain "$@"
