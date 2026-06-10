import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

// 全局权限路由守卫
import '@/access'

// 按钮级权限指令（支持权限码和角色）
import { useLoginUserStore } from '@/stores/loginUser'
import checkAccess from '@/access/checkAccess'

app.directive('permission', {
  mounted(el: HTMLElement, binding) {
    const needAccess = binding.value as string | string[]
    if (!needAccess) return
    const store = useLoginUserStore()
    if (!checkAccess(store.loginUser, needAccess)) {
      el.parentNode?.removeChild(el)
    }
  },
})

app.mount('#app')
