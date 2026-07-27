import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { showToast } from './components'
import router from './router'
import { installGlobalErrorReporting, reportClientError } from './services/clientErrorReporter'
import { configureRequestLifecycle } from './services/request'
import { useSessionStore } from './stores/session'
import './styles/base.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const session = useSessionStore(pinia)
app.config.errorHandler = (error) => {
  void reportClientError('VUE', error)
}
configureRequestLifecycle({
  onError: (notice) => showToast('error', '请求未完成', notice.message),
  onSessionExpired: (notice) =>
    session.clearSession(session.status === 'authenticated' ? notice : undefined),
})

installGlobalErrorReporting()
app.use(router).mount('#app')
