import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { showToast } from './components'
import router from './router'
import { installGlobalErrorReporting, reportClientError } from './services/clientErrorReporter'
import { clearAllFieldDrafts } from './services/fieldDrafts'
import { configurePwa } from './services/pwa'
import { configureRequestLifecycle } from './services/request'
import { registerSessionCacheClearer, useSessionStore } from './stores/session'
import './styles/base.css'

const desktopSessionKey = 'cgc-pms:desktop-shell'
const desktopRequested = new URLSearchParams(window.location.search).get('desktop') === '1'
let desktopShell = desktopRequested
try {
  if (desktopRequested) sessionStorage.setItem(desktopSessionKey, '1')
  else desktopShell = sessionStorage.getItem(desktopSessionKey) === '1'
} catch {
  desktopShell = desktopRequested
}

if (desktopShell) {
  document.documentElement.dataset.desktopShell = 'true'
  window.addEventListener('contextmenu', (event) => event.preventDefault(), { capture: true })

  const disableLinkStatus = (link: HTMLAnchorElement) => {
    const href = link.getAttribute('href')
    if (href === null) return
    link.dataset.desktopHref = href
    link.removeAttribute('href')
    if (!link.hasAttribute('role')) link.setAttribute('role', 'link')
    if (!link.hasAttribute('tabindex')) link.tabIndex = 0
  }
  const disableLinkStatuses = (root: ParentNode) => {
    if (root instanceof HTMLAnchorElement) disableLinkStatus(root)
    root.querySelectorAll<HTMLAnchorElement>('a[href]').forEach(disableLinkStatus)
  }
  const linkObserver = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === 'attributes' && record.target instanceof HTMLAnchorElement) {
        disableLinkStatus(record.target)
      } else {
        record.addedNodes.forEach((node) => {
          if (node instanceof Element) disableLinkStatuses(node)
        })
      }
    }
  })
  linkObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['href'],
    childList: true,
    subtree: true,
  })
  disableLinkStatuses(document)

  document.addEventListener(
    'click',
    (event) => {
      if (event.button !== 0 || !(event.target instanceof Element)) return
      const link = event.target.closest<HTMLAnchorElement>('a[data-desktop-href]')
      const href = link?.dataset.desktopHref
      if (!link || !href) return
      event.preventDefault()
      const target = link.target
      const url = new URL(href, window.location.href)
      if (target === '_blank' || event.ctrlKey || event.metaKey || event.shiftKey) {
        window.open(url.href, target || '_blank', 'noopener')
      } else if (url.origin === window.location.origin) {
        void router.push(`${url.pathname}${url.search}${url.hash}`)
      } else {
        window.location.assign(url.href)
      }
    },
    { capture: true },
  )
  document.addEventListener(
    'keydown',
    (event) => {
      if (event.key !== 'Enter' || !(event.target instanceof HTMLAnchorElement)) return
      if (!event.target.dataset.desktopHref) return
      event.preventDefault()
      event.target.click()
    },
    { capture: true },
  )
}

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const session = useSessionStore(pinia)
registerSessionCacheClearer(clearAllFieldDrafts)
app.config.errorHandler = (error) => {
  void reportClientError('VUE', error)
}
configureRequestLifecycle({
  onError: (notice) => showToast('error', '请求未完成', notice.message),
  onSessionExpired: (notice) =>
    session.clearSession(session.status === 'authenticated' ? notice : undefined),
  onSessionRefreshed: (userInfo) => session.replaceUserInfo(userInfo),
})

installGlobalErrorReporting()
app.use(router).mount('#app')
void configurePwa(() => showToast('info', '发现新版本', '刷新页面后使用最新版本。')).catch(
  () => undefined,
)
