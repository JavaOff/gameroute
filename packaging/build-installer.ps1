<#
.SYNOPSIS
    Builds GameRouteSetup.exe: a real Windows installer (MSI wrapped in a
    WiX Burn bootstrapper) that installs GameRoute into
    C:\Program Files\GameRoute, with Start Menu / Desktop shortcuts, an
    Add/Remove Programs entry (with icon), a local Uninstall.exe, and
    in-place upgrade support.

.DESCRIPTION
    Runs automatically as the last step of `mvn clean package` on Windows
    (see the "windows-installer" Maven profile) -- expects target\gameroute.jar
    to already exist. Pipeline from there: jpackage app-image (GameRoute.exe +
    bundled JRE) -> csc.exe (Uninstall.exe stub) -> heat.exe (harvest the
    app-image into WiX components) -> candle/light (Product.wxs ->
    GameRoute.msi) -> candle/light (Bundle.wxs -> GameRouteSetup.exe).

    Can also be run standalone after `mvn package` (see BUILD.md). Requires:
      - JDK 21 (jpackage) on -JavaHome / $env:JAVA_HOME
      - WiX Toolset v3 (candle/light/heat) on -WixHome
      - .NET Framework's csc.exe (ships with every Windows install)

.PARAMETER ProductVersion
    Four-part Windows version (Major.Minor.Build.Revision). Must increase
    for Windows Installer to treat a new build as an upgrade.

.PARAMETER SignCertificate / SignPassword
    Optional real code-signing certificate (.pfx) + password. When omitted,
    the build produces an UNSIGNED installer -- GameRoute never fabricates
    or self-signs a certificate. See "Code signing" in BUILD.md.
#>
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$WixHome = "$env:USERPROFILE\tools\wix314",
    [string]$ProductVersion = "1.0.0.0",
    [string]$SignCertificate = $env:GAMEROUTE_SIGN_CERT,
    [string]$SignPassword = $env:GAMEROUTE_SIGN_PASSWORD,
    [string]$TimestampUrl = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$installerSrc = Join-Path $root "installer"
$buildDir = Join-Path $root "target\installer-build"
$distDir = Join-Path $root "target\dist"
$appImageDir = Join-Path $distDir "GameRoute"

function Assert-Tool($path, $hint) {
    if (-not (Test-Path $path)) {
        throw "Required tool not found: $path`n$hint"
    }
}

Assert-Tool "$JavaHome\bin\jpackage.exe" "Set -JavaHome to a JDK 17+ install."
Assert-Tool "$WixHome\candle.exe" "Set -WixHome to a WiX Toolset v3 install (candle/light/heat/insignia)."
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
Assert-Tool $csc "Expected the .NET Framework C# compiler here; adjust the path in this script if your Windows install differs."

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

# This script is invoked automatically by Maven itself (see pom.xml's
# "windows-installer" profile, bound to the package phase, right after the
# shade plugin produces the fat jar) -- so gameroute.jar is expected to
# already exist. If you're running this script by hand, run `mvn package`
# first.
Write-Host "==> [1/6] Verify gameroute.jar exists" -ForegroundColor Cyan
$jarPath = Join-Path $root "target\gameroute.jar"
if (-not (Test-Path $jarPath)) {
    throw "target\gameroute.jar not found -- run 'mvn package' first (or just 'mvn clean package', which runs this script automatically on Windows)."
}

Write-Host "==> [2/6] jpackage app-image (GameRoute.exe + bundled JRE)" -ForegroundColor Cyan
& "$PSScriptRoot\package-exe.ps1" -JavaHome $JavaHome
if (-not (Test-Path "$appImageDir\GameRoute.exe")) { throw "jpackage did not produce GameRoute.exe" }

Write-Host "==> [3/6] Compile Uninstall.exe" -ForegroundColor Cyan
$uninstallExe = Join-Path $buildDir "Uninstall.exe"
& $csc /nologo /target:winexe /out:$uninstallExe /win32icon:"$installerSrc\..\packaging\gameroute.ico" "$installerSrc\Uninstall.cs"
if ($LASTEXITCODE -ne 0) { throw "csc failed to build Uninstall.exe" }

Write-Host "==> [4/6] Harvest app-image with heat.exe" -ForegroundColor Cyan
$appFilesWxs = Join-Path $buildDir "AppFiles.wxs"
& "$WixHome\heat.exe" dir $appImageDir `
    -cg AppFiles -dr INSTALLFOLDER -var var.HarvestDir `
    -ag -srd -sfrag -scom -sreg -nologo `
    -out $appFilesWxs
if ($LASTEXITCODE -ne 0) { throw "heat.exe harvest failed" }

# The "launch GameRoute" finish-page checkbox needs GameRoute.exe's harvested
# File Id. Extracted here (not hardcoded in Product.wxs) so it can never go
# stale if the harvest ever changes; heat's ID generation is a deterministic
# hash of the source path, so this is stable across rebuilds of the same tree.
$exeFileLine = Select-String -Path $appFilesWxs -Pattern 'Id="(fil[0-9A-F]+)" KeyPath="yes" Source="\$\(var\.HarvestDir\)\\GameRoute\.exe"'
if (-not $exeFileLine) { throw "Could not find GameRoute.exe's harvested File Id in $appFilesWxs" }
$gameRouteExeFileId = $exeFileLine.Matches[0].Groups[1].Value
Write-Host "    GameRoute.exe File Id: $gameRouteExeFileId"

Write-Host "==> [5/6] candle + light -> GameRoute.msi" -ForegroundColor Cyan
$iconFile = Join-Path $root "packaging\gameroute.ico"
$licenseFile = Join-Path $installerSrc "License.rtf"

& "$WixHome\candle.exe" -nologo -arch x64 `
    -dHarvestDir="$appImageDir" `
    -dProductVersion="$ProductVersion" `
    -dIconFile="$iconFile" `
    -dLicenseFile="$licenseFile" `
    -dUninstallExeFile="$uninstallExe" `
    -dGameRouteExeFileId="$gameRouteExeFileId" `
    -ext "$WixHome\WixUIExtension.dll" `
    -ext "$WixHome\WixUtilExtension.dll" `
    -out "$buildDir\" `
    "$installerSrc\Product.wxs" $appFilesWxs
if ($LASTEXITCODE -ne 0) { throw "candle (Product) failed" }

$msiPath = Join-Path $buildDir "GameRoute.msi"
& "$WixHome\light.exe" -nologo `
    -ext "$WixHome\WixUIExtension.dll" `
    -ext "$WixHome\WixUtilExtension.dll" `
    -cultures:en-us `
    -sice:ICE61 `
    -out $msiPath `
    "$buildDir\Product.wixobj" "$buildDir\AppFiles.wixobj"
if ($LASTEXITCODE -ne 0) { throw "light (Product) failed" }

Write-Host "==> [6/6] candle + light -> GameRouteSetup.exe" -ForegroundColor Cyan
& "$WixHome\candle.exe" -nologo `
    -dProductVersion="$ProductVersion" `
    -dIconFile="$iconFile" `
    -dLicenseFile="$licenseFile" `
    -dProductMsi="$msiPath" `
    -ext "$WixHome\WixBalExtension.dll" `
    -out "$buildDir\" `
    "$installerSrc\Bundle.wxs"
if ($LASTEXITCODE -ne 0) { throw "candle (Bundle) failed" }

$setupExe = Join-Path $distDir "GameRouteSetup.exe"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
& "$WixHome\light.exe" -nologo `
    -ext "$WixHome\WixBalExtension.dll" `
    -out $setupExe `
    "$buildDir\Bundle.wixobj"
if ($LASTEXITCODE -ne 0) { throw "light (Bundle) failed" }

if ($SignCertificate) {
    Write-Host "==> Signing $setupExe" -ForegroundColor Cyan
    $signtool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $signtool) { throw "signtool.exe not found (install the Windows SDK) -- cannot sign as requested." }
    & $signtool sign /f $SignCertificate /p $SignPassword /fd SHA256 /tr $TimestampUrl /td SHA256 $setupExe
    if ($LASTEXITCODE -ne 0) { throw "signtool failed" }
} else {
    Write-Host ""
    Write-Host "NOTE: $setupExe is UNSIGNED." -ForegroundColor Yellow
    Write-Host "GameRoute never generates or applies a fake/self-signed certificate." -ForegroundColor Yellow
    Write-Host "To sign with your own real code-signing certificate, pass -SignCertificate/-SignPassword" -ForegroundColor Yellow
    Write-Host "(or set GAMEROUTE_SIGN_CERT / GAMEROUTE_SIGN_PASSWORD) -- see BUILD.md." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done: $setupExe" -ForegroundColor Green
