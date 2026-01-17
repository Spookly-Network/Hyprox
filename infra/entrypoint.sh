#!/bin/sh
set -e

CONFIG_PATH="${HYPROX_CONFIG_PATH:-/config/hyprox.yaml}"
CERT_DIR="${HYPROX_CERT_DIR:-/config/certs}"
CERT_FILE="${HYPROX_CERT_FILE:-$CERT_DIR/proxy.crt}"
KEY_FILE="${HYPROX_KEY_FILE:-$CERT_DIR/proxy.key}"
CERT_DAYS="${HYPROX_CERT_DAYS:-365}"
CERT_SUBJECT="${HYPROX_CERT_SUBJECT:-/CN=hyprox}"
REFERRAL_HMAC_FILE="${HYPROX_REFERRAL_HMAC_FILE:-/config/secret/referral_hmac}"

ensure_referral_hmac() {
    if [ -n "${HYPROX_REFERRAL_HMAC:-}" ]; then
        return
    fi
    if [ -f "$REFERRAL_HMAC_FILE" ]; then
        HYPROX_REFERRAL_HMAC=$(tr -d '\n' < "$REFERRAL_HMAC_FILE")
        if [ -z "$HYPROX_REFERRAL_HMAC" ]; then
            echo "Referral HMAC file is empty: $REFERRAL_HMAC_FILE" >&2
            exit 1
        fi
        export HYPROX_REFERRAL_HMAC
        return
    fi
    if ! command -v openssl >/dev/null 2>&1; then
        echo "openssl is required to generate referral HMAC secrets" >&2
        exit 1
    fi
    mkdir -p "$(dirname "$REFERRAL_HMAC_FILE")"
    old_umask=$(umask)
    umask 077
    openssl rand -hex 32 > "$REFERRAL_HMAC_FILE"
    umask "$old_umask"
    HYPROX_REFERRAL_HMAC=$(tr -d '\n' < "$REFERRAL_HMAC_FILE")
    export HYPROX_REFERRAL_HMAC
    echo "Generated referral HMAC at $REFERRAL_HMAC_FILE"
}

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

ensure_referral_hmac

if [ "$#" -eq 0 ]; then
    set -- --config "$CONFIG_PATH"
fi

exec java -cp /app/hyprox.jar:/app/runtime/* net.spookly.hyprox.HyproxMain "$@"
