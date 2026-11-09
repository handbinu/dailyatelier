param(
    [ValidateSet('hot', 'distributed', 'shared-account')]
    [string]$Scenario,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DbUrl = $env:LOAD_TEST_DB_URL,
    [string]$DbUsername = $env:LOAD_TEST_DB_USERNAME,
    [string]$DbPassword = $env:LOAD_TEST_DB_PASSWORD,
    [string]$FixturePassword = $env:LOAD_TEST_FIXTURE_PASSWORD,
    [string]$Confirmation = $env:DAILYATELIER_LOAD_TEST_CONFIRM,
    [string]$MysqlPath = $env:MYSQL_CLIENT_PATH,
    [string]$K6Path = $env:K6_PATH,
    [string]$ResultDirectory = $env:TEMP
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\load-test-guard.ps1"
$context = Assert-LoadTestTarget -DbUrl $DbUrl -Confirmation $Confirmation
if ([string]::IsNullOrWhiteSpace($FixturePassword)) { throw 'LOAD_TEST_FIXTURE_PASSWORD is required.' }
if ([string]::IsNullOrWhiteSpace($ResultDirectory)) { throw 'A result directory is required.' }

$MysqlPath = Resolve-MysqlClient $MysqlPath
if ([string]::IsNullOrWhiteSpace($K6Path)) {
    $k6Command = Get-Command k6 -ErrorAction SilentlyContinue
    if ($null -eq $k6Command) { throw 'k6 was not found.' }
    $K6Path = $k6Command.Source
}

$previousMysqlPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $DbPassword
    Assert-GuardMarker -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath
    $pattern = switch ($Scenario) {
        'hot' { 'LT-HOT-%' }
        'distributed' { 'LT-DIST-%' }
        'shared-account' { 'LT-SHARED-%' }
    }
    $artIds = Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql "SELECT art_id FROM art WHERE name LIKE '$pattern' ORDER BY name"
    if ($null -eq $artIds) { throw "No fixture art was found for scenario '$Scenario'." }

    $env:BASE_URL = $BaseUrl
    $env:BID_SCENARIO = $Scenario
    $env:ART_IDS = ($artIds -join ',')
    $env:BIDDER_COUNT = '32'
    $resultPath = Join-Path $ResultDirectory "dailyatelier-$Scenario-summary.json"
    $env:SUMMARY_PATH = $resultPath
    & $K6Path run "$PSScriptRoot\k6\bid-load.js"
    exit $LASTEXITCODE
} finally {
    if ($null -eq $previousMysqlPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousMysqlPassword }
    Remove-Item Env:BASE_URL,Env:BID_SCENARIO,Env:ART_IDS,Env:BIDDER_COUNT,Env:SUMMARY_PATH -ErrorAction SilentlyContinue
}
