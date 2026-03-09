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

function Get-NormalizedBenchmarkSources {
    param(
        [object[]]$Sources
    )

    if (-not $Sources) {
        return @()
    }

    $normalizedSources = New-Object System.Collections.Generic.List[string]

    foreach ($source in $Sources) {
        if ($null -eq $source) {
            continue
        }

        $candidates = New-Object System.Collections.Generic.List[string]

        if ($source -is [string]) {
            $candidates.Add($source)
        } elseif ($source -is [hashtable]) {
            foreach ($key in @('source', 'sourceId', 'documentId', 'label', 'name')) {
                if ($source.ContainsKey($key) -and $source[$key]) {
                    $candidates.Add([string]$source[$key])
                }
            }
        } else {
            foreach ($propertyName in @('source', 'sourceId', 'documentId', 'label', 'name')) {
                $property = $source.PSObject.Properties[$propertyName]
                if ($property -and $property.Value) {
                    $candidates.Add([string]$property.Value)
                }
            }
        }

        if ($candidates.Count -eq 0) {
            $candidates.Add([string]$source)
        }

        foreach ($candidate in $candidates) {
            $normalized = Normalize-BenchmarkText -Text $candidate
            if ($normalized) {
                $normalizedSources.Add($normalized)
            }
        }
    }

    return @($normalizedSources)
}

function Test-BenchmarkHealthResponse {
    param(
        [object]$Response
    )

    if ($null -eq $Response) {
        return $false
    }

    if ($Response -is [System.Collections.IDictionary]) {
        if ($Response.Contains('status') -and $Response['status'] -eq 'UP') {
            return $true
        }

        if ($Response.Contains('data') -and $null -ne $Response['data']) {
            $nestedData = $Response['data']
            if ($nestedData -is [System.Collections.IDictionary]) {
                if ($nestedData.Contains('status') -and $nestedData['status'] -eq 'UP') {
                    return $true
                }
            }
        }
    }

    $topLevelStatus = $Response.PSObject.Properties['status']
    if ($topLevelStatus -and $topLevelStatus.Value -eq 'UP') {
        return $true
    }

    $dataProperty = $Response.PSObject.Properties['data']
    if ($dataProperty -and $null -ne $dataProperty.Value) {
        $nestedStatus = $dataProperty.Value.PSObject.Properties['status']
        if ($nestedStatus -and $nestedStatus.Value -eq 'UP') {
            return $true
        }
    }

    return $false
}

function Get-BenchmarkHealthProbeUrls {
    param(
        [string]$BaseUrl
    )

    return @(
        "$BaseUrl/health",
        "$BaseUrl/health/detailed"
    )
}

function Test-ExpectedSourceHit {
    param(
        [object[]]$Sources,
        [string[]]$ExpectedDocIds
    )

    if (-not $Sources -or -not $ExpectedDocIds) {
        return $false
    }

    $normalizedSources = Get-NormalizedBenchmarkSources -Sources $Sources

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
