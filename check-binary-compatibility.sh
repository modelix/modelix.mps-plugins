#!/bin/sh

set -e
set -x

./gradlew :mps-legacy-sync-plugin:jar -Pmps.version=2021.1.4
VERSION=$(cat version.txt)
mkdir -p mps-legacy-sync-plugin/build/binary-compatibility
cp "mps-legacy-sync-plugin/build/libs/mps-legacy-sync-plugin-$VERSION.jar" "mps-legacy-sync-plugin/build/binary-compatibility/a.jar"
./gradlew :mps-legacy-sync-plugin:jar -Pmps.version=2023.2
cp "mps-legacy-sync-plugin/build/libs/mps-legacy-sync-plugin-$VERSION.jar" "mps-legacy-sync-plugin/build/binary-compatibility/b.jar"
./gradlew :mps-legacy-sync-plugin:checkBinaryCompatibility
