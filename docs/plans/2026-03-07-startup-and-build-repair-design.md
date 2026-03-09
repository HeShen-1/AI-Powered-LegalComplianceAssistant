# 启动与构建修复设计

**日期**: 2026-03-07

**目标**

让项目在 Windows 本地开发环境中具备稳定、可复现的启动路径，并恢复前端 `npm run build` 可通过的状态；同时清理明显的配置风险和文档偏差。

**问题概览**

- Windows 启动脚本不能可靠加载 `.env`，`start-app.ps1` 直接解析失败，`start-app.bat` 会在批处理语法和编码边界上误解析。
- 后端虽然配置了 `auto-fallback`，但 Spring 容器在启动阶段仍强依赖 `deepSeekChatModel`，导致未设置 `DEEPSEEK_API_KEY` 时直接失败。
- 前端存在较多 TypeScript 严格模式错误，主要是后端 DTO 与前端类型漂移、第三方库 API 变更、未使用变量和空值处理不足。
- `application.yml` 中存在明文数据库默认值、开发期危险配置和与文档不一致的接口说明。

**推荐方案**

采用最小侵入修复：

1. 修复 Windows 启动脚本的 `.env` 加载与编码行为。
2. 将 DeepSeek 能力改为“有密钥启用、无密钥降级”，同时显式利用 `spring.ai.deepseek.chat.enabled` 控制自动配置。
3. 保持 TypeScript 严格模式，逐步修复前端类型问题，不通过放宽编译规则掩盖问题。
4. 收紧配置风险并同步 README。

**架构决策**

**1. 启动脚本**

- `start-app.ps1` 继续作为 PowerShell 入口，但改为更稳健的 `.env` 解析逻辑，避免字符串、括号和编码导致的脚本解析错误。
- `start-app.bat` 保留兼容性，但简化批处理逻辑，避免在 `for /f` 与 UTF-8 中文输出混用时破坏命令解析。

**2. DeepSeek 降级策略**

- 在 `application.yml` 中引入基于环境变量的 `spring.ai.deepseek.chat.enabled` 默认开关：未提供 `DEEPSEEK_API_KEY` 时默认禁用 DeepSeek Chat 自动配置。
- 在 `AiConfig` 中将 DeepSeek 相关依赖改为可选注入，统一通过一个选择函数决定使用 DeepSeek 还是 Ollama。
- `advancedChatClient`、主 `chatClient`、`ChatClient.Builder` 都使用相同的选择策略，防止不同 Bean 的行为分裂。
- `DeepSeekService` 保持按属性条件装配，仅在真正配置了密钥时启用。

**3. 前端类型修复**

- 优先修复高频文件：知识库、聊天、合同、历史、仪表盘。
- 原则是对齐真实 API，而不是对现有错误类型做“兼容性放宽”。
- 统一分页返回、审查记录字段、标签类型映射和 SSE 事件类型。
- 对第三方库升级带来的 API 差异采用当前库的正确写法，例如 `marked` 高亮配置和 `NodeJS` 类型引用。

**4. 配置与文档**

- 将数据库连接默认值改为环境变量优先，保留本地安全默认值但不再硬编码生产敏感值。
- 将 `Flyway clean` 改回默认禁用，避免误清库。
- README 中所有健康检查与文档地址对齐 `/api/v1` 上下文路径。

**测试策略**

- 后端：新增针对 `AiConfig` 的最小化单元测试，验证“无 DeepSeek Bean 时回退到 Ollama”的行为；再用 `mvn spring-boot:run` 做集成验证。
- 前端：以 `npm run build` 作为严格回归门，必要时增加局部类型辅助，但不新增无价值快照测试。
- 脚本：通过实际运行 `start-app.ps1` / `start-app.bat` 验证环境变量注入和启动流程。

**范围边界**

- 不新增功能，不重构业务流程，不调整页面视觉设计。
- 不引入新的前端测试框架。
- 不修改与当前启动/构建问题无关的业务逻辑。
