<script setup lang="ts">
import { MoreOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { ColumnSettingsButton } from '@/components/list-page'
import { getSourceTypeColor, getSourceTypeLabel } from '@/types/cost'
import type { CostLedgerVO } from '@/types/cost'

defineProps<{
  isMobile: boolean
  loading: boolean
  tableData: CostLedgerVO[]
  total: number
  pageNo: number
  pageSize: number
  visibleGridColumns: Array<Record<string, unknown>>
  columnSettings: Array<Record<string, unknown>>
  colVisible: Record<string, boolean>
  fmtWan: (val: string | undefined) => string
  fmtAmountYuan: (val: string | undefined) => string
  handleSearch: () => void
  handlePageChange: (page: number) => void
  handleShowSizeChange: (current: number, size: number) => void
  showDetail: (record: CostLedgerVO) => void | Promise<void>
  toggleCol: (key: string) => void
}>()
</script>

<template>
  <main class="lg-list-table-panel cost-ledger-table-panel">
    <div class="lg-toolbar cost-toolbar">
      <div class="lg-toolbar-left">
        <div class="cost-ledger-table-heading">
          <span class="cost-ledger-table-title">成本记录</span>
          <span class="cost-ledger-table-count">共 {{ total }} 条</span>
        </div>
      </div>
      <div class="lg-toolbar-right cost-toolbar-right">
        <ColumnSettingsButton
          v-if="!isMobile"
          :columns="columnSettings"
          :visible="colVisible"
          @toggle="toggleCol"
        />
        <a-button aria-label="刷新成本列表" title="刷新成本列表" @click="handleSearch">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </div>

    <div v-if="isMobile" class="cost-ledger-mobile-list">
      <div v-if="loading" class="cost-ledger-mobile-state">
        <a-spin />
      </div>
      <div v-else-if="!tableData.length" class="cost-ledger-mobile-state">
        <a-empty description="暂无成本记录" />
      </div>
      <template v-else>
        <button
          v-for="row in tableData"
          :key="row.id"
          type="button"
          class="cost-ledger-mobile-card"
          @click="showDetail(row)"
        >
          <div class="cost-ledger-mobile-card-head">
            <span class="cost-ledger-mobile-subject">{{ row.costSubjectName || '-' }}</span>
            <span class="cost-ledger-mobile-amount">{{ fmtAmountYuan(row.amount) }}</span>
          </div>
          <div class="cost-ledger-mobile-meta">项目：{{ row.projectName || '-' }}</div>
          <div class="cost-ledger-mobile-meta">合同：{{ row.contractName || '-' }}</div>
          <div class="cost-ledger-mobile-meta">来源：{{ getSourceTypeLabel(row.sourceType) }}</div>
          <div class="cost-ledger-mobile-meta">
            状态：{{
              row.costStatus === 'CONFIRMED'
                ? '已确认'
                : row.costStatus === 'PENDING'
                  ? '待确认'
                  : row.costStatus || '-'
            }}
          </div>
        </button>
      </template>
    </div>
    <div v-else class="lg-table-wrap cost-ledger-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="visibleGridColumns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
      >
        <template #sourceType="{ row }">
          <a-tag :color="getSourceTypeColor(row.sourceType)" size="small">
            {{ getSourceTypeLabel(row.sourceType) }}
          </a-tag>
        </template>
        <template #amount="{ row }">
          <span class="lg-money">{{ fmtWan(row.amount) }}</span>
        </template>
        <template #costStatus="{ row }">
          <a-tag
            :color="
              row.costStatus === 'CONFIRMED'
                ? 'success'
                : row.costStatus === 'PENDING'
                  ? 'processing'
                  : 'default'
            "
            size="small"
          >
            {{
              row.costStatus === 'CONFIRMED'
                ? '已确认'
                : row.costStatus === 'PENDING'
                  ? '待确认'
                  : row.costStatus
            }}
          </a-tag>
        </template>
        <template #ops="{ row }">
          <a-dropdown :trigger="['click']">
            <a-button class="lg-row-action-trigger" size="small" type="text">
              <MoreOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="showDetail(row)">详情</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
      </vxe-grid>
    </div>

    <div class="lg-pagination">
      <span class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handleShowSizeChange"
      />
    </div>
  </main>
</template>

<style scoped>
.cost-ledger-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}

.cost-toolbar {
  flex: 0 0 auto;
  border-bottom: 1px solid var(--border-subtle);
}

.cost-ledger-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.cost-ledger-table-heading,
.cost-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cost-ledger-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.cost-ledger-table-wrap {
  flex: 1 1 auto;
  min-height: 0;
}

.cost-ledger-table-panel > .lg-pagination {
  flex: 0 0 auto;
}

.cost-ledger-table-wrap :deep(.vxe-grid) {
  height: 100%;
}

.cost-ledger-mobile-list {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
  padding: 12px;
  background: var(--surface-subtle);
}

.cost-ledger-mobile-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.cost-ledger-mobile-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 14px;
  color: var(--text);
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-mobile-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cost-ledger-mobile-subject,
.cost-ledger-mobile-amount {
  font-weight: 700;
}

.cost-ledger-mobile-amount {
  color: var(--primary);
}

.cost-ledger-mobile-meta {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.cost-ledger-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}
</style>
