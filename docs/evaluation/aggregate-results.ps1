param(
    [string]$InputCsv = ".\docs\evaluation\result-template.csv"
)

if (-not (Test-Path $InputCsv)) {
    throw "Input CSV not found: $InputCsv"
}

$rows = Import-Csv $InputCsv
if (-not $rows -or $rows.Count -eq 0) {
    throw "No rows found in $InputCsv"
}

function Get-NumericValues {
    param(
        [object[]]$Items,
        [string]$Field
    )

    $values = @()
    foreach ($item in $Items) {
        $raw = $item.$Field
        if ($null -ne $raw -and $raw -ne "") {
            $number = 0.0
            if ([double]::TryParse($raw, [ref]$number)) {
                $values += $number
            }
        }
    }
    return $values
}

function Get-Rate {
    param([double[]]$Values)
    if (-not $Values -or $Values.Count -eq 0) {
        return "N/A"
    }
    return "{0:P1}" -f (($Values | Measure-Object -Average).Average)
}

function Get-Average {
    param([double[]]$Values)
    if (-not $Values -or $Values.Count -eq 0) {
        return "N/A"
    }
    return "{0:N2}" -f (($Values | Measure-Object -Average).Average)
}

function Get-P95 {
    param([double[]]$Values)
    if (-not $Values -or $Values.Count -eq 0) {
        return "N/A"
    }

    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling($sorted.Count * 0.95) - 1
    if ($index -lt 0) {
        $index = 0
    }
    return "{0:N0} ms" -f $sorted[$index]
}

$retrievalHits = Get-NumericValues -Items $rows -Field "retrieval_hit"
$answerScores = Get-NumericValues -Items $rows -Field "answer_completeness"
$structuredSuccess = Get-NumericValues -Items $rows -Field "structured_success"
$latencies = Get-NumericValues -Items $rows -Field "latency_ms"
$fallbackUsed = Get-NumericValues -Items $rows -Field "fallback_used"

Write-Host "Evaluation Summary" -ForegroundColor Cyan
Write-Host "------------------"
Write-Host ("Cases: {0}" -f $rows.Count)
Write-Host ("Retrieval hit rate: {0}" -f (Get-Rate -Values $retrievalHits))
Write-Host ("Answer completeness avg: {0}" -f (Get-Average -Values $answerScores))
Write-Host ("Structured extraction success: {0}" -f (Get-Rate -Values $structuredSuccess))
Write-Host ("P95 latency: {0}" -f (Get-P95 -Values $latencies))
Write-Host ("Fallback trigger rate: {0}" -f (Get-Rate -Values $fallbackUsed))

