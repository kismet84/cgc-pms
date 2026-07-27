<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  V2Alert,
  V2Button,
  V2Card,
  V2ConfirmDialog,
  V2Input,
  V2Stack,
  showToast,
} from '@/components'
import { clearNonProductionDatabase } from '@/services/system-management'
import { isApiClientError } from '@/services/request'

const CONFIRMATION = 'CLEAR_NON_PROD_DATABASE'
const confirmation = ref('')
const acknowledged = ref(false)
const dialogOpen = ref(false)
const clearing = ref(false)
const canSubmit = computed(
  () => acknowledged.value && confirmation.value.trim() === CONFIRMATION && !clearing.value,
)

async function clearDatabase(): Promise<void> {
  if (!canSubmit.value) return
  clearing.value = true
  try {
    const result = await clearNonProductionDatabase()
    dialogOpen.value = false
    confirmation.value = ''
    acknowledged.value = false
    showToast('success', '数据维护完成', result)
  } catch (value) {
    showToast(
      'error',
      '数据维护失败',
      isApiClientError(value) || value instanceof Error ? value.message : '请求失败',
    )
  } finally {
    clearing.value = false
  }
}
</script>

<template>
  <V2Stack class="data-maintenance-page" :gap="4">
    <V2Card title="数据维护" :heading-level="1"></V2Card>

    <V2Alert tone="danger" title="不可逆操作">
      仅非生产环境超级管理员可用。清理会删除项目、合同、审批和财务等业务数据；系统用户、角色、菜单和字典保留，且无法回滚。
    </V2Alert>

    <V2Card title="清空非生产业务数据">
      <div class="data-maintenance-page__form">
        <V2Input
          v-model="confirmation"
          label="确认码"
          :placeholder="CONFIRMATION"
          autocomplete="off"
          hint="必须完整输入确认码。"
        />
        <label class="data-maintenance-page__acknowledge">
          <input v-model="acknowledged" type="checkbox" />
          <span>我确认目标是非生产环境，并已接受业务数据不可恢复。</span>
        </label>
        <V2Button variant="danger" :disabled="!canSubmit" @click="dialogOpen = true">
          清空非生产业务数据
        </V2Button>
      </div>
    </V2Card>

    <V2ConfirmDialog
      :open="dialogOpen"
      title="最终确认清空"
      description="确认后立即清空非生产业务数据，且无法恢复。"
      confirm-text="确认清空"
      danger
      :loading="clearing"
      @close="dialogOpen = false"
      @confirm="clearDatabase"
    />
  </V2Stack>
</template>

<style scoped>
.data-maintenance-page__form {
  display: grid;
  max-width: 44rem;
  gap: var(--v2-space-4);
}

.data-maintenance-page__acknowledge {
  display: flex;
  gap: var(--v2-space-2);
  align-items: flex-start;
  color: var(--v2-color-text-secondary);
}

.data-maintenance-page__acknowledge input {
  accent-color: var(--v2-color-primary);
}
</style>
