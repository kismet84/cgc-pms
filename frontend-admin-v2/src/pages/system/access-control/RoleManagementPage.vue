<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { V2Badge, V2Button, V2Card, V2PageState, V2Stack, showToast } from '@/components'
import { isApiClientError } from '@/services/request'
import { loadRoles, type RoleRecord } from '@/services/system-management'
import { dataScopeLabel, roleTypeLabel } from './model'
import './styles.css'

const loading = ref(false)
const pageSize = 10
const error = ref('')
const roles = ref<RoleRecord[]>([])
const rolePageNo = ref(1)
const visibleRoles = computed(() =>
  roles.value.slice((rolePageNo.value - 1) * pageSize, rolePageNo.value * pageSize),
)

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

async function loadPage(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    roles.value = await loadRoles()
  } catch (value) {
    roles.value = []
    error.value = messageOf(value)
  } finally {
    loading.value = false
  }
}

async function refreshRoles(): Promise<void> {
  await loadPage()
  if (!error.value) showToast('success', '角色管理已刷新')
}

function changeRolePage(next: number): void {
  if (next < 1 || (next - 1) * pageSize >= roles.value.length) return
  rolePageNo.value = next
}

onMounted(() => void loadPage())
</script>

<template>
  <V2Stack class="access-control-page" :gap="4">
    <V2Card title="角色管理" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshRoles">刷新</V2Button>
      </template>
    </V2Card>
    <V2PageState v-if="loading" kind="loading" title="正在读取角色管理" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="角色管理加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>
    <V2Card
      v-else
      title="固定角色清单"
      description="九类业务角色由系统维护；名称、编码、状态和数据范围不可修改。"
    >
      <V2PageState
        v-if="!roles.length"
        kind="empty"
        title="暂无角色"
        description="当前租户没有角色。"
      />
      <div v-else class="access-control-page__table-wrap">
        <table>
          <thead>
            <tr>
              <th>角色编码</th>
              <th>角色名称</th>
              <th>类型</th>
              <th>数据范围</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in visibleRoles" :key="item.id">
              <th scope="row">{{ item.roleCode }}</th>
              <td>{{ item.roleName }}</td>
              <td>{{ roleTypeLabel(item.roleType) }}</td>
              <td>{{ dataScopeLabel(item.dataScope) }}</td>
              <td>
                <V2Badge :tone="item.status === 'ENABLE' ? 'success' : 'neutral'">
                  {{ item.status === 'ENABLE' ? '启用' : '停用' }}
                </V2Badge>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <nav class="access-control-page__pagination v2-pagination" aria-label="角色分页">
          <span>共 {{ roles.length }} 条</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="rolePageNo === 1"
            @click="changeRolePage(rolePageNo - 1)"
          >
            上一页
          </V2Button>
          <span>第 {{ rolePageNo }} 页</span>
          <V2Button
            size="small"
            variant="secondary"
            :disabled="rolePageNo * pageSize >= roles.length"
            @click="changeRolePage(rolePageNo + 1)"
          >
            下一页
          </V2Button>
        </nav>
      </template>
    </V2Card>
  </V2Stack>
</template>
