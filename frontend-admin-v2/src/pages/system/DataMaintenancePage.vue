<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { V2Alert, V2Button, V2Card, V2PageState, V2Stack, showToast } from '@/components'
import {
  loadDataMaintenancePreview,
  type DataMaintenancePreview,
} from '@/services/system-management'
import { isApiClientError } from '@/services/request'

const preview = ref<DataMaintenancePreview>()
const loading = ref(false)
const errorMessage = ref('')
const hostScriptCommand = computed(
  () =>
    `pwsh -NoProfile -File scripts/database/clear-business-data.ps1 -Database ${preview.value?.database ?? '<database>'}`,
)

async function loadPreview(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    preview.value = await loadDataMaintenancePreview()
  } catch (value) {
    errorMessage.value =
      isApiClientError(value) || value instanceof Error ? value.message : '预览加载失败'
  } finally {
    loading.value = false
  }
}

async function refreshPreview(): Promise<void> {
  await loadPreview()
  if (!errorMessage.value) showToast('success', '预检已刷新', '已读取最新数据库统计。')
}

async function copyCommand(): Promise<void> {
  try {
    await navigator.clipboard.writeText(hostScriptCommand.value)
    showToast('success', '命令已复制', '请在应用宿主机按运维流程执行。')
  } catch {
    showToast('error', '复制失败', '请手动选择并复制命令。')
  }
}

onMounted(loadPreview)
</script>

<template>
  <V2Stack class="data-maintenance-page" :gap="4">
    <V2Card title="数据维护" :heading-level="1"></V2Card>

    <V2Alert tone="info" title="只读预览">
      页面仅展示服务端预检结果，不直接清理数据。实际清理必须由授权运维人员在应用宿主机执行脚本。
    </V2Alert>

    <V2PageState
      v-if="loading && !preview"
      kind="loading"
      title="正在读取数据维护预览"
      description="服务端正在核对数据库、保留项和可清理项。"
    />

    <V2PageState
      v-else-if="errorMessage && !preview"
      kind="error"
      title="数据维护预览加载失败"
      :description="errorMessage"
    >
      <template #actions>
        <V2Button size="small" variant="secondary" @click="loadPreview">重新加载</V2Button>
      </template>
    </V2PageState>

    <template v-else-if="preview">
      <V2Alert v-if="errorMessage" tone="warning" title="刷新失败">
        {{ errorMessage }}；当前仍展示上次成功读取的结果。
      </V2Alert>

      <V2Card title="预检结果">
        <template #actions>
          <V2Button size="small" variant="secondary" :loading="loading" @click="refreshPreview">
            刷新
          </V2Button>
        </template>
        <dl class="data-maintenance-page__facts">
          <div>
            <dt>数据库</dt>
            <dd>{{ preview.database }}</dd>
          </div>
          <div>
            <dt>清理策略指纹</dt>
            <dd>
              <code>{{ preview.policyFingerprint }}</code>
            </dd>
          </div>
          <div>
            <dt>是否允许清理</dt>
            <dd>{{ preview.eligible ? '允许' : '阻止' }}</dd>
          </div>
          <div>
            <dt>预计清理</dt>
            <dd>
              {{ preview.clearTableCount }} 张表 / {{ preview.clearRowCount }} 行 /
              {{ preview.sysFileCount }} 个文件
            </dd>
          </div>
        </dl>
        <V2Alert
          :tone="preview.eligible ? 'success' : 'danger'"
          :title="preview.eligible ? '预检通过' : '预检阻止清理'"
        >
          {{
            preview.blockers.length > 0
              ? preview.blockers.join('；')
              : '未发现阻塞项；仍须在宿主机执行脚本。'
          }}
        </V2Alert>
      </V2Card>

      <V2Card title="保留数据组">
        <ul v-if="preview.retainedGroups.length" class="data-maintenance-page__list">
          <li v-for="group in preview.retainedGroups" :key="group.code">
            <strong>{{ group.code }}</strong>
            <span>{{ group.tableCount }} 张表 / {{ group.rowCount }} 行</span>
          </li>
        </ul>
        <p v-else class="data-maintenance-page__muted">服务端未返回保留数据组。</p>
      </V2Card>

      <V2Card title="忽略视图">
        <p v-if="preview.ignoredViews.length" class="data-maintenance-page__codes">
          <code v-for="view in preview.ignoredViews" :key="view">{{ view }}</code>
        </p>
        <p v-else class="data-maintenance-page__muted">无忽略视图。</p>
      </V2Card>

      <V2Card title="宿主机执行命令" subtitle="命令不包含数据库账号、密码或连接串。">
        <div class="data-maintenance-page__command">
          <code>{{ hostScriptCommand }}</code>
          <V2Button size="small" variant="secondary" @click="copyCommand"> 复制命令 </V2Button>
        </div>
      </V2Card>
    </template>
  </V2Stack>
</template>

<style scoped>
.data-maintenance-page__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-4);
  margin: 0 0 var(--v2-space-4);
}

.data-maintenance-page__facts div,
.data-maintenance-page__list li {
  display: grid;
  min-width: 0;
  gap: var(--v2-space-1);
}

.data-maintenance-page__facts dt,
.data-maintenance-page__muted {
  color: var(--v2-color-text-secondary);
}

.data-maintenance-page__facts dd {
  margin: 0;
  font-weight: 650;
}

.data-maintenance-page__facts code {
  overflow-wrap: anywhere;
}

.data-maintenance-page__list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.data-maintenance-page__list span {
  color: var(--v2-color-text-secondary);
}

.data-maintenance-page__codes,
.data-maintenance-page__command {
  display: flex;
  flex-wrap: wrap;
  gap: var(--v2-space-2);
  align-items: center;
  margin: 0;
}

.data-maintenance-page__codes code,
.data-maintenance-page__command code {
  overflow-wrap: anywhere;
  padding: var(--v2-space-2);
  border-radius: var(--v2-radius-sm);
  background: var(--v2-color-surface-subtle);
}

.data-maintenance-page__command code {
  flex: 1 1 24rem;
  user-select: all;
}

@media (max-width: 64rem) {
  .data-maintenance-page__facts,
  .data-maintenance-page__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 40rem) {
  .data-maintenance-page__facts,
  .data-maintenance-page__list {
    grid-template-columns: 1fr;
  }
}
</style>
