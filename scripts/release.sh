#!/usr/bin/env bash
# Release CoLink: bump version, build, tag, push, create GitHub release, upload APK.
# Usage: ./scripts/release.sh <x.y.z>
set -euo pipefail

VERSION="${1:?Usage: $0 <x.y.z>}"
REPO="Predator04/Hermes-CoLink-Android"
DIR="/mnt/c/Users/Admin/Desktop/CoLink"
cd "$DIR"

# 1. Bump versionCode (+1) and versionName
CUR_CODE=$(grep -oP 'versionCode \K[0-9]+' app/build.gradle)
NEW_CODE=$((CUR_CODE + 1))
sed -i "s/versionCode [0-9]*/versionCode $NEW_CODE/" app/build.gradle
sed -i "s/versionName \".*\"/versionName \"$VERSION\"/" app/build.gradle
echo "→ versionCode $NEW_CODE, versionName $VERSION"

# 2. Build the signed release APK
export JAVA_HOME=/home/predator04/jdk-17.0.14+7
export ANDROID_HOME=/home/predator04/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleRelease --no-daemon -q
echo "→ release APK built"

# 3. Changelog since the previous tag (or all commits if none)
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
if [ -n "$LAST_TAG" ]; then
    CHANGELOG=$(git log --oneline "$LAST_TAG"..HEAD | sed 's/^/- /')
else
    CHANGELOG=$(git log --oneline HEAD | sed 's/^/- /')
fi

# 4. Commit + tag + push
git add app/build.gradle
git commit -q -m "release: v$VERSION (versionCode $NEW_CODE)"
git tag "v$VERSION"
git push -q
git push -q --tags
echo "→ tagged v$VERSION and pushed"

# 5. GitHub release + APK asset
APK="app/build/outputs/apk/release/app-release.apk"
gh release create "v$VERSION" "$APK#CoLink-v$VERSION.apk" \
  --repo "$REPO" \
  --title "CoLink v$VERSION" \
  --notes "## Changes

$CHANGELOG

## Install

Sideload \`CoLink-v$VERSION.apk\` on Android (allow unknown sources)."
echo "✓ Released v$VERSION with APK asset"
