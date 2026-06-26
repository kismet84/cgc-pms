<script setup lang="ts">
import { RouterView } from 'vue-router'
import { onErrorCaptured } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { message } from 'ant-design-vue'
import { antdTheme, applyRootCssVariables } from '@/theme/tokens'

onErrorCaptured((err, instance, info) => {
  console.error('[App Error]', err, info)
  return false // 阻止向上传播
})

message.config({
  top: '80px',
  duration: 3,
  maxCount: 3,
})

applyRootCssVariables()

function getPopupContainer(): HTMLElement {
  return document.body
}
</script>

<template>
  <a-config-provider
    :locale="zhCN"
    :theme="antdTheme"
    :get-popup-container="getPopupContainer"
    :autoInsertSpaceInButton="false"
  >
    <RouterView />
  </a-config-provider>
</template>
