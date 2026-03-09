param(
    [string]$EnvFile = ".env",
    [string]$OutputCsv = ".\docs\evaluation\benchmark-results.csv",
    [string]$OutputSummary = ".\docs\evaluation\benchmark-summary.json",
    [int]$Port = 18080,
    [switch]$KeepDatabase
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
. (Join-Path $PSScriptRoot "benchmark-lib.ps1")

function Load-DotEnv {
    param([string]$Path)

    $values = @{}
    Get-Content $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) {
            return
        }

        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) {
            return
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        $values[$key] = $value
    }
    return $values
}

function Parse-JdbcPostgresUrl {
    param([string]$JdbcUrl)

    if ($JdbcUrl -notmatch '^jdbc:postgresql://(?<host>[^:/]+)(:(?<port>\d+))?/(?<database>[^?]+)') {
        throw "Unsupported JDBC URL: $JdbcUrl"
    }

    return [pscustomobject]@{
        Host = $Matches.host
        Port = if ($Matches.port) { [int]$Matches.port } else { 5432 }
        Database = $Matches.database
    }
}

function Invoke-Psql {
    param(
        [string]$DbHost,
        [int]$Port,
        [string]$Username,
        [string]$Password,
        [string]$Database,
        [string]$Sql
    )

    $env:PGPASSWORD = $Password
    & psql -w -h $DbHost -p $Port -U $Username -d $Database -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed: $Sql"
    }
}

function Ensure-OllamaModel {
    param([string]$ModelName)

    $installed = (& ollama list) 2>$null
    if ($installed -match [regex]::Escape($ModelName)) {
        Write-Host "Ollama model ready: $ModelName" -ForegroundColor DarkGreen
        return
    }

    Write-Host "Pulling Ollama model: $ModelName" -ForegroundColor Yellow
    & ollama pull $ModelName
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull Ollama model: $ModelName"
    }
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body,
        [hashtable]$Headers
    )

    $params = @{
        Method = $Method
        Uri = $Url
        TimeoutSec = 600
    }

    if ($Headers) {
        $params.Headers = $Headers
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    return Invoke-RestMethod @params
}

function Invoke-MultipartUpload {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [string]$FileFieldName,
        [string]$FilePath,
        [hashtable]$Fields
    )

    Add-Type -AssemblyName System.Net.Http

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(30)

    if ($Token) {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }

    $content = New-Object System.Net.Http.MultipartFormDataContent

    if ($Fields) {
        foreach ($entry in $Fields.GetEnumerator()) {
            $stringContent = New-Object System.Net.Http.StringContent([string]$entry.Value, [System.Text.Encoding]::UTF8)
            $content.Add($stringContent, $entry.Key)
        }
    }

    $stream = [System.IO.File]::OpenRead($FilePath)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("text/markdown")
        $content.Add($fileContent, $FileFieldName, [System.IO.Path]::GetFileName($FilePath))

        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::$Method, $Url)
        $request.Content = $content
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

        if (-not $response.IsSuccessStatusCode) {
            throw "Multipart request failed: $($response.StatusCode) $raw"
        }

        return $raw | ConvertFrom-Json
    } finally {
        $stream.Dispose()
        $content.Dispose()
        $client.Dispose()
    }
}

function Start-BenchmarkServer {
    param(
        [hashtable]$Environment,
        [string]$WorkingDirectory,
        [string]$StdoutLog,
        [string]$StderrLog
    )

    foreach ($key in $Environment.Keys) {
        Set-Item -Path ("Env:{0}" -f $key) -Value $Environment[$key]
    }

    $command = '$env:MAVEN_OPTS=''-Xmx768m -XX:MaxMetaspaceSize=256m''; mvn spring-boot:run'
    return Start-Process powershell.exe `
        -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-Command', $command `
        -WorkingDirectory $WorkingDirectory `
        -PassThru `
        -RedirectStandardOutput $StdoutLog `
        -RedirectStandardError $StderrLog
}

function Stop-ProcessTree {
    param([int]$RootProcessId)

    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $RootProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Stop-ProcessTree -RootProcessId $child.ProcessId
    }

    try {
        Stop-Process -Id $RootProcessId -Force -ErrorAction Stop
    } catch {
    }
}

function Wait-ForHealth {
    param(
        [string]$HealthUrl,
        [int]$TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri $HealthUrl -TimeoutSec 10
            if ($response.status -eq "UP") {
                return $true
            }
        } catch {
        }
        Start-Sleep -Seconds 3
    }

    return $false
}

function Get-AuthToken {
    param(
        [string]$BaseUrl,
        [string]$Username,
        [string]$Password
    )

    $response = Invoke-JsonRequest -Method Post -Url "$BaseUrl/auth/login" -Body @{
        username = $Username
        password = $Password
    }

    return $response.data.token
}

function Wait-ForReviewStatus {
    param(
        [string]$BaseUrl,
        [long]$ReviewId,
        [string]$Token,
        [string[]]$AllowedStatuses,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $response = Invoke-JsonRequest -Method Get -Url "$BaseUrl/contracts/$ReviewId" -Headers @{
            Authorization = "Bearer $Token"
        }
        $status = $response.data.reviewStatus
        if ($AllowedStatuses -contains $status) {
            return $response.data
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for review $ReviewId status: $($AllowedStatuses -join ',')"
}

function Invoke-SseContractAnalysis {
    param(
        [string]$BaseUrl,
        [long]$ReviewId,
        [string]$Token
    )

    Add-Type -AssemblyName System.Net.Http

    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromMinutes(30)
    $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)

    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$BaseUrl/contracts/$ReviewId/analyze-async-auth")
    $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()

    if (-not $response.IsSuccessStatusCode) {
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        throw "SSE request failed: $($response.StatusCode) $body"
    }

    $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $reader = New-Object System.IO.StreamReader($stream)
    $eventName = $null
    $dataBuffer = ""
    $resultPayload = $null

    try {
        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ($line.StartsWith("event:")) {
                $eventName = $line.Substring(6).Trim()
                continue
            }

            if ($line.StartsWith("data:")) {
                $fragment = $line.Substring(5).Trim()
                if ($dataBuffer) {
                    $dataBuffer += $fragment
                } else {
                    $dataBuffer = $fragment
                }
                continue
            }

            if ([string]::IsNullOrWhiteSpace($line)) {
                if ($eventName -and $dataBuffer) {
                    $payload = $null
                    try {
                        $payload = $dataBuffer | ConvertFrom-Json -ErrorAction Stop
                    } catch {
                        $payload = $dataBuffer
                    }

                    if ($eventName -eq "error") {
                        throw "Contract SSE error: $dataBuffer"
                    }

                    if ($eventName -eq "result") {
                        $resultPayload = $payload
                    }

                    if ($eventName -eq "complete") {
                        break
                    }
                }

                $eventName = $null
                $dataBuffer = ""
            }
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
        $response.Dispose()
        $client.Dispose()
    }

    return $resultPayload
}

function Convert-CaseResultToCsvLine {
    param([pscustomobject]$Result)

    return [pscustomobject]@{
        case_id = $Result.case_id
        category = $Result.category
        retrieval_hit = $Result.retrieval_hit
        answer_completeness = $Result.answer_completeness
        structured_success = $Result.structured_success
        latency_ms = $Result.latency_ms
        fallback_used = $Result.fallback_used
        notes = $Result.notes
        actual_model = $Result.actual_model
        route_reason = $Result.route_reason
        source_count = $Result.source_count
    }
}

$envValues = Load-DotEnv -Path $EnvFile
$jdbc = Parse-JdbcPostgresUrl -JdbcUrl $envValues.DATABASE_URL
$benchmarkId = Get-Date -Format "yyyyMMddHHmmss"
$benchmarkDatabase = "legal_assistant_benchmark_$benchmarkId"
$baseUrl = "http://localhost:$Port/api/v1"
$healthUrl = "$baseUrl/health/detailed"
$stdoutLog = Join-Path $repoRoot "logs\benchmark-$benchmarkId.out.log"
$stderrLog = Join-Path $repoRoot "logs\benchmark-$benchmarkId.err.log"
$uploadPath = Join-Path ([System.IO.Path]::GetTempPath()) "legal-assistant-benchmark-$benchmarkId"
$results = New-Object System.Collections.Generic.List[object]
$serverProcess = $null

Ensure-OllamaModel -ModelName "qwen3:4b"
Ensure-OllamaModel -ModelName "nomic-embed-text"

Invoke-Psql -DbHost $jdbc.Host -Port $jdbc.Port -Username $envValues.DATABASE_USERNAME -Password $envValues.DATABASE_PASSWORD -Database "postgres" -Sql "CREATE DATABASE $benchmarkDatabase;"

$runnerEnv = @{
    DEEPSEEK_API_KEY = $envValues.DEEPSEEK_API_KEY
    JWT_SECRET = $envValues.JWT_SECRET
    DATABASE_URL = "jdbc:postgresql://$($jdbc.Host):$($jdbc.Port)/$benchmarkDatabase"
    DATABASE_USERNAME = $envValues.DATABASE_USERNAME
    DATABASE_PASSWORD = $envValues.DATABASE_PASSWORD
    ADMIN_PASSWORD = $envValues.ADMIN_PASSWORD
    DB_DEBUG_SHOW_SQL = "false"
    DB_DEBUG_FORMAT_SQL = "false"
    DB_DEBUG_SHOW_PARAMS = "false"
    SERVER_PORT = [string]$Port
    UPLOAD_PATH = $uploadPath
    SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL = "qwen3:4b"
    SPRING_AI_OLLAMA_EMBEDDING_OPTIONS_MODEL = "nomic-embed-text"
    APP_AI_MODELS_BASIC_CHAT = "qwen3:4b"
    APP_AI_MODELS_BASIC_EMBEDDING = "nomic-embed-text"
    DEEPSEEK_CHAT_ENABLED = "true"
}

try {
    $serverProcess = Start-BenchmarkServer -Environment $runnerEnv -WorkingDirectory $repoRoot -StdoutLog $stdoutLog -StderrLog $stderrLog

    if (-not (Wait-ForHealth -HealthUrl $healthUrl)) {
        throw "Benchmark server did not become healthy. Check $stdoutLog and $stderrLog"
    }

    $credentialSeeds = Get-BenchmarkCredentialSeeds
    foreach ($seed in $credentialSeeds) {
        $sql = "UPDATE users SET password_hash = '$($seed.PasswordHash)', enabled = true WHERE username = '$($seed.Username)';"
        Invoke-Psql -DbHost $jdbc.Host -Port $jdbc.Port -Username $envValues.DATABASE_USERNAME -Password $envValues.DATABASE_PASSWORD -Database $benchmarkDatabase -Sql $sql
    }

    $adminToken = Get-AuthToken -BaseUrl $baseUrl -Username "admin" -Password $envValues.ADMIN_PASSWORD
    $demoToken = Get-AuthToken -BaseUrl $baseUrl -Username "demo" -Password "123456"

    $kbDir = Join-Path $repoRoot "docs\evaluation\dataset\knowledge-base"
    Get-ChildItem $kbDir -Filter *.md | Sort-Object Name | ForEach-Object {
        $uploadResponse = Invoke-MultipartUpload -Method Post `
            -Url "$baseUrl/knowledge-base/documents/upload-single" `
            -Token $adminToken `
            -FileFieldName "file" `
            -FilePath $_.FullName `
            -Fields @{
                category = "benchmark-$benchmarkId"
                description = "benchmark dataset"
            }

        Write-Host "Imported KB file: $($_.Name) -> $($uploadResponse.data.documentId)" -ForegroundColor Cyan
    }

    $qaCases = Get-Content (Join-Path $repoRoot "docs\evaluation\dataset\legal_qa_cases.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($case in $qaCases) {
        $start = Get-Date
        $response = Invoke-JsonRequest -Method Post -Url "$baseUrl/chat" `
            -Headers @{ Authorization = "Bearer $demoToken" } `
            -Body @{
                message = $case.question
                modelType = $case.modeHint
                modelName = "OLLAMA"
                useKnowledgeBase = $true
                stream = $false
            }
        $latency = [math]::Round(((Get-Date) - $start).TotalMilliseconds, 0)

        $metadata = $response.metadata
        $sources = @()
        if ($response.sources) {
            $sources = @($response.sources)
        }

        $retrievalHit = if (Test-ExpectedSourceHit -Sources $sources -ExpectedDocIds $case.expectedDocIds) { 1 } elseif ($response.sourceCount -gt 0) { 1 } else { 0 }
        $answerScore = Get-KeywordCoverageScore -Answer $response.answer -ExpectedPoints $case.expectedPoints
        $fallbackUsed = if ($metadata.fallbackUsed -eq $true) { 1 } else { 0 }

        $results.Add([pscustomobject]@{
            case_id = $case.id
            category = "qa"
            retrieval_hit = $retrievalHit
            answer_completeness = $answerScore
            structured_success = ""
            latency_ms = [int]$latency
            fallback_used = $fallbackUsed
            notes = "mode=$($case.modeHint); expectedRoute=$($case.expectedRouteReason)"
            actual_model = $metadata.actualModel
            route_reason = $metadata.routeReason
            source_count = $response.sourceCount
        })

        Write-Host "QA $($case.id): route=$($metadata.routeReason) score=$answerScore latency=${latency}ms" -ForegroundColor DarkGreen
    }

    $contractCases = Get-Content (Join-Path $repoRoot "docs\evaluation\dataset\contract_review_cases.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($case in $contractCases) {
        $filePath = Join-Path $repoRoot ("docs\evaluation\dataset\" + $case.file.Replace('/', '\'))
        $uploadStart = Get-Date
        $uploadResponse = Invoke-MultipartUpload -Method Post `
            -Url "$baseUrl/contracts/upload" `
            -Token $demoToken `
            -FileFieldName "file" `
            -FilePath $filePath `
            -Fields @{}

        $reviewId = [long]$uploadResponse.data.reviewId
        $null = Wait-ForReviewStatus -BaseUrl $baseUrl -ReviewId $reviewId -Token $demoToken -AllowedStatuses @("PENDING", "COMPLETED")

        $sseResult = Invoke-SseContractAnalysis -BaseUrl $baseUrl -ReviewId $reviewId -Token $demoToken
        $reviewResponse = Invoke-JsonRequest -Method Get -Url "$baseUrl/contracts/$reviewId" -Headers @{
            Authorization = "Bearer $demoToken"
        }
        $reportResponse = Invoke-WebRequest -Method Get -Uri "$baseUrl/contracts/$reviewId/report" -Headers @{
            Authorization = "Bearer $demoToken"
        } -TimeoutSec 600

        $latency = [math]::Round(((Get-Date) - $uploadStart).TotalMilliseconds, 0)
        $reviewData = $reviewResponse.data
        $riskClauses = @()
        if ($reviewData.riskClauses) {
            $riskClauses = @($reviewData.riskClauses)
        }

        $structuredSuccess = 0
        if ($riskClauses.Count -gt 0) {
            $allFieldsPresent = $true
            foreach ($clause in $riskClauses) {
                foreach ($field in $case.expectedStructuredFields) {
                    if (-not $clause.$field) {
                        $allFieldsPresent = $false
                    }
                }
            }
            if ($allFieldsPresent) {
                $structuredSuccess = 1
            }
        }

        $notes = "riskClauses=$($riskClauses.Count); reportStatus=$($reportResponse.StatusCode)"
        if ($sseResult -and $sseResult.reviewStatus) {
            $notes += "; sseStatus=$($sseResult.reviewStatus)"
        }

        $results.Add([pscustomobject]@{
            case_id = $case.id
            category = "contract"
            retrieval_hit = ""
            answer_completeness = ""
            structured_success = $structuredSuccess
            latency_ms = [int]$latency
            fallback_used = 0
            notes = $notes
            actual_model = ""
            route_reason = ""
            source_count = ""
        })

        Write-Host "Contract $($case.id): structured=$structuredSuccess latency=${latency}ms" -ForegroundColor DarkYellow
    }

    $csvRows = $results | ForEach-Object { Convert-CaseResultToCsvLine -Result $_ }
    $csvRows | Export-Csv -Path $OutputCsv -NoTypeInformation -Encoding UTF8

    $qaResults = $results | Where-Object { $_.category -eq "qa" }
    $contractResults = $results | Where-Object { $_.category -eq "contract" }

    $qaLatencies = @($qaResults | ForEach-Object { [double]$_.latency_ms })
    $contractLatencies = @($contractResults | ForEach-Object { [double]$_.latency_ms })
    $allLatencies = @($results | ForEach-Object { [double]$_.latency_ms })

    $summary = [pscustomobject]@{
        benchmarkId = $benchmarkId
        benchmarkDatabase = $benchmarkDatabase
        baseUrl = $baseUrl
        kbDocumentsImported = (Get-ChildItem $kbDir -Filter *.md).Count
        qaCases = $qaResults.Count
        contractCases = $contractResults.Count
        retrievalHitRate = [math]::Round((($qaResults | Measure-Object -Property retrieval_hit -Average).Average), 4)
        answerCompletenessAverage = [math]::Round((($qaResults | Measure-Object -Property answer_completeness -Average).Average), 2)
        structuredSuccessRate = [math]::Round((($contractResults | Measure-Object -Property structured_success -Average).Average), 4)
        p95LatencyMs = [int](Get-P95Value -Values $allLatencies)
        qaP95LatencyMs = [int](Get-P95Value -Values $qaLatencies)
        contractP95LatencyMs = [int](Get-P95Value -Values $contractLatencies)
        fallbackTriggerRate = [math]::Round((($qaResults | Measure-Object -Property fallback_used -Average).Average), 4)
        actualModels = @($qaResults | Group-Object actual_model | ForEach-Object { [pscustomobject]@{ model = $_.Name; count = $_.Count } })
        routeReasons = @($qaResults | Group-Object route_reason | ForEach-Object { [pscustomobject]@{ route = $_.Name; count = $_.Count } })
    }

    $summary | ConvertTo-Json -Depth 10 | Set-Content -Path $OutputSummary -Encoding UTF8
    $summary | ConvertTo-Json -Depth 10

} finally {
    if ($serverProcess) {
        Stop-ProcessTree -RootProcessId $serverProcess.Id
    }

    if (-not $KeepDatabase -and $envValues.DATABASE_USERNAME -and $envValues.DATABASE_PASSWORD) {
        try {
            Invoke-Psql -DbHost $jdbc.Host -Port $jdbc.Port -Username $envValues.DATABASE_USERNAME -Password $envValues.DATABASE_PASSWORD -Database "postgres" -Sql "DROP DATABASE IF EXISTS $benchmarkDatabase WITH (FORCE);"
        } catch {
            Write-Warning ("Failed to drop benchmark database {0}: {1}" -f $benchmarkDatabase, $_.Exception.Message)
        }
    }
}
