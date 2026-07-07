<script setup lang="ts">
import ContractStatusTag from '@/components/ContractStatusTag.vue'
import type { ContractVO, ContractType, ContractStatus } from '@/types/contract'

defineProps<{
  data: ContractVO[]
  loading: boolean
  colVisible: Record<string, boolean>
  typeColor: Record<ContractType, string>
  typeLabel: Record<ContractType, string>
}>()

const emit = defineEmits<{
  (e: 'view', row: ContractVO): void
  (e: 'edit', row: ContractVO): void
  (e: 'delete', row: ContractVO): void
}>()
</script>

<template>
  <div class="lg-card-list">
    <div v-if="loading" class="lg-card-list-loading">
      <a-spin size="large" />
    </div>
    <div v-else-if="!data.length" class="lg-card-list-empty">
      <a-empty />
    </div>
    <div v-for="row in data" :key="row.id" class="lg-card-item">
      <div class="lg-card-item-head">
        <span class="lg-card-code">
          <a class="lg-link" @click="emit('view', row)">{{ row.contractCode }}</a>
        </span>
        <span class="lg-card-head-right">
          <a-tag
            v-if="colVisible.contractType"
            :color="typeColor[row.contractType as ContractType]"
          >
            {{ typeLabel[row.contractType as ContractType] }}
          </a-tag>
          <ContractStatusTag
            v-if="colVisible.contractStatus"
            :status="row.contractStatus as ContractStatus"
          />
        </span>
      </div>
      <div class="lg-card-item-body">
        <div v-if="colVisible.contractName" class="lg-card-field">
          <span class="lg-card-label">合同名称</span>
          <span class="lg-card-value">{{ row.contractName }}</span>
        </div>
        <div v-if="colVisible.partyAName || colVisible.partyBName" class="lg-card-field">
          <span class="lg-card-label">签约双方</span>
          <span class="lg-card-value">
            <template v-if="colVisible.partyAName">{{ row.partyAName }}</template>
            <template v-if="colVisible.partyAName && colVisible.partyBName"> · </template>
            <template v-if="colVisible.partyBName">{{ row.partyBName }}</template>
          </span>
        </div>
        <div class="lg-card-field-row">
          <div v-if="colVisible.contractAmount" class="lg-card-field">
            <span class="lg-card-label">合同金额(含税)</span>
            <span class="lg-card-value lg-card-money">{{
              parseFloat(row.contractAmount).toLocaleString('zh-CN', {
                minimumFractionDigits: 2,
              })
            }}</span>
          </div>
          <div v-if="colVisible.signedDate" class="lg-card-field">
            <span class="lg-card-label">签订日期</span>
            <span class="lg-card-value">{{ row.signedDate }}</span>
          </div>
        </div>
      </div>
      <div class="lg-card-item-foot">
        <a-space :size="4">
          <a-button size="small" type="link" @click="emit('view', row)">查看</a-button>
          <a-button size="small" type="link" @click="emit('edit', row)">编辑</a-button>
          <a-button size="small" type="link" danger @click="emit('delete', row)">删除</a-button>
        </a-space>
      </div>
    </div>
  </div>
</template>
