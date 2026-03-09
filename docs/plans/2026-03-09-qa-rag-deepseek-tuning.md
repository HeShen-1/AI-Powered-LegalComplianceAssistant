# QA RAG DeepSeek Tuning Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rework QA requests so local models are used only for embedding/retrieval while DeepSeek handles answer generation, then align benchmark scoring and artifacts with the new retrieval semantics.

**Architecture:** Keep the existing application structure and unify behavior at the QA routing and response layers. Fix the problem at the root cause by ensuring knowledge-base QA always carries retrieval results into the final answer path and exposes stable sources metadata for both frontend and benchmark consumption.

**Tech Stack:** Spring Boot, Spring AI, LangChain4j, PowerShell benchmark scripts, JUnit/Mockito, Pester.

---

### Task 1: Capture desired QA retrieval behavior with tests

**Files:**
- Modify: `src/test/java/com/river/LegalAssistant/controller/UnifiedChatControllerTest.java`
- Modify: `src/test/java/com/river/LegalAssistant/config/AiConfigFallbackTest.java`
- Modify: `docs/evaluation/benchmark-lib.Tests.ps1`

**Step 1: Write the failing test**

- Add a controller/service level test proving KB-enabled QA returns stable `sources`, `sourceCount`, `actualModel`, and `routeReason`.
- Add a config/service test proving QA generation prefers DeepSeek while embedding remains local.
- Add a benchmark helper test for source parsing against the real response format.

**Step 2: Run test to verify it fails**

Run:

- `./mvnw -Dtest=UnifiedChatControllerTest,AiConfigFallbackTest test`
- `Invoke-Pester .\docs\evaluation\benchmark-lib.Tests.ps1`

Expected:

- New QA tests fail for missing / incorrect retrieval behavior.

**Step 3: Write minimal implementation**

- Change only what is needed to make these tests meaningful red tests.

**Step 4: Run test to verify it still fails for the intended reason**

- Confirm failures point at missing QA retrieval / source behavior, not syntax or environment issues.

### Task 2: Route KB QA to retrieval + DeepSeek generation

**Files:**
- Modify: `src/main/java/com/river/LegalAssistant/service/AiService.java`
- Modify: `src/main/java/com/river/LegalAssistant/service/DeepSeekService.java`
- Modify: `src/main/java/com/river/LegalAssistant/service/advanced/AdvancedLegalRagService.java`
- Modify: `src/main/java/com/river/LegalAssistant/controller/UnifiedChatController.java`
- Modify: `src/main/java/com/river/LegalAssistant/config/AiConfig.java`
- Modify: `src/main/resources/application.yml`

**Step 1: Write the failing test**

- Add or extend QA tests to assert:
  - KB-enabled `UNIFIED` QA does not fall back to local chat-only answers.
  - `ADVANCED_RAG` returns retrieval-backed sources and DeepSeek as final answer model when available.
  - `sourceCount` matches `sources`.

**Step 2: Run test to verify it fails**

Run:

- `./mvnw -Dtest=UnifiedChatControllerTest,AiConfigFallbackTest test`

Expected:

- Fails because current implementation still returns local-chat or empty-source responses.

**Step 3: Write minimal implementation**

- Route KB QA through retrieval-first logic.
- Keep local embedding / vector retrieval active.
- Use DeepSeek for final answer generation when available.
- Normalize sources into the unified response object.

**Step 4: Run test to verify it passes**

Run:

- `./mvnw -Dtest=UnifiedChatControllerTest,AiConfigFallbackTest test`

Expected:

- PASS.

### Task 3: Align benchmark helper and runner with new semantics

**Files:**
- Modify: `docs/evaluation/benchmark-lib.ps1`
- Modify: `docs/evaluation/benchmark-lib.Tests.ps1`
- Modify: `docs/evaluation/run-benchmark.ps1`

**Step 1: Write the failing test**

- Add tests for source extraction / hit detection against the updated QA response shape.

**Step 2: Run test to verify it fails**

Run:

- `Invoke-Pester .\docs\evaluation\benchmark-lib.Tests.ps1`

Expected:

- FAIL on the new source parsing expectations.

**Step 3: Write minimal implementation**

- Make hit detection tolerant to object / string source formats.
- Force benchmark runtime to use local embedding + DeepSeek generation for QA.

**Step 4: Run test to verify it passes**

Run:

- `Invoke-Pester .\docs\evaluation\benchmark-lib.Tests.ps1`

Expected:

- PASS.

### Task 4: Verify with focused benchmark rerun

**Files:**
- Modify: `docs/evaluation/run-benchmark.ps1`
- Output: `docs/evaluation/benchmark-results.csv`
- Output: `docs/evaluation/benchmark-summary.json`

**Step 1: Run focused verification**

Run:

- `powershell -ExecutionPolicy Bypass -File .\docs\evaluation\run-benchmark.ps1 -KeepDatabase`

Expected:

- Full run completes.
- QA retrieval hit rate improves above the current `0.0%`.
- QA answer completeness improves above the current `0.68 / 5`.

**Step 2: Inspect outputs**

- Check `benchmark-results.csv` for non-zero `source_count` and `retrieval_hit`.
- Check `benchmark-summary.json` for improved QA quality metrics.

### Task 5: Backfill docs with the new baseline

**Files:**
- Modify: `README.md`
- Modify: `README_EN.md`
- Modify: `docs/evaluation/eval-report.md`

**Step 1: Update benchmark wording**

- State clearly that local models are used for embedding / retrieval only.
- State clearly that DeepSeek is the QA generation model for this benchmark baseline.

**Step 2: Update benchmark numbers**

- Replace prior baseline values with the rerun results.

**Step 3: Verify docs against outputs**

Run:

- `powershell -ExecutionPolicy Bypass -File .\docs\evaluation\aggregate-results.ps1 -InputCsv .\docs\evaluation\benchmark-results.csv`

Expected:

- README and eval report match the generated summary.
