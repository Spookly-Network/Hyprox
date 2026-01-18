#!/bin/sh
set -e

CONFIG_PATH="${HYPROX_CONFIG_PATH:-/config/hyprox.yaml}"
CERT_DIR="${HYPROX_CERT_DIR:-/config/certs}"
CERT_FILE="${HYPROX_CERT_FILE:-$CERT_DIR/proxy.crt}"
KEY_FILE="${HYPROX_KEY_FILE:-$CERT_DIR/proxy.key}"
CERT_DAYS="${HYPROX_CERT_DAYS:-365}"
CERT_SUBJECT="${HYPROX_CERT_SUBJECT:-/CN=hyprox}"
REFERRAL_HMAC_FILE="${HYPROX_REFERRAL_HMAC_FILE:-/config/secret/referral_hmac}"
SKIP_CHOWN="${HYPROX_SKIP_CHOWN:-}"

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

ensure_certs() {
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
}

run_java() {
    exec java -cp /app/hyprox.jar:/app/runtime/* net.spookly.hyprox.HyproxMain "$@"
}

if [ "$#" -eq 0 ]; then
    set -- --config "$CONFIG_PATH"
fi

ensure_certs
ensure_referral_hmac

if [ "$(id -u)" -eq 0 ]; then
    if [ -z "$SKIP_CHOWN" ]; then
        chown -R hyprox:hyprox /config 2>/dev/null || echo "WARNING: Failed to chown /config" >&2
    fi
    if command -v gosu >/dev/null 2>&1; then
        can_write_config=true
        if [ -d /config ]; then
            gosu hyprox test -w /config || can_write_config=false
        fi
        if [ -f "$CERT_FILE" ]; then
            gosu hyprox test -r "$CERT_FILE" || can_write_config=false
        fi
        if [ -f "$KEY_FILE" ]; then
            gosu hyprox test -r "$KEY_FILE" || can_write_config=false
        fi
        if [ "$can_write_config" = true ]; then
            exec gosu hyprox java -cp /app/hyprox.jar:/app/runtime/* net.spookly.hyprox.HyproxMain "$@"
        fi
        echo "WARNING: Falling back to root (config/cert permissions are not writable for hyprox)." >&2
        run_java
    fi
    echo "WARNING: gosu is missing; running as root." >&2
fi

run_java
