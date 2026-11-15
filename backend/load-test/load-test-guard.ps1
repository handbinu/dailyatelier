$script:LoadTestConfirmation = 'dailyatelier-load-test'
$script:LoadTestSchemaPattern = '^dailyatelier_load_test(?:_[a-z0-9_]+)?$'
$script:LoadTestMarker = 'dailyatelier-load-test-v1'

function Assert-LoadTestTarget {
    param([string]$DbUrl, [string]$Confirmation)
    if ($Confirmation -cne $script:LoadTestConfirmation) { throw "DAILYATELIER_LOAD_TEST_CONFIRM must equal '$script:LoadTestConfirmation'." }
    $match = [regex]::Match($DbUrl, '^jdbc:mysql://(localhost|127\.0\.0\.1)(?::([0-9]+))?/([^?]+)(?:\?.*)?$')
    if (-not $match.Success) { throw 'LOAD_TEST_DB_URL must be a localhost MySQL JDBC URL.' }
    $schema = $match.Groups[3].Value
    if ($schema -cnotmatch $script:LoadTestSchemaPattern) { throw "Load-test schema name is not allowed: $schema" }
    return [pscustomobject]@{ Host=$match.Groups[1].Value; Port=if($match.Groups[2].Success){$match.Groups[2].Value}else{'3306'}; Schema=$schema }
}

function Resolve-MysqlClient {
    param([string]$MysqlPath)
    if ([string]::IsNullOrWhiteSpace($MysqlPath)) {
        $command = Get-Command mysql -ErrorAction SilentlyContinue
        $MysqlPath = if ($null -ne $command) { $command.Source } else { 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
    }
    if (-not (Test-Path -LiteralPath $MysqlPath)) { throw "MySQL client was not found: $MysqlPath" }
    return $MysqlPath
}

function Invoke-LoadTestMySql {
    param($Context, [string]$DbUsername, [string]$MysqlPath, [string]$Sql)
    if ([string]::IsNullOrWhiteSpace($DbUsername)) { throw 'LOAD_TEST_DB_USERNAME is required.' }
    $output = & $MysqlPath -h $Context.Host -P $Context.Port -u $DbUsername "--database=$($Context.Schema)" --batch --skip-column-names -e $Sql
    if ($LASTEXITCODE -ne 0) { throw 'Load-test MySQL command failed.' }
    return $output
}

function Assert-GuardMarker {
    param($Context, [string]$DbUsername, [string]$MysqlPath)
    $count = Invoke-LoadTestMySql -Context $Context -DbUsername $DbUsername -MysqlPath $MysqlPath -Sql "SELECT COUNT(*) FROM load_test_schema_guard WHERE marker='$script:LoadTestMarker'"
    if ($count -ne '1') { throw 'The load-test database guard marker is missing or invalid.' }
}
