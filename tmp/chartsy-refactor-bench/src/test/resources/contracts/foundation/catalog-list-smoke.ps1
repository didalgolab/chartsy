param()

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..\..")).Path
$pom = Join-Path $projectRoot "pom.xml"

$json = mvn -q -f $pom -DskipTests exec:java "-Dexec.args=catalog list --format json"
if ($LASTEXITCODE -ne 0) {
    throw "catalog list smoke command failed with exit code $LASTEXITCODE"
}

$jsonText = [string] $json
$jsonStart = $jsonText.IndexOf('{')
if ($jsonStart -lt 0) {
    throw "catalog list smoke command did not emit a JSON payload"
}

$payload = $jsonText.Substring($jsonStart) | ConvertFrom-Json
if ($payload.status -ne "scaffold") {
    throw "Expected scaffold status, got '$($payload.status)'"
}

if ($payload.upstreamCliMainClass -ne "org.springaicommunity.bench.core.cli.BenchMain") {
    throw "Unexpected upstream CLI main class '$($payload.upstreamCliMainClass)'"
}

Write-Output "catalog-list smoke probe passed"
