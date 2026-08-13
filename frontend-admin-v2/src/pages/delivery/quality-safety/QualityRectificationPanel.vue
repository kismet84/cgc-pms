<script setup lang="ts">
import type { QualityIssueRecord } from '@cgc-pms/frontend-contracts'
import { V2ActionMenu, V2Badge, V2Button, V2PageState } from '@/components'
import { deliveryLabel } from '../labels'
import { qualityStatusTone } from './presentation'

defineProps<{
  issues: QualityIssueRecord[]
  canInspect: boolean
  canRectify: boolean
  hasError: boolean
}>()

const emit = defineEmits<{
  openTrace: [issue: QualityIssueRecord]
  uploadEvidence: [issue: QualityIssueRecord]
  rectify: [issue: QualityIssueRecord]
}>()
</script>

<template>
  <div v-if="issues.length" class="quality-page__table-wrap">
    <table class="quality-page__table v2-table--top" aria-label="问题整改">
      <thead>
        <tr>
          <th scope="col">问题编号</th>
          <th scope="col">标题</th>
          <th scope="col">严重度</th>
          <th scope="col">状态</th>
          <th scope="col">整改期限</th>
          <th scope="col" class="v2-table-cell--actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(issue, index) in issues" :key="issue.id">
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
          <td>
            <V2Badge :tone="qualityStatusTone(issue.status)">{{
              deliveryLabel(issue.status)
            }}</V2Badge>
          </td>
          <td>{{ issue.dueDate }}</td>
          <td class="v2-table-cell--actions">
            <V2ActionMenu
              :label="`${issue.issueCode}更多操作`"
              :placement="index >= issues.length - 3 ? 'top-end' : 'bottom-end'"
            >
              <V2Button
                v-if="canInspect && issue.status === 'OPEN'"
                size="small"
                variant="ghost"
                @click="emit('uploadEvidence', issue)"
                >上传问题证据</V2Button
              >
              <V2Button
                v-if="canRectify && issue.status === 'RECTIFYING'"
                size="small"
                @click="emit('rectify', issue)"
                >提交整改</V2Button
              >
            </V2ActionMenu>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <V2PageState
    v-else-if="!hasError"
    kind="empty"
    title="暂无待处理问题"
    description="当前没有需要登记证据或整改的问题。"
  />
</template>
