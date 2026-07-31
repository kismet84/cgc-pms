import type { Directive } from 'vue'
import { h, render } from 'vue'
import { Spin } from 'ant-design-vue'

const LOADING_ATTR = 'data-v-loading-mount'

const vLoading: Directive<HTMLElement, boolean> = {
  mounted(el: HTMLElement, binding) {
    // Ensure the element can contain an absolute-positioned overlay
    const position = getComputedStyle(el).position
    if (position === 'static' || position === '') {
      el.style.position = 'relative'
    }

    // Create a mount container inside the element
    const mountEl = document.createElement('div')
    mountEl.setAttribute(LOADING_ATTR, '')
    mountEl.style.position = 'absolute'
    mountEl.style.inset = '0'
    mountEl.style.zIndex = '10'
    mountEl.style.display = binding.value ? '' : 'none'
    el.appendChild(mountEl)

    // Render the Spin component into the container
    const vnode = h(Spin, { spinning: binding.value })
    render(vnode, mountEl)
  },

  updated(el: HTMLElement, binding) {
    const mountEl = el.querySelector(`[${LOADING_ATTR}]`) as HTMLElement | null
    if (!mountEl) return

    mountEl.style.display = binding.value ? '' : 'none'
    const vnode = h(Spin, { spinning: binding.value })
    render(vnode, mountEl)
  },

  unmounted(el: HTMLElement) {
    const mountEl = el.querySelector(`[${LOADING_ATTR}]`)
    if (mountEl) {
      render(null, mountEl as HTMLElement)
      mountEl.remove()
    }
  },
}

export default vLoading
