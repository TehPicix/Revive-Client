<#
  Apply (or, with -Reverse, reverse) the analysis-mode patch against the launcher source.
  Works from any directory; operates on the launcher root (this script's parent's parent).
#>
[CmdletBinding()]
param([switch]$Reverse)

$ErrorActionPreference = 'Stop'
$here     = Split-Path -Parent $MyInvocation.MyCommand.Path
$launcher = Split-Path -Parent $here
$patch    = Join-Path $here 'analysis-mode.patch'

$flag = @()
if ($Reverse) { $flag = @('-R') }

Push-Location $launcher
try {
    & git apply --check @flag $patch 2>$null
    if ($LASTEXITCODE -ne 0) {
        if ($Reverse) { Write-Error 'Nothing to reverse: the source already looks clean.' }
        else          { Write-Error 'Patch does not apply cleanly. Is the source already patched, or modified?' }
    }
    & git apply @flag $patch
    if ($Reverse) { Write-Host 'Reversed: launcher restored to the clean reconstruction.' }
    else          { Write-Host 'Applied: launcher is now the analysis build. Reverse with: analysis-mode\apply.ps1 -Reverse' }
}
finally {
    Pop-Location
}
