<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { V2Alert, V2Button, V2Card, V2Input, V2PageState, V2Stack, showToast } from '@/components'
import { isApiClientError } from '@/services/request'
import {
  assignRoleMenus,
  loadMenus,
  loadRole,
  loadRoles,
  type MenuRecord,
  type RoleRecord,
} from '@/services/system-management'
import { useSessionStore } from '@/stores/session'
import { filterRoles, menuTypeLabel, setsEqual } from './model'
import {
  buildPermissionNodeMap,
  buildPermissionTree,
  collectSubtreeMenuIds,
  flattenPermissionTree,
} from './permission-tree'
import './styles.css'

const session = useSessionStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const roles = ref<RoleRecord[]>([])
const menus = ref<MenuRecord[]>([])
const roleSearch = ref('')
const selectedRoleId = ref('')
const selectedMenuIds = ref<Set<string>>(new Set())
const savedMenuIds = ref<Set<string>>(new Set())
const expandedMenuIds = ref<Set<string>>(new Set())
const roleLoading = ref(false)
let roleLoadVersion = 0

const canRoleAssign = computed(() => session.hasAdminOrPermission('system:role:assign'))
const filteredRoles = computed(() => filterRoles(roles.value, roleSearch.value))
const selectedRole = computed(
  () => roles.value.find((role) => role.id === selectedRoleId.value) ?? null,
)
const permissionTree = computed(() => buildPermissionTree(menus.value))
const permissionNodeMap = computed(() => buildPermissionNodeMap(permissionTree.value))
const permissionRows = computed(() =>
  flattenPermissionTree(permissionTree.value, expandedMenuIds.value),
)
const permissionsDirty = computed(() => !setsEqual(selectedMenuIds.value, savedMenuIds.value))

function messageOf(value: unknown): string {
  return isApiClientError(value) || value instanceof Error ? value.message : '请求失败'
}

function applyRoleMenus(menuIds: string[]): void {
  selectedMenuIds.value = new Set(menuIds)
  savedMenuIds.value = new Set(menuIds)
}

async function selectRole(roleId: string, throwOnError = false): Promise<void> {
  if (
    !roleId ||
    (!throwOnError && roleId === selectedRoleId.value && savedMenuIds.value.size > 0)
  ) {
    return
  }
  const requestVersion = ++roleLoadVersion
  roleLoading.value = true
  try {
    const detail = await loadRole(roleId)
    if (requestVersion !== roleLoadVersion) return
    selectedRoleId.value = detail.id
    applyRoleMenus(detail.menuIds)
    const index = roles.value.findIndex((role) => role.id === detail.id)
    if (index >= 0) roles.value.splice(index, 1, detail)
  } catch (value) {
    if (throwOnError) throw value
    showToast('error', '角色权限加载失败', messageOf(value))
  } finally {
    if (requestVersion === roleLoadVersion) roleLoading.value = false
  }
}

async function refreshPermissions(): Promise<void> {
  const [currentRoles, currentMenus] = await Promise.all([loadRoles(), loadMenus()])
  roles.value = currentRoles
  menus.value = currentMenus
  expandedMenuIds.value = new Set(
    [...permissionNodeMap.value.values()]
      .filter((node) => node.children.length > 0)
      .map((node) => node.id),
  )
  const nextRoleId =
    currentRoles.find((role) => role.id === selectedRoleId.value)?.id ?? currentRoles[0]?.id ?? ''
  if (nextRoleId) await selectRole(nextRoleId, true)
  else applyRoleMenus([])
}

function clearRows(): void {
  roles.value = []
  menus.value = []
  selectedRoleId.value = ''
  applyRoleMenus([])
}

async function loadPage(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    await refreshPermissions()
  } catch (value) {
    error.value = messageOf(value)
    clearRows()
  } finally {
    loading.value = false
  }
}

async function refreshPermissionList(): Promise<void> {
  await loadPage()
  if (!error.value) showToast('success', '权限清单已刷新')
}

function subtreeMenuIds(nodeId: string): string[] {
  return collectSubtreeMenuIds(permissionNodeMap.value, nodeId)
}

function menuChecked(nodeId: string): boolean {
  const ids = subtreeMenuIds(nodeId)
  return ids.length > 0 && ids.every((id) => selectedMenuIds.value.has(id))
}

function menuIndeterminate(nodeId: string): boolean {
  const ids = subtreeMenuIds(nodeId)
  const selected = ids.filter((id) => selectedMenuIds.value.has(id)).length
  return selected > 0 && selected < ids.length
}

function toggleMenu(nodeId: string, checked: boolean): void {
  const next = new Set(selectedMenuIds.value)
  subtreeMenuIds(nodeId).forEach((id) => (checked ? next.add(id) : next.delete(id)))
  selectedMenuIds.value = next
}

function toggleExpanded(menuId: string): void {
  const next = new Set(expandedMenuIds.value)
  if (next.has(menuId)) next.delete(menuId)
  else next.add(menuId)
  expandedMenuIds.value = next
}

function expandAllMenus(): void {
  expandedMenuIds.value = new Set(
    [...permissionNodeMap.value.values()]
      .filter((node) => node.children.length > 0)
      .map((node) => node.id),
  )
}

function collapseAllMenus(): void {
  expandedMenuIds.value = new Set()
}

function selectAllMenus(): void {
  selectedMenuIds.value = new Set(menus.value.map((menu) => menu.id))
}

function clearAllMenus(): void {
  selectedMenuIds.value = new Set()
}

async function savePermissions(): Promise<void> {
  if (!selectedRole.value || !canRoleAssign.value || !permissionsDirty.value) return
  saving.value = true
  try {
    await assignRoleMenus(selectedRole.value.id, [...selectedMenuIds.value])
    const detail = await loadRole(selectedRole.value.id)
    applyRoleMenus(detail.menuIds)
    const index = roles.value.findIndex((role) => role.id === detail.id)
    if (index >= 0) roles.value.splice(index, 1, detail)
    showToast('success', '角色权限已保存', '已按服务端最新授权结果刷新。')
  } catch (value) {
    showToast('error', '角色权限保存失败', messageOf(value))
  } finally {
    saving.value = false
  }
}

onMounted(() => void loadPage())
</script>

<template>
  <V2Stack class="access-control-page" :gap="4">
    <V2Card title="权限清单" :heading-level="1">
      <template #actions>
        <V2Button size="small" variant="secondary" @click="refreshPermissionList">刷新</V2Button>
      </template>
    </V2Card>
    <V2PageState v-if="loading" kind="loading" title="正在读取权限清单" description="请稍候。" />
    <V2PageState v-else-if="error" kind="error" title="权限清单加载失败" :description="error">
      <template #actions><V2Button @click="loadPage">重试</V2Button></template>
    </V2PageState>

    <div v-else class="permission-workspace">
      <V2PageState
        v-if="!roles.length || !menus.length"
        kind="empty"
        title="暂无可配置权限"
        description="当前租户缺少角色或菜单权限数据。"
      />
      <template v-else>
        <V2Card title="角色" :heading-level="2" class="permission-workspace__roles">
          <V2Input
            v-model="roleSearch"
            label="搜索角色名称或编码"
            hide-label
            placeholder="搜索角色名称或编码"
          />
          <div class="permission-role-list" role="group" aria-label="角色">
            <V2Button
              v-for="role in filteredRoles"
              :key="role.id"
              class="permission-role-list__item"
              size="medium"
              :variant="role.id === selectedRoleId ? 'secondary' : 'ghost'"
              :aria-pressed="role.id === selectedRoleId"
              :disabled="roleLoading"
              @click="selectRole(role.id)"
            >
              <strong>{{ role.roleName }}</strong>
              <span class="permission-role-list__count">{{ role.menuIds.length }} 项</span>
            </V2Button>
            <p v-if="!filteredRoles.length" class="permission-role-list__empty">未找到匹配角色</p>
          </div>
        </V2Card>

        <V2Card
          :title="selectedRole?.roleName || '请选择角色'"
          :heading-level="2"
          class="permission-workspace__matrix"
        >
          <template #actions>
            <span class="permission-workspace__count">已选 {{ selectedMenuIds.size }} 项</span>
            <V2Button size="small" variant="secondary" @click="expandAllMenus">全部展开</V2Button>
            <V2Button size="small" variant="secondary" @click="collapseAllMenus">全部收起</V2Button>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="!canRoleAssign || roleLoading"
              @click="selectAllMenus"
            >
              全选
            </V2Button>
            <V2Button
              size="small"
              variant="secondary"
              :disabled="!canRoleAssign || roleLoading"
              @click="clearAllMenus"
            >
              清空
            </V2Button>
            <V2Button
              v-if="canRoleAssign"
              size="small"
              :loading="saving"
              :disabled="roleLoading || !selectedRole || !permissionsDirty"
              @click="savePermissions"
            >
              保存权限
            </V2Button>
          </template>

          <V2Alert
            v-if="selectedRole?.roleCode === 'COMPANY_FINANCE'"
            tone="warning"
            title="财务权限提示"
          >
            移除菜单权限不会撤销超级管理员旁路能力。
          </V2Alert>

          <V2PageState
            v-if="roleLoading"
            kind="loading"
            title="正在读取角色权限"
            description="请稍候。"
          />
          <div
            v-else
            class="access-control-page__table-wrap permission-tree-region"
            role="region"
            aria-label="目录、菜单与按钮权限"
            tabindex="0"
          >
            <table class="v2-table--compact" data-table-identity="contextual">
              <thead>
                <tr>
                  <th>权限名称</th>
                  <th>类型</th>
                  <th>权限标识</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="{ node, depth, hasChildren } in permissionRows" :key="node.id">
                  <th scope="row" :style="{ paddingInlineStart: `${depth * 24 + 12}px` }">
                    <span class="permission-tree-table__name">
                      <V2Button
                        v-if="hasChildren"
                        size="small"
                        variant="ghost"
                        :aria-label="
                          expandedMenuIds.has(node.id) ? `收起${node.label}` : `展开${node.label}`
                        "
                        :aria-expanded="expandedMenuIds.has(node.id)"
                        @click="toggleExpanded(node.id)"
                      >
                        {{ expandedMenuIds.has(node.id) ? '⌄' : '›' }}
                      </V2Button>
                      <span v-else class="permission-tree-table__spacer" aria-hidden="true" />
                      <input
                        type="checkbox"
                        :aria-label="`${node.label}权限`"
                        :checked="menuChecked(node.id)"
                        :indeterminate="menuIndeterminate(node.id)"
                        :disabled="!canRoleAssign || !subtreeMenuIds(node.id).length"
                        @change="toggleMenu(node.id, ($event.target as HTMLInputElement).checked)"
                      />
                      <span>{{ node.label }}</span>
                    </span>
                  </th>
                  <td>
                    <span
                      class="permission-tree-table__type"
                      :class="`permission-tree-table__type--${node.menuType.toLowerCase()}`"
                    >
                      {{ menuTypeLabel(node.menuType) }}
                    </span>
                  </td>
                  <td>
                    <code :title="node.perms || node.path || undefined">{{
                      node.perms || node.path || '—'
                    }}</code>
                  </td>
                  <td>
                    <span
                      :class="
                        node.status !== 'ENABLE'
                          ? 'permission-tree-table__status--disabled'
                          : menuIndeterminate(node.id)
                            ? 'permission-tree-table__status--partial'
                            : menuChecked(node.id)
                              ? 'permission-tree-table__status--active'
                              : 'permission-tree-table__status--inactive'
                      "
                    >
                      {{
                        node.status === 'MISSING'
                          ? '缺少菜单记录'
                          : node.status === 'REVIEW'
                            ? '需治理'
                            : node.status !== 'ENABLE'
                              ? '停用项'
                              : menuIndeterminate(node.id)
                                ? '部分分配'
                                : menuChecked(node.id)
                                  ? '已分配'
                                  : '未分配'
                      }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </V2Card>
      </template>
    </div>
  </V2Stack>
</template>
