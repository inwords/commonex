[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-AndroidSdkRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $environmentCandidates = @(
        @{ Name = 'ANDROID_SDK_ROOT'; Value = $env:ANDROID_SDK_ROOT },
        @{ Name = 'ANDROID_HOME'; Value = $env:ANDROID_HOME }
    )

    foreach ($candidate in $environmentCandidates) {
        $value = $candidate.Value
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        if (-not (Test-Path -Path $value -PathType Container)) {
            throw "Environment variable '$($candidate.Name)' points to a missing directory: $value"
        }
        return (Resolve-Path -Path $value).Path
    }

    $localPropertiesPath = Join-Path -Path $ProjectRoot -ChildPath 'local.properties'
    if (-not (Test-Path -Path $localPropertiesPath -PathType Leaf)) {
        throw "Android SDK is not configured. Create '$localPropertiesPath' with sdk.dir=... or set ANDROID_SDK_ROOT."
    }

    $sdkLine = Get-Content -Path $localPropertiesPath |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1

    if ([string]::IsNullOrWhiteSpace($sdkLine)) {
        throw "Android SDK is not configured. '$localPropertiesPath' does not contain sdk.dir=..."
    }

    $sdkDir = $sdkLine.Substring('sdk.dir='.Length).Trim()
    $sdkDir = $sdkDir.Replace('\:', ':').Replace('\\', '\')

    if (-not (Test-Path -Path $sdkDir -PathType Container)) {
        throw "Android SDK directory does not exist: $sdkDir"
    }

    return (Resolve-Path -Path $sdkDir).Path
}

$projectRoot = (Resolve-Path -Path (Join-Path -Path $PSScriptRoot -ChildPath '..')).Path
$marathonPath = Join-Path -Path $projectRoot -ChildPath 'marathon\bin\marathon.bat'

if (-not (Test-Path -Path $marathonPath -PathType Leaf)) {
    throw "Marathon is not installed at '$marathonPath'. Extract the Marathon distribution into '$projectRoot\\marathon\\'."
}

$sdkRoot = Get-AndroidSdkRoot -ProjectRoot $projectRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

Write-Host "Using ANDROID_SDK_ROOT=$sdkRoot"

Push-Location -Path $projectRoot
try {
    if ($SkipBuild) {
        Write-Host 'Skipping autotest APK build.'
    } else {
        & .\gradlew ':app:assembleAutotest' ':app:assembleAutotestAndroidTest' '-Dcom.android.tools.r8.disableApiModeling=true'
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }

    & $marathonPath
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
