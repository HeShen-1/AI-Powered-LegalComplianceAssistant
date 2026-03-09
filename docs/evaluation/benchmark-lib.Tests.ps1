$here = $PSScriptRoot
. (Join-Path $here 'benchmark-lib.ps1')

Describe 'Benchmark helper functions' {

    It 'scores answer completeness by keyword coverage' {
        $score = Get-KeywordCoverageScore -Answer 'continue performance, cure defects, and compensate loss' -ExpectedPoints @('continue performance', 'cure defects', 'compensate loss')
        $score | Should Be 5
    }

    It 'returns partial completeness when only some expected points appear' {
        $score = Get-KeywordCoverageScore -Answer 'continue performance and compensate loss' -ExpectedPoints @('continue performance', 'cure defects', 'compensate loss')
        $score | Should Be 3.33
    }

    It 'detects retrieval hit from source slug match' {
        $hit = Test-ExpectedSourceHit -Sources @('kb-civil-breach.md (相关度: 0.93)', 'other-source.md (相关度: 0.61)') -ExpectedDocIds @('kb-civil-breach')
        $hit | Should Be $true
    }

    It 'returns false when no expected source matches' {
        $hit = Test-ExpectedSourceHit -Sources @('kb-labor-termination.md (相关度: 0.88)') -ExpectedDocIds @('kb-civil-breach')
        $hit | Should Be $false
    }

    It 'calculates p95 from numeric values' {
        $p95 = Get-P95Value -Values @(100, 120, 140, 160, 180, 200, 220, 240, 260, 1000)
        $p95 | Should Be 1000
    }

    It 'returns benchmark credential seeds for admin and demo users' {
        $seeds = Get-BenchmarkCredentialSeeds

        ($seeds | Measure-Object).Count | Should Be 2
        ($seeds | Where-Object Username -eq 'admin').PasswordHash | Should Be '$2a$10$mD..katWWM44G14gqRZbueXjlnF5vdktkWUoHl5pmvu/y/eN7ljpe'
        ($seeds | Where-Object Username -eq 'demo').PasswordHash | Should Be '$2a$10$Q2kXbIw2ytujlC/lElSiROJ1c/mimRocfbqo0E6j2BK4oTwVjAE1a'
    }

    It 'avoids reserved Host parameter in Invoke-Psql' {
        $runnerPath = Join-Path $here 'run-benchmark.ps1'
        $tokens = $null
        $parseErrors = $null
        $ast = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$parseErrors)

        $parseErrors | Should BeNullOrEmpty

        $invokePsql = $ast.Find({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-Psql'
        }, $true)

        $invokePsql | Should Not BeNullOrEmpty
        $parameterNames = @($invokePsql.Body.ParamBlock.Parameters | ForEach-Object { $_.Name.VariablePath.UserPath })
        ($parameterNames -contains 'Host') | Should Be $false
    }
}
