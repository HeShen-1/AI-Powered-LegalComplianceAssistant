import { createPinia } from 'pinia'

const pinia = createPinia()

// 导出所有store
export { useUserStore } from './modules/user'
export { useAppStore } from './modules/app'

export default pinia
