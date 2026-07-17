<#
.SYNOPSIS
    Packages GameRoute as a native Windows app (GameRoute.exe + bundled JRE)
    using the JDK's own jpackage tool -- no external installer toolchain
    (WiX etc.) required for this --type app-image output.

.DESCRIPTION
    Run `mvn package` first to produce target\gameroute.jar, then run this
    script. Output lands in target\dist\GameRoute\GameRoute.exe -- a
    self-contained folder you can copy anywhere. Being a real .exe (unlike
    running `java -jar ...`), Windows Explorer's right-click menu offers
    "Run as administrator" on it directly.

.PARAMETER JavaHome
    Path to a JDK 17+ install whose bin\jpackage.exe will be used. Defaults
    to $env:JAVA_HOME.
#>
param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"

if (-not $JavaHome) {
    throw "JAVA_HOME is not set and -JavaHome was not passed. Point it at a JDK 17+ install (jpackage ships with the JDK since 14)."
}

$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "target\gameroute.jar"
if (-not (Test-Path $jar)) {
    throw "target\gameroute.jar not found -- run 'mvn package' first."
}

$stagingDir = Join-Path $root "target\jpackage-input"
$destDir = Join-Path $root "target\dist"
$icon = Join-Path $root "packaging\gameroute.ico"

Write-Host "Staging jar for jpackage..."
Remove-Item -Recurse -Force $stagingDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $stagingDir | Out-Null
Copy-Item $jar $stagingDir

Write-Host "Removing previous build output (if any)..."
Remove-Item -Recurse -Force (Join-Path $destDir "GameRoute") -ErrorAction SilentlyContinue

$jpackage = Join-Path $JavaHome "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found under $JavaHome\bin -- need a full JDK (not just a JRE), version 17+."
}

& $jpackage `
    --type app-image `
    --name GameRoute `
    --input $stagingDir `
    --main-jar gameroute.jar `
    --main-class com.gameroute.Launcher `
    --icon $icon `
    --app-version 1.0.0 `
    --vendor "GameRoute" `
    --description "Network companion for League of Legends" `
    --dest $destDir

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "Done: $destDir\GameRoute\GameRoute.exe" -ForegroundColor Green
Write-Host "Right-click it and choose 'Run as administrator' to launch elevated." -ForegroundColor Green
