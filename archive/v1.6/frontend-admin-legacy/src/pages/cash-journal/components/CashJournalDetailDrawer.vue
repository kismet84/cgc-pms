<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { CashJournalEntryVO } from '@/types/cashbook'

const props = defineProps<{
  open: boolean
  entry: CashJournalEntryVO | null
  canMaintain: boolean
  isSuperAdmin: boolean
}>()

const emit = defineEmits<{
  close: []
  edit: []
  archive: []
  reverse: [reason: string]
  reopen: [reason: string]
  upload: [file: File]
  deleteFile: [id: string]
  downloadFile: [id: string]
  openSource: [entry: CashJournalEntryVO]
}>()

const reopenVisible = ref(false)
const reopenReason = ref('')
const reverseVisible = ref(false)
const reverseReason = ref('')

const editable = computed(
  () => props.canMaintain && ['DRAFT', 'PENDING_ARCHIVE'].includes(props.entry?.status ?? ''),
)
const attachments = computed(() => props.entry?.attachments ?? [])
const hasAttachment = computed(
  () => attachments.value.length > 0 || Number(props.entry?.attachmentCount ?? 0) > 0,
)

watch(
  () => props.open,
  (open) => {
    if (!open) {
      reopenVisible.value = false
      reverseVisible.value = false
      reopenReason.value = ''
      reverseReason.value = ''
    }
  },
)

function requestArchive() {
  if (!hasAttachment.value) {
    message.warning('请先上传至少一个附件再归档')
    return
  }
  emit('archive')
}

function requestReopen() {
  const reason = reopenReason.value.trim()
  if (!reason) {
    message.warning('请输入撤销归档原因')
    return
  }
  emit('reopen', reason)
  reopenVisible.value = false
  reopenReason.value = ''
}

function requestReverse() {
  const reason = reverseReason.value.trim()
  if (!reason) {
    message.warning('请输入红冲原因')
    return
  }
  emit('reverse', reason)
  reverseVisible.value = false
  reverseReason.value = ''
}

function beforeUpload(file: File) {
  emit('upload', file)
  return false
}

function money(value?: string) {
  return value == null || value === ''
    ? '-'
    : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}
</script>

<template>
  <a-drawer
    :open="open"
    title="资金流水详情"
    width="min(720px, 96vw)"
    class="cash-journal-detail-drawer"
    @close="emit('close')"
  >
    <template v-if="entry">
      <a-descriptions bordered :column="2" size="small">
        <a-descriptions-item label="流水号">{{ entry.entryNo }}</a-descriptions-item>
        <a-descriptions-item label="状态"
          ><a-tag>{{ entry.status }}</a-tag></a-descriptions-item
        >
        <a-descriptions-item label="业务日期">{{ entry.businessDate }}</a-descriptions-item>
        <a-descriptions-item label="金额">{{ money(entry.amount) }}</a-descriptions-item>
        <a-descriptions-item label="资金账户">{{
          entry.accountName || '待选择'
        }}</a-descriptions-item>
        <a-descriptions-item label="方向">{{
          entry.direction === 'IN' ? '收入' : '支出'
        }}</a-descriptions-item>
        <a-descriptions-item label="往来单位">{{
          entry.counterpartyName || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="来源">
          <a-button
            v-if="entry.sourceType === 'PAY_RECORD'"
            type="link"
            size="small"
            @click="emit('openSource', entry)"
            >付款记录 #{{ entry.sourceId }}</a-button
          >
          <span v-else>{{ entry.sourceType }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="摘要" :span="2">{{ entry.summary }}</a-descriptions-item>
        <a-descriptions-item label="归档时间">{{ entry.archivedAt || '-' }}</a-descriptions-item>
        <a-descriptions-item label="红冲关系">
          {{ entry.reverseOfEntryId || entry.reversalEntryId || '-' }}
        </a-descriptions-item>
      </a-descriptions>

      <section class="cash-journal-files">
        <div class="section-title">
          <strong>附件（{{ attachments.length || entry.attachmentCount || 0 }}）</strong>
          <a-upload
            v-if="editable"
            data-testid="file-upload"
            :show-upload-list="false"
            :before-upload="beforeUpload"
          >
            <a-button size="small">上传附件</a-button>
          </a-upload>
        </div>
        <a-list v-if="attachments.length" size="small" :data-source="attachments">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-button type="link" size="small" @click="emit('downloadFile', item.id)">
                {{ item.originalName || item.fileName }}
              </a-button>
              <a-button
                v-if="editable"
                :data-testid="`delete-file-${item.id}`"
                danger
                type="link"
                size="small"
                @click="emit('deleteFile', item.id)"
                >删除</a-button
              >
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-else description="暂无附件" :image-style="{ height: '36px' }" />
      </section>

      <section v-if="entry.changeLogs?.length" class="cash-journal-changes">
        <strong>受控修正记录</strong>
        <article v-for="log in entry.changeLogs" :key="log.id" class="change-log-item">
          <div>{{ log.action }} · {{ log.createdAt }} · {{ log.reason || '无备注' }}</div>
          <details v-if="log.beforeSnapshot || log.afterSnapshot">
            <summary>查看前后快照</summary>
            <pre>修改前：{{ log.beforeSnapshot || '-' }}</pre>
            <pre>修改后：{{ log.afterSnapshot || '-' }}</pre>
          </details>
        </article>
      </section>

      <div class="cash-journal-detail-actions">
        <a-button v-if="editable" @click="emit('edit')">编辑</a-button>
        <a-button
          v-if="editable"
          data-testid="archive-button"
          type="primary"
          @click="requestArchive"
          >确认归档</a-button
        >
        <a-button
          v-if="canMaintain && entry.status === 'ARCHIVED'"
          danger
          @click="reverseVisible = true"
          >红冲</a-button
        >
        <a-button
          v-if="isSuperAdmin && entry.status === 'ARCHIVED'"
          data-testid="reopen-button"
          @click="reopenVisible = true"
          >撤销归档</a-button
        >
      </div>

      <a-modal
        :open="reopenVisible"
        title="撤销归档"
        :footer="null"
        @cancel="reopenVisible = false"
      >
        <template v-if="reopenVisible">
          <textarea
            v-model="reopenReason"
            data-testid="reopen-reason"
            class="reason-input"
            maxlength="500"
            placeholder="必填：说明修正原因"
          />
          <div class="modal-actions">
            <a-button @click="reopenVisible = false">取消</a-button>
            <a-button data-testid="reopen-confirm" type="primary" @click="requestReopen"
              >确认</a-button
            >
          </div>
        </template>
      </a-modal>

      <a-modal
        :open="reverseVisible"
        title="红冲流水"
        :footer="null"
        @cancel="reverseVisible = false"
      >
        <template v-if="reverseVisible">
          <textarea
            v-model="reverseReason"
            class="reason-input"
            maxlength="500"
            placeholder="必填：说明红冲原因"
          />
          <div class="modal-actions">
            <a-button @click="reverseVisible = false">取消</a-button>
            <a-button danger type="primary" @click="requestReverse">确认红冲</a-button>
          </div>
        </template>
      </a-modal>
    </template>
  </a-drawer>
</template>

<style scoped>
.cash-journal-files,
.cash-journal-changes {
  margin-top: 20px;
}
.section-title,
.cash-journal-detail-actions,
.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cash-journal-detail-actions {
  justify-content: flex-end;
  margin-top: 24px;
  flex-wrap: wrap;
}
.change-log-item {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--border-color, #eee);
  border-radius: 6px;
}
.change-log-item pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
.reason-input {
  width: 100%;
  min-height: 96px;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
}
.modal-actions {
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
