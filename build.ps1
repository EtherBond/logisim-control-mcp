param([string]$JarPath = $env:LOGISIM_JAR)
$ErrorActionPreference = 'Stop'
if (-not $JarPath) { throw 'Pass -JarPath or set LOGISIM_JAR to your Logisim-evolution 3.9.0 JAR.' }
$env:LOGISIM_JAR = (Resolve-Path -LiteralPath $JarPath).Path
& node (Join-Path $PSScriptRoot 'scripts/build.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
