#!/usr/bin/env bash
# Creates the one signing key used for every build of the app.
# Android only installs an update over an existing app if both are signed with
# the same key. Losing this key means: uninstall, reinstall, data gone.
# => BACK UP the .jks file and keystore.properties (e.g. password manager).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEY_DIR="${HOME}/.android-keys"
STORE="${KEY_DIR}/sunshine-alarm.jks"
PROPS="${ROOT}/keystore.properties"

if [[ -f "$STORE" ]]; then
    echo "Keystore exists already: $STORE (not touched)"
    exit 0
fi

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"

keytool -genkeypair -v \
    -keystore "$STORE" -storetype PKCS12 \
    -alias sunshine -keyalg RSA -keysize 4096 -validity 36500 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Sunshine Alarm" >/dev/null
chmod 600 "$STORE"

cat > "$PROPS" <<EOF
storeFile=${STORE}
storePassword=${PASS}
keyAlias=sunshine
keyPassword=${PASS}
EOF
chmod 600 "$PROPS"

echo "Created $STORE and $PROPS"
echo "Back up both files. Without them no update can be installed over the app."
