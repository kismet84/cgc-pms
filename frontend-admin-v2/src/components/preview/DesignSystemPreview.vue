<script setup lang="ts">
import { ref } from 'vue'
import {
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2ErrorBoundary,
  V2GlassButton,
  V2Grid,
  V2Input,
  V2PageState,
  V2Select,
  V2Skeleton,
  V2Stack,
  type V2SelectOption,
} from '@/components'

const dialogOpen = ref(false)
const confirmOpen = ref(false)
const keyword = ref('中建国际金融中心项目')
const emptyKeyword = ref('')
const period = ref('2026-07')
const options: V2SelectOption[] = [
  { value: '2026-07', label: '2026年7月' },
  { value: '2026-06', label: '2026年6月' },
  { value: '2026-05', label: '2026年5月', disabled: true },
]
</script>

<template>
  <main class="preview-page">
    <header class="preview-header">
      <div>
        <p>CGC-PMS / CLEAN-ROOM V2</p>
        <h1>设计系统组件基线</h1>
      </div>
      <V2Badge tone="success" dot>独立 V2</V2Badge>
    </header>

    <V2Stack :gap="4">
      <V2Alert title="视觉源已锁定" tone="info">
        真白面板、深蓝文字、轻边框与蓝红橙绿语义色；不包含真实业务数据。
      </V2Alert>

      <V2Card title="操作与状态" subtitle="按钮、徽标、加载和禁用状态">
        <V2Stack :gap="4">
          <V2Cluster :gap="2">
            <V2Button>主要操作</V2Button>
            <V2Button variant="secondary">次要操作</V2Button>
            <V2Button variant="ghost">文字操作</V2Button>
            <V2Button variant="danger">危险操作</V2Button>
          </V2Cluster>
          <V2Cluster :gap="2">
            <V2Button loading>处理中</V2Button>
            <V2Button disabled>不可用</V2Button>
            <V2Badge tone="danger">高风险</V2Badge>
            <V2Badge tone="warning">需关注</V2Badge>
            <V2Badge tone="success">正常</V2Badge>
          </V2Cluster>
        </V2Stack>
      </V2Card>

      <V2Grid min-item-width="17rem" :gap="3">
        <V2Card title="表单控件" subtitle="标签、提示、错误和加载状态">
          <V2Stack :gap="4">
            <V2Input v-model="keyword" label="当前项目" hint="支持项目名称或编号" />
            <V2Select v-model="period" label="报告期" :options="options" />
            <V2Input v-model="emptyKeyword" label="校验示例" error="请输入有效内容" required />
            <V2Input model-value="正在加载" label="加载状态" loading />
            <V2Input model-value="无权修改" label="禁用状态" disabled />
          </V2Stack>
        </V2Card>

        <V2Card title="反馈与骨架" subtitle="页面和异步状态的最小表达">
          <V2Stack :gap="3">
            <V2Alert title="保存成功" tone="success">变更已进入当前 V2 会话。</V2Alert>
            <V2Alert title="存在校验错误" tone="danger" dismissible> 请检查必填项后重试。 </V2Alert>
            <V2Skeleton variant="rect" label="面板加载中" />
            <V2Cluster :gap="3">
              <V2Skeleton variant="circle" label="头像加载中" />
              <V2Stack :gap="2" class="preview-skeleton-copy">
                <V2Skeleton label="标题加载中" />
                <V2Skeleton label="说明加载中" />
              </V2Stack>
            </V2Cluster>
          </V2Stack>
        </V2Card>
      </V2Grid>

      <V2Card title="对话框" subtitle="Escape、背景关闭、焦点进入和恢复">
        <V2Cluster :gap="2">
          <V2Button variant="secondary" @click="dialogOpen = true">打开对话框</V2Button>
          <V2Button variant="danger" @click="confirmOpen = true">打开确认框</V2Button>
          <V2GlassButton text="玻璃操作" />
        </V2Cluster>
      </V2Card>

      <V2ErrorBoundary>
        <V2PageState
          kind="empty"
          title="暂无组件数据"
          description="页面状态与全局错误边界使用统一语义和恢复入口。"
          :heading-level="2"
        >
          <template #actions><V2Button variant="secondary">刷新状态</V2Button></template>
        </V2PageState>
      </V2ErrorBoundary>
    </V2Stack>

    <V2Dialog
      v-model:open="dialogOpen"
      title="确认当前操作"
      description="此对话框仅展示组件行为，不执行业务写入。"
    >
      <p class="preview-dialog-copy">确认前可继续检查输入内容和权限边界。</p>
      <template #footer>
        <V2Button variant="ghost" @click="dialogOpen = false">取消</V2Button>
        <V2Button @click="dialogOpen = false">确认</V2Button>
      </template>
    </V2Dialog>
    <V2ConfirmDialog
      :open="confirmOpen"
      title="确认演示操作"
      description="组件预览不会执行业务写入。"
      danger
      @close="confirmOpen = false"
      @confirm="confirmOpen = false"
    />
  </main>
</template>

<style scoped>
.preview-page {
  width: min(var(--v2-page-max-width), 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: var(--v2-page-gutter);
}

.preview-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--v2-space-4);
  margin-bottom: var(--v2-space-5);
  padding-bottom: var(--v2-space-4);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
}

.preview-header p,
.preview-header h1,
.preview-dialog-copy {
  margin: 0;
}

.preview-header p {
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
  letter-spacing: 0.08em;
}

.preview-header h1 {
  margin-top: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: clamp(var(--v2-font-size-21), 3vw, var(--v2-font-size-28));
  line-height: var(--v2-line-height-tight);
}

.preview-skeleton-copy {
  min-width: 12rem;
  flex: 1;
}

.preview-dialog-copy {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
  line-height: var(--v2-line-height-body);
}

@media (max-width: 30rem) {
  .preview-header {
    align-items: flex-start;
  }
}
</style>
