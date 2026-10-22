param(
    [string]$DbUrl = $env:LOAD_TEST_DB_URL,
    [string]$DbUsername = $env:LOAD_TEST_DB_USERNAME,
    [string]$DbPassword = $env:LOAD_TEST_DB_PASSWORD,
    [string]$Confirmation = $env:DAILYATELIER_LOAD_TEST_CONFIRM,
    [string]$MysqlPath = $env:MYSQL_CLIENT_PATH
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\load-test-guard.ps1"
$context = Assert-LoadTestTarget -DbUrl $DbUrl -Confirmation $Confirmation
$MysqlPath = Resolve-MysqlClient $MysqlPath
$previousMysqlPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $DbPassword
    Assert-GuardMarker -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath
    $sql = @"
SELECT 'current_price_mismatch', COUNT(*) FROM art a
WHERE a.name LIKE 'LT-%' AND EXISTS (SELECT 1 FROM bid b WHERE b.art_id=a.art_id)
AND a.current_price <> (SELECT MAX(b.bid_price) FROM bid b WHERE b.art_id=a.art_id)
UNION ALL
SELECT 'multiple_active_holds', COUNT(*) FROM (
  SELECT art_id FROM point_hold WHERE status='HELD' GROUP BY art_id HAVING COUNT(*) > 1
) x
UNION ALL
SELECT 'winner_hold_mismatch', COUNT(*) FROM art a
JOIN point_hold ph ON ph.hold_id=a.active_point_hold_id
JOIN bid b ON b.bid_id=ph.latest_bid_id
WHERE a.name LIKE 'LT-%' AND (
  ph.status <> 'HELD' OR ph.art_id <> a.art_id OR ph.user_id <> b.user_id
  OR ph.amount <> b.bid_price OR a.current_price <> b.bid_price
  OR EXISTS (
    SELECT 1 FROM bid higher WHERE higher.art_id=a.art_id AND (
      higher.bid_price > b.bid_price OR
      (higher.bid_price=b.bid_price AND higher.bid_time < b.bid_time) OR
      (higher.bid_price=b.bid_price AND higher.bid_time=b.bid_time AND higher.bid_id < b.bid_id)
    )
  )
)
UNION ALL
SELECT 'account_total_mismatch', COUNT(*) FROM point_account
WHERE user_id LIKE 'load_bid_%' AND available_balance + held_balance <> 10000000000
UNION ALL
SELECT 'ledger_balance_mismatch', COUNT(*) FROM point_account pa
LEFT JOIN (
  SELECT user_id, SUM(available_delta) available_sum, SUM(held_delta) held_sum
  FROM point_transaction GROUP BY user_id
) pt ON pt.user_id=pa.user_id
WHERE pa.user_id LIKE 'load_bid_%'
AND (pa.available_balance <> COALESCE(pt.available_sum,0) OR pa.held_balance <> COALESCE(pt.held_sum,0))
UNION ALL
SELECT 'held_balance_mismatch', COUNT(*) FROM point_account pa
LEFT JOIN (
  SELECT user_id, SUM(amount) held_sum FROM point_hold WHERE status='HELD' GROUP BY user_id
) ph ON ph.user_id=pa.user_id
WHERE pa.user_id LIKE 'load_bid_%' AND pa.held_balance <> COALESCE(ph.held_sum,0)
UNION ALL
SELECT 'bid_without_ledger', COUNT(*) FROM bid b
LEFT JOIN point_transaction pt ON pt.reference_type='BID' AND pt.reference_id=CAST(b.bid_id AS CHAR)
WHERE pt.transaction_id IS NULL;
"@
    $rows = Invoke-LoadTestMySql -Context $context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql $sql
    $failures = 0
    foreach ($row in $rows) {
        $parts = $row -split "`t"
        Write-Output "$($parts[0])=$($parts[1])"
        $failures += [int]$parts[1]
    }
    if ($failures -ne 0) { throw "Load-test consistency verification found $failures violation(s)." }
} finally {
    if ($null -eq $previousMysqlPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD=$previousMysqlPassword }
}
