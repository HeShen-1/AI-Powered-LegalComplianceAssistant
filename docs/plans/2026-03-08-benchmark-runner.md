# Benchmark Runner Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a reproducible benchmark runner that imports the evaluation markdown corpus through the existing knowledge-base upload API, runs 24 QA cases and 3 contract-review cases against a clean benchmark database, aggregates real metrics, and writes the results back into the repo docs.

**Architecture:** Use a PowerShell-based benchmark runner because the repo already includes PowerShell evaluation tooling and the current environment is Windows + PowerShell. Keep production behavior unchanged by running against a temporary benchmark database and the existing HTTP APIs. Add a small, testable helper layer for scoring and result aggregation, then use it from the runner script.

**Tech Stack:** PowerShell, Pester, PostgreSQL (`psql`), Spring Boot, Ollama, DeepSeek API, existing REST endpoints.

---

### Task 1: Add helper tests for benchmark scoring

**Files:**
- Create: `docs/evaluation/benchmark-lib.ps1`
- Create: `docs/evaluation/benchmark-lib.Tests.ps1`

**Step 1: Write the failing test**

- Add tests for:
  - keyword coverage → completeness score
  - expected document slug matching → retrieval hit
  - percentile helper → P95

**Step 2: Run test to verify it fails**

Run: `Invoke-Pester docs/evaluation/benchmark-lib.Tests.ps1`

**Step 3: Write minimal implementation**

- Implement only the scoring helpers needed by the tests.

**Step 4: Run test to verify it passes**

Run: `Invoke-Pester docs/evaluation/benchmark-lib.Tests.ps1`

### Task 2: Build benchmark runner

**Files:**
- Create: `docs/evaluation/run-benchmark.ps1`
- Modify: `docs/evaluation/result-template.csv`

**Step 1: Write the failing test**

- Reuse helper tests from Task 1 for the scoring logic.

**Step 2: Implement runner**

- Load `.env`
- Create a temporary benchmark database
- Override runtime env vars for benchmark-safe execution
- Start Spring Boot app in background on a dedicated port
- Log in as `admin` and `demo`
- Upload benchmark knowledge-base markdown files
- Run 24 QA cases
- Run 3 contract-review cases
- Write raw case results to CSV
- Print summary metrics

**Step 3: Verify runner on real environment**

Run: `powershell -ExecutionPolicy Bypass -File .\docs\evaluation\run-benchmark.ps1`

Expected:
- benchmark database created
- backend becomes healthy
- KB import succeeds
- 27 cases recorded
- summary metrics printed

### Task 3: Backfill docs with real numbers

**Files:**
- Modify: `README.md`
- Modify: `README_EN.md`
- Modify: `docs/evaluation/eval-report.md`

**Step 1: Update benchmark tables**

- Replace placeholder “待运行” with actual numbers
- State benchmark baseline clearly:
  - temporary clean database
  - markdown corpus imported through existing KB upload API
  - DeepSeek + local Ollama mixed runtime

**Step 2: Verify docs reference real outputs**

Run:
- `powershell -ExecutionPolicy Bypass -File .\docs\evaluation\aggregate-results.ps1 -InputCsv .\docs\evaluation\benchmark-results.csv`

Expected:
- summary matches README/eval-report values

