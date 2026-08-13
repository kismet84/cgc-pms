<script setup lang="ts">
import type { QualityPlanRecord } from '@cgc-pms/frontend-contracts'
import { V2ActionMenu, V2Badge, V2Button, V2PageState } from '@/components'
import { deliveryLabel } from '../labels'
import { qualityStatusTone } from './presentation'

defineProps<{
  plans: QualityPlanRecord[]
  selectedPlanId: string
  canMaintain: boolean
  saving: boolean
  hasError: boolean
}>()

const emit = defineEmits<{
  select: [planId: string]
  activate: [plan: QualityPlanRecord]
  finish: [plan: QualityPlanRecord]
}>()
</script>

<template>
  <div v-if="plans.length" class="quality-page__table-wrap">
    <table class="quality-page__table v2-table--top" aria-label="检查计划">
      <thead>
        <tr>
          <th scope="col">计划编号</th>
          <th scope="col">计划名称</th>
          <th scope="col">状态</th>
          <th scope="col">周期</th>
          <th scope="col" class="v2-table-cell--actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(plan, index) in plans" :key="plan.id">
          <th scope="row">{{ plan.planCode }}</th>
          <td>
            <V2Button
              size="small"
              variant="ghost"
              :aria-pressed="selectedPlanId === plan.id"
              @click="emit('select', plan.id)"
            >
              {{ plan.planName }}
            </V2Button>
          </td>
          <td>
            <V2Badge :tone="qualityStatusTone(plan.status)">{{
              deliveryLabel(plan.status)
            }}</V2Badge>
          </td>
          <td>{{ plan.startDate }} 至 {{ plan.endDate }}</td>
          <td class="v2-table-cell--actions">
            <V2ActionMenu
              :label="`${plan.planCode}更多操作`"
              :placement="index >= plans.length - 3 ? 'top-end' : 'bottom-end'"
            >
              <V2Button
                v-if="canMaintain && plan.status === 'DRAFT'"
                size="small"
                variant="secondary"
                :loading="saving"
                @click="emit('activate', plan)"
                >激活</V2Button
              >
              <V2Button
                v-if="canMaintain && plan.status === 'ACTIVE'"
                size="small"
                variant="ghost"
                :loading="saving"
                @click="emit('finish', plan)"
                >完成</V2Button
              >
              <span v-if="!canMaintain || !['DRAFT', 'ACTIVE'].includes(plan.status)">—</span>
            </V2ActionMenu>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <V2PageState
    v-else-if="!hasError"
    kind="empty"
    title="暂无检查计划"
    description="当前检查类型下没有计划。"
  />
</template>
