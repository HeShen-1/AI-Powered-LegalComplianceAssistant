# Startup And Build Repair Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the backend start locally without `DEEPSEEK_API_KEY`, restore frontend production build success, and align config/docs with the actual runtime behavior.

**Architecture:** Keep the existing hybrid AI design, but make DeepSeek an optional runtime capability instead of a startup-time hard dependency. Fix Windows startup scripts, then repair frontend types by aligning them with backend DTOs and current library APIs, and finally tighten risky config defaults plus README instructions.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring AI 1.0.2, PostgreSQL, Vue 3, TypeScript, Vite, Element Plus

---

### Task 1: Add backend fallback regression tests

**Files:**
- Create: `src/test/java/com/river/LegalAssistant/config/AiConfigFallbackTest.java`
- Reference: `src/main/java/com/river/LegalAssistant/config/AiConfig.java`

**Step 1: Write the failing test**

Add tests that construct `AiConfig` with mocked `PromptTemplateService` and `ChatModel` instances, then assert:
- `chatClient(...)` falls back to Ollama when DeepSeek is unavailable
- `chatClientBuilder(...)` returns a builder backed by Ollama when DeepSeek is unavailable

**Step 2: Run test to verify it fails**

Run: `mvn -Dtest=AiConfigFallbackTest test`
Expected: FAIL because `AiConfig` currently requires `deepSeekChatModel` and has no optional fallback for the builder.

**Step 3: Write minimal implementation**

Modify `src/main/java/com/river/LegalAssistant/config/AiConfig.java` to centralize model selection and allow optional DeepSeek usage.

**Step 4: Run test to verify it passes**

Run: `mvn -Dtest=AiConfigFallbackTest test`
Expected: PASS

### Task 2: Repair Windows startup and DeepSeek optional startup

**Files:**
- Modify: `start-app.ps1`
- Modify: `start-app.bat`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/river/LegalAssistant/config/AiConfig.java`

**Step 1: Write the failing verification**

Reproduce the failures with:
- `powershell -ExecutionPolicy Bypass -File .\start-app.ps1`
- `cmd /c .\start-app.bat`
- `mvn spring-boot:run`

Expected:
- PowerShell script parse error
- Batch script environment loading errors
- Backend startup failure without a valid DeepSeek key

**Step 2: Write minimal implementation**

- Harden `.env` loading in both scripts
- Set `spring.ai.deepseek.chat.enabled` from `DEEPSEEK_API_KEY`
- Ensure advanced/default/builder chat clients all degrade to Ollama when DeepSeek is unavailable

**Step 3: Run verification**

Run:
- `mvn -Dtest=AiConfigFallbackTest test`
- `powershell -ExecutionPolicy Bypass -File .\start-app.ps1`

Expected:
- Regression test passes
- Backend starts successfully without requiring DeepSeek

### Task 3: Use build failures as the red test for frontend typing

**Files:**
- Modify: `legal-assistant-frontend/src/types/api.ts`
- Modify: `legal-assistant-frontend/src/api/aiService.ts`
- Modify: `legal-assistant-frontend/src/layout/index.vue`
- Modify: `legal-assistant-frontend/src/router/index.ts`
- Modify: `legal-assistant-frontend/src/views/admin/knowledge/index.vue`
- Modify: `legal-assistant-frontend/src/views/admin/statistics/index.vue`
- Modify: `legal-assistant-frontend/src/views/chat/index.vue`
- Modify: `legal-assistant-frontend/src/views/contract/index.vue`
- Modify: `legal-assistant-frontend/src/views/dashboard/index.vue`
- Modify: `legal-assistant-frontend/src/views/history/index.vue`
- Modify: `legal-assistant-frontend/src/views/login/index.vue`
- Modify: `legal-assistant-frontend/src/views/profile/index.vue`

**Step 1: Run the failing test**

Run: `npm run build`
Expected: FAIL with TypeScript errors in the files above.

**Step 2: Write minimal implementation**

Fix the reported errors by:
- aligning DTO fields and paginated response shapes
- narrowing Element Plus tag types
- replacing outdated `marked` options usage
- removing unused imports/parameters
- fixing nullable and event typing

**Step 3: Run test to verify it passes**

Run: `npm run build`
Expected: PASS

### Task 4: Tighten config defaults and fix docs

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `README.md`
- Modify: `README_EN.md` (if English startup instructions mention old endpoints)

**Step 1: Write the failing verification**

Check:
- runtime context path is `/api/v1`
- README still points to `/api/health` and `/api/doc.html`
- config still includes risky/default-sensitive values

**Step 2: Write minimal implementation**

- remove or reduce hard-coded sensitive defaults
- disable dangerous Flyway clean by default
- update URLs and startup notes in README files

**Step 3: Run verification**

Run:
- `Select-String -Path README.md,README_EN.md -Pattern '/api/health|/api/doc.html'`
- `mvn spring-boot:run`
- `npm run build`

Expected:
- README paths match runtime behavior
- backend still starts
- frontend still builds
