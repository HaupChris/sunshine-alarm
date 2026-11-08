#!/usr/bin/env bash
# Builds a new version and optionally installs it as an UPDATE on the phone.
#
#   scripts/release.sh            bump patch (0.1.0 -> 0.1.1), build
#   scripts/release.sh minor      bump minor (0.1.3 -> 0.2.0), build
#   scripts/release.sh --install  ... and install via adb (USB or wireless debugging)
#
# The APK ends up in dist/ and can also be copied to the phone and opened there.
# Data is kept because: same applicationId, same signing key, higher versionCode,
# and Room migrations instead of a wiped database.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

BUMP="patch"
INSTALL=0
for arg in "$@"; do
    case "$arg" in
        major|minor|patch) BUMP="$arg" ;;
        --install) INSTALL=1 ;;
        *) echo "unknown argument: $arg"; exit 1 ;;
    esac
done

[[ -f keystore.properties ]] || { echo "keystore.properties missing, run scripts/create-keystore.sh"; exit 1; }

# shellcheck disable=SC1091
source <(grep -E '^VERSION_(CODE|NAME)=' version.properties)
IFS=. read -r MAJ MIN PAT <<< "$VERSION_NAME"
case "$BUMP" in
    major) MAJ=$((MAJ + 1)); MIN=0; PAT=0 ;;
    minor) MIN=$((MIN + 1)); PAT=0 ;;
    patch) PAT=$((PAT + 1)) ;;
esac
NEW_NAME="$MAJ.$MIN.$PAT"
NEW_CODE=$((VERSION_CODE + 1))

cp version.properties version.properties.bak
sed -i "s/^VERSION_CODE=.*/VERSION_CODE=$NEW_CODE/; s/^VERSION_NAME=.*/VERSION_NAME=$NEW_NAME/" version.properties
if ! ./gradlew --quiet testDebugUnitTest assembleRelease; then
    mv version.properties.bak version.properties
    echo "Build failed, version not bumped"
    exit 1
fi
rm version.properties.bak

mkdir -p dist
APK="dist/sunshine-alarm-$NEW_NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
echo "Built $APK (versionCode $NEW_CODE)"

if [[ $INSTALL -eq 1 ]]; then
    # -r = replace existing app, keeps its data
    "$ANDROID_HOME/platform-tools/adb" install -r "$APK"
fi
