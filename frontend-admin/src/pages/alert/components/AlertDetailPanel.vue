<script setup lang="ts">
import { computed } from 'vue'
import type { AlertLogVO, AlertTraceVO } from '@/types/alert'
import { ALERT_PROCESS_STATUS_COLOR, SEVERITY_COLOR } from '@/types/alert'

type AlertProcessStatus = 'OPEN' | 'PROCESSED' | 'ARCHIVED' | 'INVALID'

const props = withDefaults(
  defineProps<{
    activeRecord: AlertLogVO | null
    activeTrace: AlertTraceVO | null
    traceLoading: boolean
    statusRemarkDraft: string
    currentOperator: string
    subscriptionRows: Array<{
      channel: string
      label: string
      enabled: boolean
      minSeverity: string
    }>
    subscriptionSummaryText?: string
    formatSeverityText: (value: string) => string
    formatDateTime: (value: unknown) => string
    getAlertDomainLabel: (record: AlertLogVO) => string
    getProjectName: (projectId: string | number) => string
    getAlertMessageText: (value: unknown) => string
    getProcessStatusLabel: (record: AlertLogVO) => string
    openSubscriptionModal: () => void
    handleMarkRead: (record: AlertLogVO) => void
    handleAcknowledge: (record: AlertLogVO) => void
    handleChangeStatus: (
      record: AlertLogVO,
      processStatus: AlertProcessStatus,
      statusRemark?: string,
    ) => void
    canOpenBusinessEntry: (record: AlertLogVO) => boolean
    openBusinessEntry: (record: AlertLogVO) => void
    handleSaveActiveResult: () => void
  }>(),
  {
    subscriptionSummaryText: '',
  },
)

const emit = defineEmits<{
  (e: 'update:statusRemarkDraft', value: string): void
}>()

const statusRemarkModel = computed({
  get: () => props.statusRemarkDraft,
  set: (value: string) => emit('update:statusRemarkDraft', value),
})
</script>

<template>
  <div class="alert-detail-panel">
    <div class="alert-detail-head">
      <div class="alert-detail-title">告警详情</div>
    </div>

    <template v-if="activeRecord">
      <section class="alert-detail-section">
        <div class="alert-section-title">基本信息</div>
        <div class="alert-detail-grid">
          <div class="alert-detail-item">
            <span>告警ID</span>
            <strong>{{ activeRecord.id }}</strong>
          </div>
          <div class="alert-detail-item">
            <span>严重度</span>
            <a-tag :color="SEVERITY_COLOR[activeRecord.severity] ?? 'default'">{{
              formatSeverityText(activeRecord.severity)
            }}</a-tag>
          </div>
          <div class="alert-detail-item">
            <span>处理状态</span>
            <a-tag
              :color="
                ALERT_PROCESS_STATUS_COLOR[String(activeRecord.processStatus ?? 'OPEN')] ??
                'default'
              "
            >
              {{ getProcessStatusLabel(activeRecord) }}
            </a-tag>
          </div>
          <div class="alert-detail-item">
            <span>已读状态</span>
            <span class="alert-read-state" :class="{ 'is-unread': activeRecord.isRead === 0 }">
              <i></i>
              {{ activeRecord.isRead === 0 ? '未读' : '已读' }}
            </span>
          </div>
          <div class="alert-detail-item">
            <span>触发时间</span>
            <strong>{{ formatDateTime(activeRecord.triggeredAt) }}</strong>
          </div>
          <div class="alert-detail-item">
            <span>响应期限</span>
            <strong>{{ formatDateTime(activeRecord.responseDueAt) }}</strong>
          </div>
          <div class="alert-detail-item">
            <span>处置期限</span>
            <strong>{{ formatDateTime(activeRecord.resolutionDueAt) }}</strong>
          </div>
          <div class="alert-detail-item">
            <span>升级级别</span>
            <a-tag :color="(activeRecord.escalationLevel ?? 0) >= 2 ? 'red' : 'orange'">
              {{ activeRecord.escalationLevel ? `L${activeRecord.escalationLevel}` : '未升级' }}
            </a-tag>
          </div>
        </div>
      </section>

      <section class="alert-detail-section">
        <div class="alert-section-title">告警内容</div>
        <div class="alert-content-list">
          <div class="alert-content-row">
            <span>规则域</span>
            <strong>{{ getAlertDomainLabel(activeRecord) }}</strong>
          </div>
          <div class="alert-content-row">
            <span>项目</span>
            <strong>{{ getProjectName(activeRecord.projectId) }}</strong>
          </div>
          <div class="alert-content-row">
            <span>消息摘要</span>
            <strong class="is-message">{{ getAlertMessageText(activeRecord.message) }}</strong>
          </div>
        </div>
      </section>

      <section class="alert-detail-section">
        <div class="alert-section-title">状态备注</div>
        <a-textarea
          v-model:value="statusRemarkModel"
          :maxlength="200"
          :auto-size="{ minRows: 4, maxRows: 6 }"
          placeholder="请填写处理备注，支持 200 字以内"
        />
        <div class="alert-detail-tip">
          当前状态：{{ getProcessStatusLabel(activeRecord) }}，保存时会同步备注。
        </div>
      </section>

      <section class="alert-detail-section">
        <div class="alert-section-title">处理信息</div>
        <div class="alert-content-list">
          <div class="alert-content-row">
            <span>接单责任人</span>
            <strong>{{ activeRecord.acknowledgedBy ?? '-' }}</strong>
          </div>
          <div class="alert-content-row">
            <span>接单时间</span>
            <strong>{{ formatDateTime(activeRecord.acknowledgedAt) }}</strong>
          </div>
          <div class="alert-content-row">
            <span>处理人</span>
            <strong>{{ activeRecord.processedBy ?? currentOperator }}</strong>
          </div>
          <div class="alert-content-row">
            <span>处理时间</span>
            <strong>{{ formatDateTime(activeRecord.processedAt) }}</strong>
          </div>
          <div class="alert-content-row">
            <span>归档时间</span>
            <strong>{{ formatDateTime(activeRecord.archivedAt) }}</strong>
          </div>
          <div class="alert-content-row">
            <span>最近升级时间</span>
            <strong>{{ formatDateTime(activeRecord.lastEscalatedAt) }}</strong>
          </div>
        </div>
      </section>

      <section class="alert-detail-section">
        <div class="alert-section-title">全链路追溯</div>
        <a-spin :spinning="traceLoading">
          <div v-if="activeTrace?.lifecycleEvents.length" class="alert-trace-list">
            <div
              v-for="event in activeTrace.lifecycleEvents"
              :key="String(event.id)"
              class="alert-trace-line"
            >
              <strong>{{ event.eventType }}</strong>
              <span>{{ event.fromStatus || '-' }} → {{ event.toStatus || '-' }}</span>
              <span>{{ formatDateTime(event.occurredAt) }}</span>
              <small>{{ event.remark || '-' }} · {{ event.payloadHash }}</small>
            </div>
          </div>
          <div v-else class="alert-detail-tip">暂无生命周期事件</div>
          <div class="alert-notification-trace">
            通知发送 {{ activeTrace?.notificationSendRecords.length ?? 0 }} 条，站内信
            {{ activeTrace?.notifications.length ?? 0 }} 条
          </div>
        </a-spin>
      </section>

      <section class="alert-detail-section">
        <div class="alert-section-row">
          <div class="alert-section-title">通知订阅</div>
          <a-button type="link" size="small" @click="openSubscriptionModal">编辑</a-button>
        </div>
        <div class="alert-subscription-summary">{{ subscriptionSummaryText }}</div>
        <div class="alert-subscription-table">
          <div class="alert-subscription-header">
            <span>通知渠道</span>
            <span>是否启用</span>
            <span>最低严重度</span>
          </div>
          <div v-for="item in subscriptionRows" :key="item.channel" class="alert-subscription-line">
            <span>{{ item.label }}</span>
            <a-switch :checked="item.enabled" disabled size="small" />
            <span>{{ formatSeverityText(item.minSeverity) }}</span>
          </div>
        </div>
      </section>

      <div class="alert-detail-actions">
        <a-button v-if="activeRecord.isRead === 0" @click="handleMarkRead(activeRecord)"
          >标记已读</a-button
        >
        <a-button
          v-if="
            String(activeRecord.processStatus ?? 'OPEN') === 'OPEN' && !activeRecord.acknowledgedBy
          "
          type="primary"
          @click="handleAcknowledge(activeRecord)"
        >
          接单处理
        </a-button>
        <a-button
          v-if="String(activeRecord.processStatus ?? 'OPEN') === 'PROCESSED'"
          @click="handleChangeStatus(activeRecord, 'ARCHIVED', statusRemarkDraft.trim())"
        >
          归档
        </a-button>
        <a-button
          v-if="canOpenBusinessEntry(activeRecord)"
          @click="openBusinessEntry(activeRecord)"
        >
          查看业务单据
        </a-button>
        <a-button
          v-if="
            String(activeRecord.processStatus ?? 'OPEN') === 'OPEN' && activeRecord.acknowledgedBy
          "
          type="primary"
          @click="handleSaveActiveResult"
          >保存处理结果</a-button
        >
      </div>
    </template>

    <div v-else class="alert-detail-empty">
      <div class="alert-detail-empty-title">未选择预警</div>
      <div class="alert-detail-empty-text">请从左侧列表选择一条告警查看详情。</div>
    </div>
  </div>
</template>

<style scoped>
.alert-detail-panel {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 0 10px;
  overflow-y: auto;
  background: transparent;
}

.alert-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}

.alert-detail-title {
  color: #1f2329;
  font-size: 15px;
  font-weight: 700;
}

.alert-detail-section {
  padding: 10px 16px 0;
}

.alert-section-title {
  margin-bottom: 8px;
  color: #1f2329;
  font-size: 15px;
  font-weight: 700;
}

.alert-section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.alert-detail-grid,
.alert-content-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.alert-detail-item,
.alert-content-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  color: #5f6b7a;
  font-size: 12px;
}

.alert-detail-item strong,
.alert-content-row strong {
  color: #1f2329;
  font-weight: 600;
  text-align: right;
}

.alert-content-row .is-message {
  max-width: 220px;
  line-height: 1.35;
  white-space: pre-wrap;
}

.alert-read-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5f6b7a;
}

.alert-read-state i {
  width: 7px;
  height: 7px;
  background: #52c41a;
  border-radius: 50%;
}

.alert-read-state.is-unread {
  color: #ff4d4f;
}

.alert-read-state.is-unread i {
  background: #ff4d4f;
}

.alert-detail-tip,
.alert-subscription-summary {
  margin-top: 6px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.35;
}

.alert-subscription-table {
  margin-top: 8px;
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: 10px;
}

.alert-subscription-header,
.alert-subscription-line {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 0.8fr;
  gap: 8px;
  align-items: center;
  padding: 6px 10px;
  font-size: 12px;
}

.alert-subscription-header {
  color: #5f6b7a;
  font-weight: 600;
  background: #fafbfd;
}

.alert-subscription-line + .alert-subscription-line {
  border-top: 1px solid #eef2f7;
}

.alert-detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  margin-top: 10px;
  border-top: 1px solid #eef2f7;
}

.alert-trace-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alert-trace-line {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 8px;
  padding: 8px;
  font-size: 12px;
  background: #fafbfd;
  border-radius: 8px;
}

.alert-trace-line small {
  grid-column: 1 / -1;
  overflow: hidden;
  color: #8a94a6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-notification-trace {
  margin-top: 8px;
  color: #5f6b7a;
  font-size: 12px;
}

.alert-detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  color: #8a94a6;
  text-align: center;
}

.alert-detail-empty-title {
  color: #1f2329;
  font-size: 16px;
  font-weight: 700;
}

.alert-detail-empty-text {
  margin-top: 8px;
  font-size: 13px;
}
</style>
