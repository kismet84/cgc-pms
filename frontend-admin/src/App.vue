<script setup lang="ts">
import { RouterView } from 'vue-router'
import { onErrorCaptured } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { message } from 'ant-design-vue'

onErrorCaptured((err, instance, info) => {
  console.error('[App Error]', err, info)
  return false // 阻止向上传播
})

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
    colorPrimary: '#2563eb',
    colorInfo: '#0ea5e9',
    colorSuccess: '#16a34a',
    colorWarning: '#d97706',
    colorError: '#ef4444',
    colorText: '#152033',
    colorTextSecondary: '#64748b',
    colorTextTertiary: '#8a97aa',
    colorTextQuaternary: '#c5cfdd',
    colorBorder: '#e5ebf3',
    colorBorderSecondary: '#edf1f7',
    colorBgLayout: '#f6f8fb',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    fontFamily: "'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif",
    fontSize: 14,
    fontSizeSM: 13,
    fontSizeLG: 16,
    fontSizeXL: 18,
    fontWeightStrong: 700,
    borderRadius: 6,
    controlHeight: 34,
  },
  components: {
    Button: {
      colorText: '#152033',
      colorPrimary: '#2563eb',
      colorPrimaryHover: '#1d4ed8',
      colorPrimaryActive: '#1e40af',
      borderRadius: 4,
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
      headerColor: '#64748b',
      colorText: '#152033',
      rowHoverBg: '#f4f8ff',
      borderColor: '#e5ebf3',
      fontSize: 14,
      headerSplitColor: '#edf1f7',
    },
    Tabs: {
      itemColor: '#64748b',
      itemSelectedColor: '#2563eb',
      itemHoverColor: '#2563eb',
      inkBarColor: '#2563eb',
    },
    Card: {
      colorTextHeading: '#152033',
      fontSize: 14,
      headerFontSize: 15,
    },
    Modal: {
      titleFontSize: 16,
    },
    Drawer: {
      fontSizeLG: 16,
    },
    Form: {
      labelColor: '#64748b',
      fontSize: 14,
    },
  },
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme" :get-popup-container="getPopupContainer">
    <RouterView />
  </a-config-provider>
</template>
