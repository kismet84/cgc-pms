<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { V2Button, V2Card, V2PageState, V2Stack } from '@/components'
import {
  loadPartner,
  loadPartnerTypes,
  type DictOption,
  type PartnerRecord,
} from '@/services/master-data'
import { isApiClientError } from '@/services/request'
import PartnerDetailFacts from './partner/PartnerDetailFacts.vue'

const route = useRoute()
const router = useRouter()
const record = ref<PartnerRecord | null>(null)
const partnerTypes = ref<DictOption[]>([])
const loading = ref(false)
const error = ref('')

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
      <PartnerDetailFacts
        class="v2-detail-dialog__facts"
        :record="record"
        :partner-type-label="typeLabel(record.partnerType)"
        :risk-level-label="record.riskLevel || '—'"
      />
    </V2Card>
  </V2Stack>
</template>
