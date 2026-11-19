<#
.SYNOPSIS
  Builds Phantom Client: the Fabric mod (Gradle) and PhantomClient.exe (WPF, self-contained).
.DESCRIPTION
  Output goes to .\dist :
    PhantomClient.exe          single-file launcher with the mod embedded
    phantom-client-<v>.jar     the mod on its own (for Prism/MultiMC/manual installs)
    SHA256SUMS.txt
  Requirements: Windows 10/11, .NET 8 SDK, JDK 21 (Gradle downloads everything else).
.EXAMPLE
  .\build.ps1
  .\build.ps1 -SkipTests
#>
param(
    [switch]$SkipTests,
    [string]$Configuration = "Release"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Magenta }

Step "Checking toolchain"
& java -version 2>&1 | Select-Object -First 1 | Write-Host
& dotnet --version | Write-Host

Step "Building the Fabric mod (Gradle)"
$gradleArgs = @("build")
if ($SkipTests) { $gradleArgs += "-x"; $gradleArgs += "test" }
& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$version = (Select-String -Path gradle.properties -Pattern '^client_version=(.+)$').Matches[0].Groups[1].Value.Trim()
$jar = "MinecraftIntegration\build\libs\phantom-client-$version.jar"
if (-not (Test-Path $jar)) { throw "Mod jar not found: $jar" }

Step "Embedding the mod into the launcher"
$payload = "PhantomLauncher\src\PhantomLauncher\Payload"
New-Item -ItemType Directory -Force $payload | Out-Null
Copy-Item $jar "$payload\phantom-client.jar" -Force

if (-not $SkipTests) {
    Step "Testing launcher core"
    & dotnet test PhantomLauncher\tests\PhantomLauncher.Tests -c $Configuration
    if ($LASTEXITCODE -ne 0) { throw "Launcher tests failed" }
}

Step "Publishing PhantomClient.exe"
if (Test-Path dist) { Remove-Item dist -Recurse -Force }
& dotnet publish PhantomLauncher\src\PhantomLauncher -c $Configuration -o dist
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed" }
Get-ChildItem dist -Exclude PhantomClient.exe | Remove-Item -Recurse -Force
Copy-Item $jar dist\ -Force

Get-ChildItem dist -File | ForEach-Object {
    "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
} | Set-Content dist\SHA256SUMS.txt

Step "Done"
Get-ChildItem dist | Format-Table Name, @{n = "Size (MB)"; e = { "{0:N1}" -f ($_.Length / 1MB) } }
