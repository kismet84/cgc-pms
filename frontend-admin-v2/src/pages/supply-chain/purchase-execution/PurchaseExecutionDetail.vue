<script setup lang="ts">
import { V2Badge, V2Button, V2Dialog, V2PageState } from '@/components'
import { formatAmount } from '@/shared/display'
import PurchaseExecutionAttachments from './PurchaseExecutionAttachments.vue'
import { statusLabel, type DetailTable, type PurchaseExecutionMode } from './model'

defineProps<{
  open: boolean
  mode: PurchaseExecutionMode
  title: string
  businessId: string
  businessCode: string
  projectName?: string | null
  sourceLabel: string
  approvalStatus?: string | null
  businessStatus: string
  detailTable: DetailTable
  detailLoading: boolean
  canEdit: boolean
  canManageAttachments: boolean
  canSubmit: boolean
  canDelete?: boolean
  canReturn?: boolean
  amount?: string | null
  sourceRequest?: { id: string; code: string } | null
}>()

defineEmits<{
  close: []
  edit: []
  submit: []
  delete: []
  return: []
  openSourceRequest: []
}>()
</script>

<template>
  <V2Dialog
    :open="open"
    :title="`${title}详情`"
    :description="businessCode"
    panel-class="v2-detail-dialog"
    :close-on-backdrop="false"
    @close="$emit('close')"
  >
    <V2PageState v-if="detailLoading" kind="loading" title="正在读取详情" description="请稍候。" />
    <template v-else>
      <dl class="purchase-execution-page__facts v2-detail-dialog__facts">
        <div>
          <dt>编号</dt>
          <dd>{{ businessCode }}</dd>
        </div>
        <div>
          <dt>项目</dt>
          <dd>{{ projectName || '项目信息缺失' }}</dd>
        </div>
        <div>
          <dt>来源</dt>
          <dd>{{ sourceLabel }}</dd>
        </div>
        <div v-if="sourceRequest">
          <dt>来源采购申请</dt>
          <dd>
            <V2Button
              type="button"
              variant="ghost"
              size="small"
              class="v2-table__record-link"
              @click="$emit('openSourceRequest')"
              >{{ sourceRequest.code }} · 查看采购申请</V2Button
            >
          </dd>
        </div>
        <div>
          <dt>审批状态</dt>
          <dd>{{ statusLabel(approvalStatus) }}</dd>
        </div>
        <div>
          <dt>业务状态</dt>
          <dd>{{ businessStatus }}</dd>
        </div>
        <div v-if="mode !== 'request'">
          <dt>金额</dt>
          <dd>{{ formatAmount(amount) }}</dd>
        </div>
      </dl>

      <section class="v2-detail-dialog__section" aria-labelledby="purchase-detail-title">
        <div class="v2-detail-dialog__section-heading">
          <h3 id="purchase-detail-title">单据明细</h3>
          <V2Badge tone="info">{{ detailTable.rows.length }} 条</V2Badge>
        </div>
        <V2PageState
          v-if="!detailTable.rows.length"
          title="暂无明细"
          description="当前单据暂无明细。"
          :heading-level="3"
        />
        <div
          v-else
          class="v2-detail-dialog__table"
          role="region"
          :aria-label="`${title}明细表格`"
          tabindex="0"
        >
          <table>
            <thead>
              <tr>
                <th v-for="column in detailTable.columns" :key="column" scope="col">
                  {{ column }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in detailTable.rows" :key="row.key">
                <th scope="row">{{ row.cells[0] }}</th>
                <td v-for="(cell, index) in row.cells.slice(1)" :key="index">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <PurchaseExecutionAttachments
        :mode="mode"
        :business-id="businessId"
        :business-code="businessCode"
        :approval-status="approvalStatus"
        :can-edit="canManageAttachments"
      />
    </template>

    <template #footer>
      <V2Button type="button" variant="secondary" @click="$emit('close')">关闭</V2Button>
      <V2Button v-if="canEdit" type="button" variant="secondary" @click="$emit('edit')">
        {{ mode === 'order' ? '编辑商业条件' : '编辑验收明细' }}
      </V2Button>
      <V2Button v-if="canReturn" type="button" variant="secondary" @click="$emit('return')">
        登记供应商退货
      </V2Button>
      <V2Button v-if="canSubmit" type="button" @click="$emit('submit')">提交审批</V2Button>
      <V2Button v-if="canDelete" type="button" variant="danger" @click="$emit('delete')">
        删除草稿
      </V2Button>
    </template>
  </V2Dialog>
</template>
