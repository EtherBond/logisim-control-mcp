param([string]$JarPath = $env:LOGISIM_JAR, [string]$Name = 'logisim-control', [switch]$Headless)
$ErrorActionPreference = 'Stop'
if (-not $JarPath) { throw 'Pass -JarPath or set LOGISIM_JAR.' }
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$env:LOGISIM_JAR = $resolvedJar
& node (Join-Path $PSScriptRoot 'scripts/build.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$existing = & codex mcp get $Name 2>$null
if ($LASTEXITCODE -eq 0) { throw "MCP '$Name' already exists. Inspect it, or choose a different -Name; this script will not overwrite it." }
$configArgs = @('mcp', 'add', $Name, '--env', "LOGISIM_JAR=$resolvedJar")
if ($Headless) { $configArgs += @('--env', 'LOGISIM_HEADLESS=true') }
$configArgs += @('--', 'node', (Join-Path $PSScriptRoot 'server/index.mjs'))
& codex @configArgs
if ($LASTEXITCODE -ne 0) { throw 'Codex MCP registration failed.' }
