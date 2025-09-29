import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './store'

// Element Plus样式
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

// 自定义样式
import './assets/styles/index.css'

// highlight.js样式
import 'highlight.js/styles/github.css'

const app = createApp(App)

app.use(pinia)
app.use(router)

app.mount('#app')
