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
if ($payload.status -ne "ok") {
    throw "Expected ok status, got '$($payload.status)'"
}

if ($payload.upstreamCliMainClass -ne "org.springaicommunity.bench.core.cli.BenchMain") {
    throw "Unexpected upstream CLI main class '$($payload.upstreamCliMainClass)'"
}

if ($payload.cases.Count -ne 1 -or $payload.cases[0].id -ne "double-tree-map") {
    throw "Expected the default catalog to expose the double-tree-map seed case"
}

if (-not $payload.seedOnly -or $payload.balancedV1Ready) {
    throw "Expected the default catalog to remain seed-only and not claim final balanced v1 readiness"
}

Write-Output "catalog-list smoke probe passed"
