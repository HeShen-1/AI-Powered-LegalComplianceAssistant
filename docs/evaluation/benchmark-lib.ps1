function Normalize-BenchmarkText {
    param(
        [string]$Text
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ''
    }

    return ($Text.ToLowerInvariant() -replace '\s+', '')
}

function Get-KeywordCoverageScore {
    param(
        [string]$Answer,
        [string[]]$ExpectedPoints
    )

    if (-not $ExpectedPoints -or $ExpectedPoints.Count -eq 0) {
        return 0
    }

    $normalizedAnswer = Normalize-BenchmarkText -Text $Answer
    $matched = 0

    foreach ($point in $ExpectedPoints) {
        $normalizedPoint = Normalize-BenchmarkText -Text $point
        if ($normalizedPoint -and $normalizedAnswer.Contains($normalizedPoint)) {
            $matched++
        }
    }

    return [math]::Round(($matched / $ExpectedPoints.Count) * 5, 2)
}

function Test-ExpectedSourceHit {
    param(
        [string[]]$Sources,
        [string[]]$ExpectedDocIds
    )

    if (-not $Sources -or -not $ExpectedDocIds) {
        return $false
    }

    $normalizedSources = $Sources | ForEach-Object { Normalize-BenchmarkText -Text $_ }

    foreach ($docId in $ExpectedDocIds) {
        $slug = Normalize-BenchmarkText -Text $docId
        if (-not $slug) {
            continue
        }

        foreach ($source in $normalizedSources) {
            if ($source.Contains($slug)) {
                return $true
            }
        }
    }

    return $false
}

function Get-P95Value {
    param(
        [double[]]$Values
    )

    if (-not $Values -or $Values.Count -eq 0) {
        return $null
    }

    $sorted = $Values | Sort-Object
    $index = [math]::Ceiling($sorted.Count * 0.95) - 1
    if ($index -lt 0) {
        $index = 0
    }

    return [double]$sorted[$index]
}

function Get-BenchmarkCredentialSeeds {
    return @(
        [pscustomobject]@{
            Username = 'admin'
            PasswordHash = '$2a$10$mD..katWWM44G14gqRZbueXjlnF5vdktkWUoHl5pmvu/y/eN7ljpe'
        },
        [pscustomobject]@{
            Username = 'demo'
            PasswordHash = '$2a$10$Q2kXbIw2ytujlC/lElSiROJ1c/mimRocfbqo0E6j2BK4oTwVjAE1a'
        }
    )
}
