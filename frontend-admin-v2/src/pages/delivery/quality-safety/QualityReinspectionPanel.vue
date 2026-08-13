<script setup lang="ts">
import type { QualityIssueRecord } from '@cgc-pms/frontend-contracts'
import { V2Badge, V2Button, V2PageState } from '@/components'
import { deliveryLabel } from '../labels'
import { qualityStatusTone } from './presentation'

defineProps<{
  issues: QualityIssueRecord[]
  canReinspect: boolean
  hasError: boolean
}>()

const emit = defineEmits<{
  openTrace: [issue: QualityIssueRecord]
  reinspect: [issue: QualityIssueRecord]
}>()
</script>

<template>
  <div v-if="issues.length" class="quality-page__table-wrap">
    <table class="quality-page__table v2-table--top" aria-label="复检闭环">
      <thead>
        <tr>
          <th scope="col">问题编号</th>
          <th scope="col">标题</th>
          <th scope="col">严重度</th>
          <th scope="col">整改期限</th>
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
          <td>
            <V2Badge :tone="qualityStatusTone(issue.severity)">{{
              deliveryLabel(issue.severity)
            }}</V2Badge>
          </td>
          <td>{{ issue.dueDate }}</td>
          <td>
            <V2Button v-if="canReinspect" size="small" @click="emit('reinspect', issue)"
              >复检</V2Button
            >
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <V2PageState
    v-else-if="!hasError"
    kind="empty"
    title="暂无待复检问题"
    description="当前没有已提交整改、等待复检的问题。"
  />
</template>
