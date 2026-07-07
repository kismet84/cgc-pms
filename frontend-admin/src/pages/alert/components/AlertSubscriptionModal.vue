<script setup lang="ts">
import type { AlertSubscriptionConfig } from '@/types/alert'
import { ALERT_CHANNEL_LABELS, RULE_CATEGORY_LABELS } from '@/types/alert'

defineProps<{
  open: boolean
  loading: boolean
  saving: boolean
  form: AlertSubscriptionConfig
  availableSubscriptionChannels: string[]
  availableSubscriptionDomains: string[]
  availableSeverityOptions: string[]
  defaultSubscriptionEnabled: boolean
  defaultStatusChangeEnabled: boolean
  handleSaveSubscription: () => void
}>()

defineEmits<{
  (e: 'update:open', value: boolean): void
}>()
</script>

<template>
  <a-modal
    :open="open"
    title="通知订阅"
    :confirm-loading="saving"
    @update:open="$emit('update:open', $event)"
    @ok="handleSaveSubscription"
  >
    <a-spin :spinning="loading">
      <div class="alert-subscription-form">
        <div class="alert-subscription-row">
          <span class="alert-subscription-label">接收通知</span>
          <a-switch v-model:checked="form.enabled" :disabled="!defaultSubscriptionEnabled" />
        </div>
        <div class="alert-subscription-row is-block">
          <span class="alert-subscription-label">通知渠道</span>
          <a-checkbox-group v-model:value="form.channels">
            <a-checkbox v-for="channel in availableSubscriptionChannels" :key="channel" :value="channel">
              {{ ALERT_CHANNEL_LABELS[channel] ?? channel }}
            </a-checkbox>
          </a-checkbox-group>
        </div>
        <div class="alert-subscription-row is-block">
          <span class="alert-subscription-label">预警域</span>
          <a-checkbox-group v-model:value="form.domains">
            <a-checkbox v-for="domain in availableSubscriptionDomains" :key="domain" :value="domain">
              {{ RULE_CATEGORY_LABELS[domain] ?? domain }}
            </a-checkbox>
          </a-checkbox-group>
        </div>
        <div class="alert-subscription-row is-block">
          <span class="alert-subscription-label">最低严重度</span>
          <a-radio-group v-model:value="form.minSeverity">
            <a-radio-button v-for="item in availableSeverityOptions" :key="item" :value="item">
              {{ item === 'HIGH' ? '高危' : item === 'MEDIUM' ? '中危' : '低危' }}
            </a-radio-button>
          </a-radio-group>
        </div>
        <div class="alert-subscription-row">
          <span class="alert-subscription-label">状态变更通知</span>
          <a-switch v-model:checked="form.notifyOnStatusChanged" :disabled="!defaultStatusChangeEnabled" />
        </div>
      </div>
    </a-spin>
  </a-modal>
</template>

<style scoped>
.alert-subscription-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.alert-subscription-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.alert-subscription-row.is-block {
  align-items: flex-start;
  flex-direction: column;
}

.alert-subscription-label {
  color: #1f2329;
  font-weight: 600;
}
</style>
