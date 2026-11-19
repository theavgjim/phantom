#!/usr/bin/env bash
# Builds and tests Phantom Client on Linux/macOS.
#   - always: Fabric mod jar + Java unit tests + launcher-core (.NET) unit tests
#   - PhantomClient.exe: only if the .NET SDK includes the WindowsDesktop (WPF) SDK, which
#     Microsoft's official SDK builds do (EnableWindowsTargeting). Otherwise use build.ps1 on
#     Windows or the GitHub Actions workflow.
# Optional: SELFTEST=1 ./build.sh  -> also runs the in-game self-test under xvfb-run.
#           GRADLE_ARGS="..."      -> extra Gradle arguments (e.g. -I mirror.gradle, --offline).
set -euo pipefail
cd "$(dirname "$0")"
step() { printf '\n\033[35m==> %s\033[0m\n' "$1"; }

step "Building the Fabric mod (Gradle)"
./gradlew ${GRADLE_ARGS:-} build
version=$(grep '^client_version=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')
jar="MinecraftIntegration/build/libs/phantom-client-${version}.jar"
mkdir -p dist PhantomLauncher/src/PhantomLauncher/Payload
cp "$jar" dist/
cp "$jar" PhantomLauncher/src/PhantomLauncher/Payload/phantom-client.jar

if [[ "${SELFTEST:-0}" == "1" ]]; then
  step "In-game self-test (xvfb)"
  xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew ${GRADLE_ARGS:-} :MinecraftIntegration:runSelftest
  cat MinecraftIntegration/run-selftest/phantom-selftest.json
fi

if command -v dotnet >/dev/null; then
  step "Testing launcher core (.NET)"
  dotnet test PhantomLauncher/tests/PhantomLauncher.Tests -c Release

  if ls "$(dotnet --list-sdks | tail -1 | sed -E 's/^([^ ]+) \[(.*)\]$/\2\/\1/')/Sdks/Microsoft.NET.Sdk.WindowsDesktop" >/dev/null 2>&1; then
    step "Publishing PhantomClient.exe (cross-compiled)"
    dotnet publish PhantomLauncher/src/PhantomLauncher -c Release -o dist/exe
    mv dist/exe/PhantomClient.exe dist/ && rm -rf dist/exe
  else
    echo "WindowsDesktop SDK not present in this .NET SDK: skipping PhantomClient.exe (build it with build.ps1 on Windows)."
  fi
else
  echo "dotnet not found: skipping launcher build/tests"
fi

step "Artifacts"
ls -la dist
