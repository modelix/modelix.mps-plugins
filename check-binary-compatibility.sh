#!/bin/sh

set -e
set -x

mkdir -p build/binary-compatibility/mps-legacy-sync-plugin
./gradlew :mps-legacy-sync-plugin:clean :mps-legacy-sync-plugin:jar -Pmps.version=2021.1.4
VERSION=$(cat version.txt)
cp "mps-legacy-sync-plugin/build/libs/mps-legacy-sync-plugin-$VERSION.jar" "build/binary-compatibility/mps-legacy-sync-plugin/a.jar"
./gradlew :mps-legacy-sync-plugin:clean :mps-legacy-sync-plugin:jar -Pmps.version=2023.2
cp "mps-legacy-sync-plugin/build/libs/mps-legacy-sync-plugin-$VERSION.jar" "build/binary-compatibility/mps-legacy-sync-plugin/b.jar"
./gradlew :mps-legacy-sync-plugin:checkBinaryCompatibility

#mkdir -p build/binary-compatibility/mps-diff-plugin
#./gradlew :mps-diff-plugin:clean :mps-diff-plugin:jar -Pmps.version=2021.1.4
#cp "mps-diff-plugin/build/libs/mps-diff-plugin-$VERSION.jar" "build/binary-compatibility/mps-diff-plugin/a.jar"
#./gradlew :mps-diff-plugin:clean :mps-diff-plugin:jar -Pmps.version=2023.2
#cp "mps-diff-plugin/build/libs/mps-diff-plugin-$VERSION.jar" "build/binary-compatibility/mps-diff-plugin/b.jar"
#./gradlew :mps-diff-plugin:checkBinaryCompatibility
