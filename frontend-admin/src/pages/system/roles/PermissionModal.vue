<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import { getMenuTree, updateRoleMenus } from '@/api/modules/system'
import type { SysRoleVO, MenuTreeVO } from '@/types/system'

const props = defineProps<{
  open: boolean
  role: SysRoleVO | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved'): void
}>()

const loading = ref(false)
const saving = ref(false)
const treeData = ref<MenuTreeVO[]>([])
const checkedKeys = ref<(number | string)[]>([])

const modalTitle = computed(() => {
  return props.role ? `编辑权限 - ${props.role.roleName}` : '编辑权限'
})

function flattenTree(nodes: MenuTreeVO[]): MenuTreeVO[] {
  const result: MenuTreeVO[] = []
  function walk(list: MenuTreeVO[]) {
    for (const node of list) {
      result.push(node)
      if (node.children && node.children.length > 0) {
        walk(node.children)
      }
    }
  }
  walk(nodes)
  return result
}

async function fetchTree() {
  loading.value = true
  try {
    treeData.value = await getMenuTree()
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    message.error(msg || '加载菜单树失败')
    treeData.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      fetchTree()
      checkedKeys.value = props.role?.menuIds?.map((id) => id) ?? []
    } else {
      treeData.value = []
      checkedKeys.value = []
    }
  },
  { immediate: true },
)

async function handleSave() {
  if (!props.role) return
  saving.value = true
  try {
    const menuIds = checkedKeys.value.map((k) => k)
    await updateRoleMenus(props.role.id, menuIds)
    message.success('权限保存成功')
    emit('update:open', false)
    emit('saved')
  } catch (e: unknown) {
    console.error(e)
    const msg = axios.isAxiosError(e)
      ? (e.response?.data as { message?: string })?.message || e.message
      : e instanceof Error
        ? e.message
        : ''
    message.error(msg || '权限保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    :width="520"
    :confirm-loading="saving"
    @ok="handleSave"
    @cancel="emit('update:open', false)"
  >
    <a-spin :spinning="loading">
      <a-tree
        v-if="treeData.length > 0"
        v-model:checked-keys="checkedKeys"
        :tree-data="treeData"
        :field-names="{ title: 'menuName', key: 'id', children: 'children' }"
        checkable
        default-expand-all
        block-node
      />
      <div v-else-if="!loading" style="text-align: center; padding: 24px; color: #9ca3af">
        暂无菜单数据
      </div>
    </a-spin>
  </a-modal>
</template>
