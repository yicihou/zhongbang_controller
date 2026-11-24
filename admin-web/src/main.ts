/**
 * 管理端入口。浏览器打开页面后先执行这里，再画出 App.vue 里的路由出口。
 * Pinia 管登录态和通知；Vue Router 管页面地址；Element Plus 用中文文案。
 * 不在这里请求接口。默认请求本机 8080，见 config.ts 的 USE_MOCK。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import App from './App.vue'
import { router } from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
