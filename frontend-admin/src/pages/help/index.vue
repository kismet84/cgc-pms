<script setup lang="ts">
const shortcutColumns = [
  { title: '快捷键', dataIndex: 'shortcut' },
  { title: '功能', dataIndex: 'description' },
]

const shortcuts = [
  { key: '1', shortcut: 'Ctrl+S / Cmd+S', description: '保存当前表单' },
  { key: '2', shortcut: 'Ctrl+Enter', description: '提交审批' },
  { key: '3', shortcut: 'Esc', description: '关闭弹窗/抽屉' },
  { key: '4', shortcut: 'Ctrl+F', description: '搜索' },
  { key: '5', shortcut: 'Tab', description: '切换输入框' },
]

const faqItems = [
  {
    key: '1',
    header: '如何新建合同？',
    answer: '导航到"合同管理"→"新建合同"，按向导填写基本信息、清单和付款条件后提交。',
  },
  {
    key: '2',
    header: '如何查看审批进度？',
    answer: '在"审批管理"→"我的待办"查看待审批项，点击详情可查看审批历史和流程图。',
  },
  {
    key: '3',
    header: '忘记密码怎么办？',
    answer: '请联系系统管理员重置密码。管理员在"系统设置"中可重置用户密码。',
  },
  {
    key: '4',
    header: '如何上传文件？',
    answer: '在合同详情或项目详情页面，点击"上传附件"按钮，选择文件（支持 PDF/Word/Excel/图片，最大 50MB）。',
  },
  {
    key: '5',
    header: '数据多久备份一次？',
    answer: '系统每日凌晨自动备份数据库，保留最近 30 天备份。',
  },
  {
    key: '6',
    header: '页面加载慢怎么办？',
    answer: '请检查网络连接，或清除浏览器缓存后重试。如持续缓慢，请联系技术支持。',
  },
]
</script>

<template>
  <div class="help-page">
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>首页</a-breadcrumb-item>
      <a-breadcrumb-item>帮助</a-breadcrumb-item>
    </a-breadcrumb>

    <a-typography-title :level="2">帮助中心</a-typography-title>

    <!-- Section 1: 快捷键 -->
    <a-card title="快捷键" class="section-card">
      <a-table :columns="shortcutColumns" :data-source="shortcuts" :pagination="false" size="small" />
    </a-card>

    <!-- Section 2: 常见问题 -->
    <a-card title="常见问题" class="section-card">
      <a-collapse>
        <a-collapse-panel v-for="item in faqItems" :key="item.key" :header="item.header">
          <p>{{ item.answer }}</p>
        </a-collapse-panel>
      </a-collapse>
    </a-card>

    <!-- Section 3: 联系我们 -->
    <a-card title="联系我们" class="section-card">
      <a-descriptions :column="1" size="small">
        <a-descriptions-item label="技术支持邮箱">support@cgc-pms.com</a-descriptions-item>
        <a-descriptions-item label="技术支持电话">400-XXX-XXXX</a-descriptions-item>
        <a-descriptions-item label="工作时间">周一至周五 9:00 - 18:00</a-descriptions-item>
        <a-descriptions-item label="系统版本">v1.0.0</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<style scoped>
.help-page {
  max-width: 900px;
  margin: 0 auto;
}

.breadcrumb {
  margin-bottom: 14px;
}

.section-card {
  margin-bottom: 16px;
}

.section-card :deep(.ant-card-body) {
  padding: 16px;
}
</style>
