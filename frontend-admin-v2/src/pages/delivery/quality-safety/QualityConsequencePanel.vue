<script setup lang="ts">
import type { QualityIssueRecord } from '@cgc-pms/frontend-contracts'
import { V2Badge, V2Button, V2PageState } from '@/components'
import { deliveryLabel } from '../labels'
import { qualityStatusTone } from './presentation'

defineProps<{
  issues: QualityIssueRecord[]
  canConsequence: boolean
  hasError: boolean
  partnerLabel: (partnerId?: string) => string
}>()

const emit = defineEmits<{
  openTrace: [issue: QualityIssueRecord]
  createConsequence: [issue: QualityIssueRecord]
}>()
</script>

<template>
  <div v-if="issues.length" class="quality-page__table-wrap">
    <table class="v2-table--top" aria-label="后果追踪">
      <thead>
        <tr>
          <th scope="col">问题编号</th>
          <th scope="col">标题</th>
          <th scope="col">责任合作方</th>
          <th scope="col">状态</th>
          <th scope="col">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="issue in issues" :key="issue.id">
          <th scope="row">
            <V2Button
              size="small"
              variant="ghost"
              class="v2-table__record-link"
              @click="emit('openTrace', issue)"
            >
              {{ issue.issueCode }}
            </V2Button>
          </th>
          <td>{{ issue.title }}</td>
          <td>{{ partnerLabel(issue.responsiblePartnerId) }}</td>
          <td>
            <V2Badge :tone="qualityStatusTone(issue.status)">{{
              deliveryLabel(issue.status)
            }}</V2Badge>
          </td>
          <td>
            <V2Button
              v-if="canConsequence && issue.responsibleKind === 'PARTNER'"
              size="small"
              variant="ghost"
              @click="emit('createConsequence', issue)"
              >登记后果</V2Button
            >
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <V2PageState
    v-else-if="!hasError"
    kind="empty"
    title="暂无后果追踪事项"
    description="当前没有已闭环且归属合作方的问题。"
  />
</template>
