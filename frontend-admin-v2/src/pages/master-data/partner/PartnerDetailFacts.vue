<script setup lang="ts">
import { V2Badge } from '@/components'
import type { PartnerRecord } from '@/services/master-data'

defineProps<{
  record: PartnerRecord
  partnerTypeLabel: string
  riskLevelLabel: string
}>()

function displayText(value: unknown): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}
</script>

<template>
  <dl class="partner-detail__facts">
    <div>
      <dt>合作方名称</dt>
      <dd>{{ record.partnerName }}</dd>
    </div>
    <div>
      <dt>合作方编号</dt>
      <dd>{{ record.partnerCode }}</dd>
    </div>
    <div>
      <dt>合作方类型</dt>
      <dd>{{ partnerTypeLabel }}</dd>
    </div>
    <div>
      <dt>统一社会信用代码</dt>
      <dd>{{ displayText(record.creditCode) }}</dd>
    </div>
    <div>
      <dt>法定代表人</dt>
      <dd>{{ displayText(record.legalPerson) }}</dd>
    </div>
    <div>
      <dt>联系人</dt>
      <dd>{{ displayText(record.contactName) }}</dd>
    </div>
    <div>
      <dt>联系电话</dt>
      <dd>{{ displayText(record.contactPhone) }}</dd>
    </div>
    <div>
      <dt>开户银行</dt>
      <dd>{{ displayText(record.bankName) }}</dd>
    </div>
    <div>
      <dt>银行账号</dt>
      <dd>{{ displayText(record.bankAccount) }}</dd>
    </div>
    <div>
      <dt>资质等级</dt>
      <dd>{{ displayText(record.qualificationLevel) }}</dd>
    </div>
    <div>
      <dt>默认提前期</dt>
      <dd>{{ displayText(record.defaultLeadDays) }}</dd>
    </div>
    <div>
      <dt>风险等级</dt>
      <dd>{{ riskLevelLabel }}</dd>
    </div>
    <div>
      <dt>状态</dt>
      <dd>
        <V2Badge :tone="record.status === 'ENABLE' ? 'success' : 'neutral'">
          {{ record.status === 'ENABLE' ? '启用' : '停用' }}
        </V2Badge>
      </dd>
    </div>
    <div>
      <dt>黑名单</dt>
      <dd>{{ record.blacklistFlag ? '是' : '否' }}</dd>
    </div>
  </dl>
</template>
