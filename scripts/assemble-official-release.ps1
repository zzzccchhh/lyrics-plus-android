[CmdletBinding()]
param(
    [string]$StatsEndpoint = $env:LYRICS_PLUS_STATS_ENDPOINT,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$envFile = Join-Path $scriptDir "official-build.env"

if ([string]::IsNullOrWhiteSpace($StatsEndpoint) -and (Test-Path $envFile)) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match "^\s*LYRICS_PLUS_STATS_ENDPOINT\s*=\s*(.+?)\s*$") {
            $StatsEndpoint = $Matches[1].Trim().Trim('"').Trim("'")
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($StatsEndpoint)) {
    throw "Missing LYRICS_PLUS_STATS_ENDPOINT for official release build."
}

$env:ORG_GRADLE_PROJECT_lyricsPlusStatsEndpoint = $StatsEndpoint

Push-Location $repoRoot
try {
    & .\gradlew.bat assembleRelease @GradleArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

