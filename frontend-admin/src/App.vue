<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { message } from 'ant-design-vue'

message.config({
  top: '80px',
  duration: 3,
  maxCount: 3,
})

function getPopupContainer(trigger?: HTMLElement): HTMLElement {
  return trigger?.parentElement ?? document.body
}

const theme = {
  token: {
    colorPrimary: '#1668dc',
    colorInfo: '#0ea5e9',
    colorSuccess: '#16a34a',
    colorWarning: '#f59e0b',
    colorError: '#dc2626',
    colorText: '#172033',
    colorTextSecondary: '#475569',
    colorTextTertiary: '#64748b',
    colorBorder: '#e4e9f2',
    colorBgLayout: '#f5f7fb',
    colorBgContainer: '#ffffff',
    borderRadius: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif",
    controlHeight: 34,
  },
  components: {
    Button: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Input: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Select: {
      borderRadius: 6,
      controlHeight: 34,
    },
    Table: {
      headerBg: '#f8fafc',
      headerColor: '#475569',
      rowHoverBg: '#f4f8ff',
      borderColor: '#e4e9f2',
    },
    Tabs: {
      itemSelectedColor: '#1668dc',
      inkBarColor: '#1668dc',
    },
  },
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme" :get-popup-container="getPopupContainer">
    <RouterView />
  </a-config-provider>
</template>
