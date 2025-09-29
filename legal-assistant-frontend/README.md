# 法律合规智能审查助手 - 前端应用

基于 Vue 3 + TypeScript + Element Plus 构建的现代化法律智能助手前端应用。

## ✨ 功能特性

### 🤖 核心功能
- **AI智能问答** - 基于 SSE 的实时聊天体验，支持流式响应
- **合同智能审查** - 支持多格式文件上传，实时进度展示
- **审查历史** - 完整的审查记录管理和检索
- **个人中心** - 用户信息管理和系统设置

### 🔐 用户系统
- **用户认证** - 完整的登录/注册流程
- **权限管理** - 基于角色的访问控制
- **状态管理** - 持久化的用户状态管理

### 👨‍💼 管理功能
- **用户管理** - 用户账户的增删改查
- **知识库管理** - 法律文档的批量上传和分类管理
- **系统统计** - 可视化的系统使用数据分析

### 🎨 界面特性
- **响应式设计** - 适配桌面和移动设备
- **现代化UI** - 基于 Element Plus 的简洁大气界面
- **实时通信** - Server-Sent Events 实现的流式数据传输
- **Markdown支持** - AI回复支持完整的 Markdown 格式渲染

## 🛠️ 技术栈

- **前端框架**: Vue 3 (Composition API)
- **开发语言**: TypeScript
- **构建工具**: Vite
- **UI组件库**: Element Plus
- **状态管理**: Pinia
- **路由管理**: Vue Router 4
- **HTTP客户端**: Axios
- **Markdown解析**: Marked + highlight.js
- **图表库**: ECharts

## 📦 快速开始

### 环境要求

- Node.js >= 18.0.0
- pnpm (推荐) 或 npm

### 安装依赖

```bash
# 进入项目目录
cd legal-assistant-frontend

# 安装依赖
pnpm install
# 或使用 npm
npm install
```

### 开发环境

```bash
# 启动开发服务器
pnpm dev
# 或使用 npm
npm run dev
```

启动后访问 [http://localhost:3000](http://localhost:3000)

### 演示账户

为了方便体验，项目提供了以下演示账户：

- **普通用户**: `demo` / `123456`
- **管理员**: `admin` / `123456`

> 💡 提示：在后端服务未启动的情况下，您可以使用这些演示账户来体验前端功能。

### 构建生产版本

```bash
# 构建生产版本
pnpm build
# 或使用 npm
npm run build
```

### 类型检查

```bash
# 运行类型检查
pnpm type-check
# 或使用 npm
npm run type-check
```

## 📁 项目结构

```
legal-assistant-frontend/
├── public/                  # 静态资源
├── src/
│   ├── api/                 # API 服务模块
│   │   ├── index.ts         # Axios 实例和拦截器
│   │   ├── aiService.ts     # AI 相关 API
│   │   ├── contractService.ts # 合同审查 API
│   │   ├── userService.ts   # 用户管理 API
│   │   ├── knowledgeBaseService.ts # 知识库 API
│   │   └── healthService.ts # 系统健康检查 API
│   ├── assets/              # 资源文件
│   │   └── styles/          # 全局样式
│   ├── components/          # 全局通用组件
│   ├── layout/              # 页面布局组件
│   │   └── index.vue        # 主布局组件
│   ├── router/              # 路由配置
│   │   └── index.ts         # 路由定义和守卫
│   ├── store/               # 状态管理
│   │   ├── index.ts         # Pinia 实例
│   │   └── modules/
│   │       └── user.ts      # 用户状态模块
│   ├── types/               # TypeScript 类型定义
│   │   └── api.ts           # API 相关类型
│   ├── utils/               # 工具函数
│   ├── views/               # 页面组件
│   │   ├── login/           # 登录页
│   │   ├── dashboard/       # 工作台
│   │   ├── chat/            # AI智能问答
│   │   ├── contract/        # 合同审查
│   │   ├── history/         # 审查历史
│   │   ├── profile/         # 个人中心
│   │   ├── admin/           # 管理员页面
│   │   │   ├── users/       # 用户管理
│   │   │   ├── knowledge/   # 知识库管理
│   │   │   └── statistics/  # 系统统计
│   │   └── error/           # 错误页面
│   ├── App.vue              # 根组件
│   └── main.ts              # 应用入口
├── index.html               # HTML 模板
├── package.json             # 依赖配置
├── tsconfig.json            # TypeScript 配置
├── vite.config.ts           # Vite 配置
└── README.md                # 项目说明
```

## 🔧 配置说明

### 代理配置

项目已配置开发环境代理，自动将 `/api` 请求转发到后端服务：

```typescript
// vite.config.ts
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080', // 后端服务地址
      changeOrigin: true,
    },
  },
}
```

### 环境变量

可以在项目根目录创建 `.env.local` 文件配置环境变量：

```env
# API 基础地址（可选，默认使用代理）
VITE_API_BASE_URL=http://localhost:8080

# 应用标题
VITE_APP_TITLE=法律合规智能审查助手
```

## 🚀 核心功能说明

### 1. AI智能问答

- **多模式支持**: 基础聊天、RAG知识问答、智能法律顾问
- **流式响应**: 基于 SSE 的实时数据流，打字机效果展示
- **Markdown渲染**: 完整支持代码高亮、表格、列表等格式
- **对话管理**: 支持清空对话、导出记录、快速问题等功能

### 2. 合同审查

- **文件上传**: 支持 PDF、Word、文本格式文件
- **实时进度**: 通过 SSE 实时展示分析进度和日志
- **风险分析**: 智能识别风险条款，提供分级评估
- **报告生成**: 自动生成 PDF 格式的详细审查报告

### 3. 用户认证

- **JWT认证**: 基于 Token 的安全认证机制
- **路由守卫**: 自动处理登录状态和权限检查
- **状态持久化**: 自动保存和恢复用户登录状态

### 4. 管理后台

- **用户管理**: 完整的用户增删改查功能
- **知识库管理**: 支持单文档和批量上传，分类管理
- **统计分析**: 基于 ECharts 的可视化数据展示

## 🎨 主题和样式

项目使用 CSS 变量定义主题色彩，支持主题切换：

```css
:root {
  --primary-color: #409eff;
  --success-color: #67c23a;
  --warning-color: #e6a23c;
  --danger-color: #f56c6c;
  /* ... 更多变量 */
}
```

## 📱 响应式设计

项目采用移动优先的响应式设计策略：

- **断点设计**: 768px (平板)、1024px (桌面)
- **弹性布局**: 使用 CSS Grid 和 Flexbox
- **组件适配**: 所有组件都支持移动设备显示

## 🔍 开发建议

### 代码规范

- 使用 TypeScript 严格模式
- 遵循 Vue 3 Composition API 最佳实践
- 组件采用 `<script setup>` 语法
- 统一使用 Element Plus 组件库

### 性能优化

- 路由懒加载
- 组件按需导入
- 图片懒加载
- 代码分割

### 错误处理

- 全局错误边界
- API 请求统一错误处理
- 用户友好的错误提示

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 Apache 许可证 - 详情请查看 [LICENSE](LICENSE) 文件。

## 📞 支持

如有问题或建议，请通过以下方式联系：

- 创建 [Issue](https://github.com/your-repo/legal-assistant-frontend/issues)
- 发送邮件至 support@example.com

---

**法律合规智能审查助手** - 让法律审查更智能、更高效！ 🚀
