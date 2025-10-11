# 法律合规智能审查助手 - 前端应用

基于 Vue 3 + TypeScript + Element Plus 构建的现代化法律助手前端应用。

## ✨ 功能特性

### 🔐 用户认证系统
- JWT Token 认证
- 用户登录/注册
- 演示账户支持（demo/123456, admin/123456）
- 自动token刷新和状态管理

### 🤖 AI智能问答
- **多模式对话**：基础RAG、高级Agent、高级RAG
- **流式响应**：实时打字机效果
- **Markdown支持**：完整的Markdown渲染，包括代码高亮
- **会话管理**：支持会话记忆和历史管理

### 📄 合同审查
- **文件上传**：支持PDF、DOC、DOCX格式
- **实时进度**：SSE实时显示分析进度
- **可视化报告**：风险项分析、关键条款提取
- **报告下载**：生成PDF格式审查报告

### 📋 审查历史
- **历史记录管理**：查看、搜索、筛选
- **多维度筛选**：按状态、风险等级、时间范围
- **详细结果查看**：完整的审查结果展示

### 👥 后台管理（管理员）
- **用户管理**：用户增删改查、权限管理
- **知识库管理**：文档上传、向量索引管理
- **统计仪表盘**：系统使用统计、性能监控

### 📱 响应式设计
- 完美适配桌面和移动设备
- 现代化UI设计
- 流畅的用户体验

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
- **实时通信**: Server-Sent Events (SSE)

## 📦 安装与运行

### 环境要求
- Node.js >= 18.0.0
- pnpm (推荐) 或 npm/yarn

### 安装依赖
```bash
cd legal-assistant-frontend
pnpm install
```

### 开发环境运行
```bash
pnpm dev
```

应用将在 http://localhost:3000 启动

### 生产构建
```bash
pnpm build
```

### 类型检查
```bash
pnpm type-check
```

## 🏗️ 项目结构

```
legal-assistant-frontend/
├── public/                  # 静态资源
├── src/
│   ├── api/                 # API 服务模块
│   │   ├── index.ts         # Axios 实例配置
│   │   ├── authService.ts   # 认证服务
│   │   ├── chatService.ts   # 聊天服务
│   │   └── contractService.ts # 合同服务
│   ├── assets/              # 资源文件
│   ├── components/          # 全局组件
│   ├── layout/              # 布局组件
│   │   └── index.vue        # 主布局
│   ├── router/              # 路由配置
│   │   └── index.ts         # 路由定义
│   ├── store/               # 状态管理
│   │   └── modules/
│   │       └── user.ts      # 用户状态
│   ├── types/               # TypeScript 类型定义
│   │   └── api.ts           # API 类型
│   ├── views/               # 页面组件
│   │   ├── login/           # 登录页
│   │   ├── dashboard/       # 工作台
│   │   ├── chat/            # AI问答
│   │   ├── contract/        # 合同审查
│   │   ├── history/         # 审查历史
│   │   ├── profile/         # 个人中心
│   │   ├── admin/           # 管理员页面
│   │   └── error/           # 错误页面
│   ├── App.vue              # 根组件
│   └── main.ts              # 应用入口
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts           # Vite 配置
└── README.md
```

## 🔧 配置说明

### API 配置
- 后端API地址：`http://localhost:8080`
- API前缀：`/api/v1`
- 认证方式：JWT Bearer Token

### 代理配置
开发环境下，Vite会自动代理API请求到后端服务：
```typescript
// vite.config.ts
server: {
  proxy: {
    '/api/v1': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

## 🎯 核心功能使用

### 用户认证
```typescript
// 登录
const userStore = useUserStore()
await userStore.login(username, password)

// 获取用户信息
const user = userStore.userInfo
const isAdmin = userStore.isAdmin
```

### AI聊天
```typescript
// 流式聊天
import { createChatStreamPost } from '@/api/chatService'

await createChatStreamPost(
  { prompt, mode, sessionId },
  (data) => console.log('接收数据:', data),
  (error) => console.error('错误:', error),
  () => console.log('完成')
)
```

### 文件上传
```typescript
// 合同上传
const uploadAction = '/api/v1/contracts/upload'
const uploadHeaders = {
  'Authorization': `Bearer ${userStore.token}`
}
```

## 🎨 样式规范

### CSS变量
```css
:root {
  --primary-color: #409EFF;
  --success-color: #67C23A;
  --warning-color: #E6A23C;
  --danger-color: #F56C6C;
  --info-color: #909399;
}
```

### 响应式断点
- `xs`: < 768px (手机)
- `sm`: 768px - 992px (平板)
- `md`: 992px - 1200px (小屏桌面)
- `lg`: 1200px - 1920px (大屏桌面)
- `xl`: > 1920px (超大屏)

## 🚀 部署指南

### 构建生产版本
```bash
pnpm build
```

### 部署到Nginx
```nginx
server {
    listen 80;
    server_name your-domain.com;
    root /path/to/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://backend-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 🔍 开发指南

### 添加新页面
1. 在 `src/views/` 下创建页面组件
2. 在 `src/router/index.ts` 中添加路由
3. 在布局组件中添加菜单项

### 添加新API
1. 在 `src/types/api.ts` 中定义类型
2. 在 `src/api/` 下创建服务文件
3. 使用统一的 `apiClient` 实例

### 状态管理
使用 Pinia 进行状态管理：
```typescript
// 定义store
export const useExampleStore = defineStore('example', () => {
  const state = ref('')
  const getter = computed(() => state.value)
  const action = () => { /* ... */ }
  
  return { state, getter, action }
})
```

## 🐛 常见问题

### Q: 登录后页面空白？
A: 检查后端服务是否启动，API地址是否正确配置。

### Q: 文件上传失败？
A: 确认文件格式和大小限制，检查后端上传接口。

### Q: SSE连接失败？
A: 检查浏览器是否支持SSE，确认后端SSE接口正常。

### Q: 图表不显示？
A: 确认ECharts依赖已安装，检查容器元素是否存在。

## 📄 许可证

MIT License

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📞 联系方式

如有问题或建议，请通过以下方式联系：
- 项目Issues: [GitHub Issues](https://github.com/your-repo/issues)
- 邮箱: your-email@example.com

---

**法律合规智能审查助手** - 让法律工作更智能、更高效！ 🚀