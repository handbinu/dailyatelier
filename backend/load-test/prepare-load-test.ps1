param(
    [string]$DbUrl = $env:LOAD_TEST_DB_URL,
    [string]$DbUsername = $env:LOAD_TEST_DB_USERNAME,
    [string]$DbPassword = $env:LOAD_TEST_DB_PASSWORD,
    [string]$FixturePassword = $env:LOAD_TEST_FIXTURE_PASSWORD,
    [string]$Confirmation = $env:DAILYATELIER_LOAD_TEST_CONFIRM,
    [string]$MysqlPath = $env:MYSQL_CLIENT_PATH
)

$ErrorActionPreference = 'Stop'
$requiredConfirmation = 'dailyatelier-load-test'
$allowedSchemaPattern = '^dailyatelier_load_test(?:_[a-z0-9_]+)?$'
$markerValue = 'dailyatelier-load-test-v1'

if ($Confirmation -cne $requiredConfirmation) {
    throw "DAILYATELIER_LOAD_TEST_CONFIRM must equal '$requiredConfirmation'."
}

$urlMatch = [regex]::Match(
    $DbUrl,
    '^jdbc:mysql://(localhost|127\.0\.0\.1)(?::([0-9]+))?/([^?]+)(?:\?.*)?$'
)
if (-not $urlMatch.Success) {
    throw 'LOAD_TEST_DB_URL must be a localhost MySQL JDBC URL.'
}

$hostName = $urlMatch.Groups[1].Value
$port = if ($urlMatch.Groups[2].Success) { $urlMatch.Groups[2].Value } else { '3306' }
$schemaName = $urlMatch.Groups[3].Value
if ($schemaName -cnotmatch $allowedSchemaPattern) {
    throw "Load-test schema name is not allowed: $schemaName"
}
if ([string]::IsNullOrWhiteSpace($DbUsername)) {
    throw 'LOAD_TEST_DB_USERNAME is required.'
}
if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw 'LOAD_TEST_DB_PASSWORD is required.'
}
if ([string]::IsNullOrWhiteSpace($FixturePassword)) {
    throw 'LOAD_TEST_FIXTURE_PASSWORD is required.'
}

if ([string]::IsNullOrWhiteSpace($MysqlPath)) {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -ne $mysqlCommand) {
        $MysqlPath = $mysqlCommand.Source
    } else {
        $MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    }
}
if (-not (Test-Path -LiteralPath $MysqlPath)) {
    throw "MySQL client was not found: $MysqlPath"
}

function Invoke-MySqlScalar {
    param([string]$Sql)

    $output = & $MysqlPath -h $hostName -P $port -u $DbUsername --batch --skip-column-names -e $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL safety check failed.'
    }
    return ($output | Select-Object -First 1)
}

$previousMysqlPassword = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $DbPassword
    $escapedSchema = $schemaName.Replace("'", "''")
    $schemaExists = Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$escapedSchema'"

    if ($schemaExists -eq '1') {
        $markerExists = Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$escapedSchema' AND table_name = 'load_test_schema_guard'"
        if ($markerExists -eq '1') {
            $escapedMarker = $markerValue.Replace("'", "''")
            $markerMatches = Invoke-MySqlScalar "SELECT COUNT(*) FROM ``$schemaName``.load_test_schema_guard WHERE marker = '$escapedMarker'"
            if ($markerMatches -ne '1') {
                throw "Existing schema '$schemaName' has an invalid load-test guard marker; refusing to reset it."
            }
        } else {
            $tableCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$escapedSchema'"
            if ($tableCount -ne '0') {
                throw "Existing non-empty schema '$schemaName' has no load-test guard marker; refusing to reset it."
            }
        }
        & $MysqlPath -h $hostName -P $port -u $DbUsername -e "DROP DATABASE ``$schemaName``"
        if ($LASTEXITCODE -ne 0) {
            throw "Could not reset load-test schema '$schemaName'."
        }
    }

    & $MysqlPath -h $hostName -P $port -u $DbUsername -e "CREATE DATABASE ``$schemaName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create load-test schema '$schemaName'."
    }

    $env:DB_URL = $DbUrl
    $env:DB_USERNAME = $DbUsername
    $env:DB_PASSWORD = $DbPassword
    $env:LOAD_TEST_FIXTURE_PASSWORD = $FixturePassword
    $env:DAILYATELIER_LOAD_TEST_FIXTURE = 'true'

    Push-Location (Resolve-Path "$PSScriptRoot\..")
    try {
        & .\gradlew.bat test --rerun-tasks --tests "com.dailyatelier.dailyatelier.loadtest.LoadTestFixtureMySqlTest"
        if ($LASTEXITCODE -ne 0) {
            throw 'Load-test Flyway, fixture, login, or smoke verification failed.'
        }
    } finally {
        Pop-Location
    }

    Write-Host "Load-test fixture is ready in schema '$schemaName'."
} finally {
    if ($null -eq $previousMysqlPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $previousMysqlPassword
    }
}
