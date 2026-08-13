<script setup lang="ts">
import type { QualityInspectionRecord } from '@cgc-pms/frontend-contracts'
import { V2ActionMenu, V2Badge, V2Button, V2PageState } from '@/components'
import { deliveryLabel } from '../labels'
import { qualityStatusTone } from './presentation'

defineProps<{
  inspections: QualityInspectionRecord[]
  canMaintain: boolean
  saving: boolean
  hasError: boolean
}>()

const emit = defineEmits<{
  uploadEvidence: [inspection: QualityInspectionRecord]
  createIssue: [inspection: QualityInspectionRecord]
  submit: [inspection: QualityInspectionRecord]
}>()
</script>

<template>
  <div v-if="inspections.length" class="quality-page__table-wrap">
    <table class="quality-page__table v2-table--top" aria-label="检查记录">
      <thead>
        <tr>
          <th scope="col">检查编号</th>
          <th scope="col">位置 / 摘要</th>
          <th scope="col">状态</th>
          <th scope="col">日期</th>
          <th scope="col" class="v2-table-cell--actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(inspection, index) in inspections" :key="inspection.id">
          <th scope="row">{{ inspection.inspectionCode }}</th>
          <td>{{ inspection.location }} · {{ inspection.summary }}</td>
          <td>
            <V2Badge :tone="qualityStatusTone(inspection.status)">{{
              deliveryLabel(inspection.status)
            }}</V2Badge>
          </td>
          <td>{{ inspection.inspectionDate }}</td>
          <td class="v2-table-cell--actions">
            <V2ActionMenu
              :label="`${inspection.inspectionCode}更多操作`"
              :placement="index >= inspections.length - 3 ? 'top-end' : 'bottom-end'"
            >
              <V2Button
                v-if="canMaintain && inspection.status === 'DRAFT'"
                size="small"
                variant="ghost"
                @click="emit('uploadEvidence', inspection)"
                >上传检查证据</V2Button
              >
              <V2Button
                v-if="canMaintain && inspection.status === 'DRAFT'"
                size="small"
                variant="secondary"
                @click="emit('createIssue', inspection)"
                >登记问题</V2Button
              >
              <V2Button
                v-if="canMaintain && inspection.status === 'DRAFT'"
                size="small"
                variant="ghost"
                :loading="saving"
                @click="emit('submit', inspection)"
                >提交检查</V2Button
              >
              <span v-if="!canMaintain || inspection.status !== 'DRAFT'">—</span>
            </V2ActionMenu>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <V2PageState
    v-else-if="!hasError"
    kind="empty"
    title="暂无检查记录"
    description="当前计划下没有检查记录。"
  />
</template>
