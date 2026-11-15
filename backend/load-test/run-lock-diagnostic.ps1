param(
    [ValidateSet('art', 'point_account')]
    [string]$LockTarget,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DbUrl = $env:LOAD_TEST_DB_URL,
    [string]$DbUsername = $env:LOAD_TEST_DB_USERNAME,
    [string]$DbPassword = $env:LOAD_TEST_DB_PASSWORD,
    [string]$FixturePassword = $env:LOAD_TEST_FIXTURE_PASSWORD,
    [string]$Confirmation = $env:DAILYATELIER_LOAD_TEST_CONFIRM,
    [string]$MysqlPath = $env:MYSQL_CLIENT_PATH,
    [string]$K6Path = $env:K6_PATH,
    [int]$HoldSeconds = 55,
    [string]$RequestTimeout = '65s',
    [string]$ResultDirectory = $env:TEMP
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\load-test-guard.ps1"
$context = Assert-LoadTestTarget -DbUrl $DbUrl -Confirmation $Confirmation
if ([string]::IsNullOrWhiteSpace($FixturePassword)) { throw 'LOAD_TEST_FIXTURE_PASSWORD is required.' }
$MysqlPath = Resolve-MysqlClient $MysqlPath
if ([string]::IsNullOrWhiteSpace($K6Path)) {
    $k6Command = Get-Command k6 -ErrorAction SilentlyContinue
    if ($null -eq $k6Command) { throw 'k6 was not found.' }
    $K6Path = $k6Command.Source
}

$previousMysqlPassword = $env:MYSQL_PWD
$lockJob = $null
try {
    $env:MYSQL_PWD = $DbPassword
    Assert-GuardMarker -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath
    $artId = Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql "SELECT art_id FROM art WHERE name='LT-HOT-001'"
    if ($null -eq $artId) { throw 'LT-HOT-001 fixture was not found.' }
    $bidderId = 'load_bid_001'
    $lockSql = if ($LockTarget -eq 'art') {
        "START TRANSACTION; SELECT art_id FROM art WHERE art_id=$artId FOR UPDATE; SELECT 'LOCK_ACQUIRED'; DO SLEEP($HoldSeconds); ROLLBACK;"
    } else {
        "START TRANSACTION; SELECT user_id FROM point_account WHERE user_id='$bidderId' FOR UPDATE; SELECT 'LOCK_ACQUIRED'; DO SLEEP($HoldSeconds); ROLLBACK;"
    }
    $lockJob = Start-Job -ScriptBlock {
        param($Client, $HostName, $Port, $Username, $Password, $Schema, $Sql)
        $env:MYSQL_PWD = $Password
        & $Client -h $HostName -P $Port -u $Username "--database=$Schema" --batch --skip-column-names -e $Sql
        if ($LASTEXITCODE -ne 0) { throw 'MySQL lock session failed.' }
    } -ArgumentList $MysqlPath,$context.Host,$context.Port,$DbUsername,$DbPassword,$context.Schema,$lockSql

    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    $lockTable = if ($LockTarget -eq 'art') { 'art' } else { 'point_account' }
    do {
        Start-Sleep -Milliseconds 100
        $lockCount = Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql "SELECT COUNT(*) FROM performance_schema.data_locks WHERE object_schema='$($context.Schema)' AND object_name='$lockTable' AND lock_mode LIKE 'X%'"
    } while ($lockCount -eq '0' -and [DateTime]::UtcNow -lt $deadline -and $lockJob.State -eq 'Running')
    if ($lockCount -eq '0') {
        throw "Could not confirm the $LockTarget lock. Job state: $($lockJob.State)"
    }

    $env:BASE_URL = $BaseUrl
    $env:BIDDER_ID = $bidderId
    $env:ART_ID = [string]$artId
    $env:BID_PRICE = '1900000000'
    $env:REQUEST_TIMEOUT = $RequestTimeout
    $resultPath = Join-Path $ResultDirectory "dailyatelier-lock-$LockTarget-summary.json"
    $env:SUMMARY_PATH = $resultPath
    & $K6Path run "$PSScriptRoot\k6\bid-lock-diagnostic.js"
    $k6Exit = $LASTEXITCODE
    Wait-Job -Job $lockJob -Timeout ($HoldSeconds + 10) | Out-Null
    if ($lockJob.State -eq 'Running') { throw 'The diagnostic lock session did not finish.' }
    Receive-Job -Job $lockJob -ErrorAction Stop | Out-Null
    exit $k6Exit
} finally {
    if ($null -ne $lockJob) {
        if ($lockJob.State -eq 'Running') { Stop-Job -Job $lockJob }
        Remove-Job -Job $lockJob -Force -ErrorAction SilentlyContinue
    }
    if ($null -eq $previousMysqlPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousMysqlPassword }
    Remove-Item Env:BASE_URL,Env:BIDDER_ID,Env:ART_ID,Env:BID_PRICE,Env:REQUEST_TIMEOUT,Env:SUMMARY_PATH -ErrorAction SilentlyContinue
}
