param(
    [ValidateSet('hot', 'distributed', 'shared-account')]
    [string]$Scenario,
    [int]$ServerPid,
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DbUrl = $env:LOAD_TEST_DB_URL,
    [string]$DbUsername = $env:LOAD_TEST_DB_USERNAME,
    [string]$DbPassword = $env:LOAD_TEST_DB_PASSWORD,
    [string]$FixturePassword = $env:LOAD_TEST_FIXTURE_PASSWORD,
    [string]$Confirmation = $env:DAILYATELIER_LOAD_TEST_CONFIRM,
    [string]$MysqlPath = $env:MYSQL_CLIENT_PATH,
    [string]$JcmdPath = $env:JCMD_PATH,
    [string]$ResultDirectory = $env:TEMP
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\load-test-guard.ps1"
$context = Assert-LoadTestTarget -DbUrl $DbUrl -Confirmation $Confirmation
$MysqlPath = Resolve-MysqlClient $MysqlPath
if ([string]::IsNullOrWhiteSpace($JcmdPath)) {
    $jcmdCommand=Get-Command jcmd -ErrorAction SilentlyContinue
    $JcmdPath=if($null -ne $jcmdCommand){$jcmdCommand.Source}else{'C:\Users\sonyebin\.jdks\corretto-17.0.11\bin\jcmd.exe'}
}
if(-not(Test-Path -LiteralPath $JcmdPath)){throw "jcmd was not found: $JcmdPath"}
$previousMysqlPassword=$env:MYSQL_PWD
$env:MYSQL_PWD=$DbPassword
Assert-GuardMarker -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath

$job = Start-Job -ScriptBlock {
    param($Script,$ScenarioName,$Base,$Url,$User,$Password,$Fixture,$Confirm)
    $env:LOAD_TEST_DB_URL=$Url;$env:LOAD_TEST_DB_USERNAME=$User;$env:LOAD_TEST_DB_PASSWORD=$Password
    $env:LOAD_TEST_FIXTURE_PASSWORD=$Fixture;$env:DAILYATELIER_LOAD_TEST_CONFIRM=$Confirm
    & $Script -Scenario $ScenarioName -BaseUrl $Base
    if($LASTEXITCODE -ne 0){throw "k6 scenario failed with exit code $LASTEXITCODE"}
} -ArgumentList "$PSScriptRoot\run-bid-load.ps1",$Scenario,$BaseUrl,$DbUrl,$DbUsername,$DbPassword,$FixturePassword,$Confirmation

$samples=0;$artWaitSamples=0;$accountWaitSamples=0;$artPeak=0;$accountPeak=0;$maxWaitMs=0
$poolPeak=0;$poolActivePeak=0;$poolIdlePeak=0;$pendingStackSamples=0;$pendingStackPeak=0
try {
    while($job.State -eq 'Running') {
        $row=Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql @"
SELECT
 COALESCE(SUM(dl.object_name='art'),0),
 COALESCE(SUM(dl.object_name='point_account'),0),
 COALESCE(MAX(TIMESTAMPDIFF(MICROSECOND,trx.trx_wait_started,NOW(6))/1000),0)
FROM performance_schema.data_lock_waits w
JOIN performance_schema.data_locks dl ON dl.engine_lock_id=w.requesting_engine_lock_id
LEFT JOIN information_schema.innodb_trx trx ON trx.trx_id=w.requesting_engine_transaction_id
WHERE dl.object_schema='$($context.Schema)';
"@
        $values=($row -split "`t");$art=[int]$values[0];$account=[int]$values[1];$wait=[double]$values[2]
        $artPeak=[Math]::Max($artPeak,$art);$accountPeak=[Math]::Max($accountPeak,$account);$maxWaitMs=[Math]::Max($maxWaitMs,$wait)
        if($art -gt 0){$artWaitSamples++};if($account -gt 0){$accountWaitSamples++}
        $poolRow=Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql @"
SELECT COUNT(*),COALESCE(SUM(t.processlist_command<>'Sleep'),0),COALESCE(SUM(t.processlist_command='Sleep'),0)
FROM performance_schema.threads t
JOIN performance_schema.session_connect_attrs a ON a.processlist_id=t.processlist_id
WHERE t.processlist_db='$($context.Schema)' AND a.attr_name='_client_name' AND a.attr_value='MySQL Connector/J';
"@
        $pool=($poolRow -split "`t");$poolPeak=[Math]::Max($poolPeak,[int]$pool[0]);$poolActivePeak=[Math]::Max($poolActivePeak,[int]$pool[1]);$poolIdlePeak=[Math]::Max($poolIdlePeak,[int]$pool[2])
        if(($samples % 5)-eq 0){
            $dump=& $JcmdPath $ServerPid Thread.print 2>$null | Out-String
            $threadBlocks=[regex]::Matches($dump,'(?ms)^"[^"]+".*?(?=^"|\z)')
            $pending=($threadBlocks|Where-Object{$_.Value -match 'com\.zaxxer\.hikari\.pool\.HikariPool\.getConnection'}).Count
            if($pending -gt 0){$pendingStackSamples++};$pendingStackPeak=[Math]::Max($pendingStackPeak,$pending)
        }
        $samples++;Start-Sleep -Milliseconds 100
    }
    Wait-Job $job | Out-Null;Receive-Job $job -ErrorAction Stop | Out-Null
    $result=[ordered]@{scenario=$Scenario;samples=$samples;art_wait_samples=$artWaitSamples;art_peak_waiters=$artPeak;point_account_wait_samples=$accountWaitSamples;point_account_peak_waiters=$accountPeak;max_observed_wait_ms=$maxWaitMs;connectorj_peak_connections=$poolPeak;connectorj_peak_active=$poolActivePeak;connectorj_peak_idle=$poolIdlePeak;hikari_pending_stack_samples=$pendingStackSamples;hikari_pending_stack_peak=$pendingStackPeak}
    $path=Join-Path $ResultDirectory "dailyatelier-$Scenario-observation.json";$result|ConvertTo-Json|Set-Content -Encoding utf8 $path;$result
} finally {
    if($job.State -eq 'Running'){Stop-Job $job};Remove-Job $job -Force -ErrorAction SilentlyContinue
    if($null -eq $previousMysqlPassword){Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}else{$env:MYSQL_PWD=$previousMysqlPassword}
}
