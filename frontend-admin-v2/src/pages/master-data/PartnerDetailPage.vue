<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Badge, V2Button, V2Card, V2PageState, V2Stack } from '@/components'
import {
  loadPartner,
  loadPartnerTypes,
  type DictOption,
  type PartnerRecord,
} from '@/services/master-data'
import { isApiClientError } from '@/services/request'

const route = useRoute()
const router = useRouter()
const record = ref<PartnerRecord | null>(null)
const partnerTypes = ref<DictOption[]>([])
const loading = ref(false)
const error = ref('')

function text(value: unknown): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function typeLabel(value: string): string {
  return partnerTypes.value.find((item) => item.dictValue === value)?.dictLabel ?? value
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const id = String(route.params.id ?? '')
    const [detail, types] = await Promise.all([loadPartner(id), loadPartnerTypes()])
    record.value = detail
    partnerTypes.value = types
  } catch (value) {
    record.value = null
    error.value = isApiClientError(value) ? value.message : '合作方详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <V2Stack class="partner-detail-page" :gap="4">
    <V2Card title="合作方详情" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="router.push('/partner')"
          >返回列表</V2Button
        >
      </template>
    </V2Card>

    <V2PageState v-if="loading" kind="loading" title="正在读取合作方详情" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="合作方详情加载失败" :description="error">
      <template #actions><V2Button @click="load">重试</V2Button></template>
    </V2PageState>
    <V2Card v-else-if="record" :title="record.partnerName">
      <dl class="v2-detail-dialog__facts">
        <div>
          <dt>合作方编号</dt>
          <dd>{{ record.partnerCode }}</dd>
        </div>
        <div>
          <dt>合作方类型</dt>
          <dd>{{ typeLabel(record.partnerType) }}</dd>
        </div>
        <div>
          <dt>统一社会信用代码</dt>
          <dd>{{ text(record.creditCode) }}</dd>
        </div>
        <div>
          <dt>法定代表人</dt>
          <dd>{{ text(record.legalPerson) }}</dd>
        </div>
        <div>
          <dt>联系人</dt>
          <dd>{{ text(record.contactName) }}</dd>
        </div>
        <div>
          <dt>联系电话</dt>
          <dd>{{ text(record.contactPhone) }}</dd>
        </div>
        <div>
          <dt>开户银行</dt>
          <dd>{{ text(record.bankName) }}</dd>
        </div>
        <div>
          <dt>银行账号</dt>
          <dd>{{ text(record.bankAccount) }}</dd>
        </div>
        <div>
          <dt>资质等级</dt>
          <dd>{{ text(record.qualificationLevel) }}</dd>
        </div>
        <div>
          <dt>默认提前期</dt>
          <dd>{{ text(record.defaultLeadDays) }}</dd>
        </div>
        <div>
          <dt>风险等级</dt>
          <dd>{{ text(record.riskLevel) }}</dd>
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
    </V2Card>
  </V2Stack>
</template>
