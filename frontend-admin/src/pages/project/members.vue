<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'
import { useProjectStore } from '@/stores/project'
import { getUserList, type SysUserBrief } from '@/api/modules/system'
import type { MemberVO, MemberFormParams } from '@/types/project'
import type { SelectOption } from '@/types/ui'

const route = useRoute()
const router = useRouter()
const store = useProjectStore()

const projectId = route.params.projectId as string

// ── Role definitions ──
const ROLE_OPTIONS = [
  { value: 'PM', label: '项目经理' },
  { value: 'CM', label: '商务经理' },
  { value: 'CSTM', label: '成本经理' },
  { value: 'MAT', label: '材料员' },
  { value: 'SUBC', label: '分包管理员' },
  { value: 'FIN', label: '财务' },
  { value: 'OTH', label: '其他' },
]

const ROLE_MAP: Record<string, string> = Object.fromEntries(
  ROLE_OPTIONS.map((r) => [r.value, r.label]),
)

const memberStats = computed(() => ({
  total: store.membersTotal || store.members.length,
  manager: store.members.filter((item) => item.roleCode === 'PM').length,
  business: store.members.filter((item) => ['CM', 'CSTM', 'FIN'].includes(item.roleCode)).length,
  pending: store.members.filter((item) => item.status !== 'ACTIVE').length,
}))

const roleDistribution = computed(() => {
  const counts = store.members.reduce<Record<string, number>>((acc, item) => {
    acc[item.roleCode] = (acc[item.roleCode] || 0) + 1
    return acc
  }, {})
  const rows = Object.entries(counts).map(([role, count]) => ({
    role,
    label: ROLE_MAP[role] ?? role,
    count,
  }))
  return rows.length ? rows : [{ role: 'EMPTY', label: '待添加成员', count: 0 }]
})

// ── User lookup ──
const userList = ref<SysUserBrief[]>([])
const userMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  userList.value.forEach((u) => {
    map[u.id] = u.realName || u.username
  })
  return map
})

function getUserName(userId: string): string {
  return userMap.value[userId] ?? userId
}

// ── Add member modal ──
const addVisible = ref(false)
const addForm = reactive<MemberFormParams>({
  userId: '',
  roleCode: '',
  positionName: '',
  startDate: '',
  status: 'ACTIVE',
})
const addLoading = ref(false)

// ── Load data ──
async function loadUsers() {
  try {
    const res = await getUserList({ pageNum: 1, pageSize: 200 })
    userList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    userList.value = []
  }
}

onMounted(async () => {
  await Promise.all([store.fetchProject(projectId), store.fetchMembers(projectId), loadUsers()])
})

// ── Handlers ──
function openAddModal() {
  addForm.userId = ''
  addForm.roleCode = ''
  addForm.positionName = ''
  addForm.startDate = ''
  addForm.status = 'ACTIVE'
  addVisible.value = true
}

async function handleAdd() {
  if (!addForm.userId || !addForm.roleCode) {
    message.warning('请选择用户和角色')
    return
  }
  addLoading.value = true
  try {
    await store.addMember(projectId, { ...addForm })
    message.success('添加成功')
    addVisible.value = false
    await store.fetchMembers(projectId)
  } catch (e: unknown) {
    console.error(e)
    // error handled by interceptor
  } finally {
    addLoading.value = false
  }
}

async function handleRoleChange(member: MemberVO, newRole: string) {
  if (newRole === member.roleCode) return
  try {
    await store.updateMember(projectId, member.id, {
      userId: member.userId,
      roleCode: newRole,
    })
    message.success('角色更新成功')
    member.roleCode = newRole
  } catch (e: unknown) {
    console.error(e)
    // error handled by interceptor
  }
}

function handleDelete(member: MemberVO) {
  Modal.confirm({
    title: '确认移除',
    content: `确定要从项目中移除成员「${getUserName(member.userId)}」吗？`,
    okText: '确认移除',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      await store.removeMember(projectId, member.id)
      message.success('已移除')
      await store.fetchMembers(projectId)
    },
  })
}

function goBack() {
  router.push('/project')
}

// ── Table columns ──
const columns = [
  { title: '姓名', dataIndex: 'userId', width: 140 },
  { title: '角色', dataIndex: 'roleCode', width: 160 },
  { title: '岗位', dataIndex: 'positionName', width: 140 },
  { title: '开始日期', dataIndex: 'startDate', width: 130 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'ops', width: 80 },
]
</script>

<template>
  <div class="pm-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>项目管理</a-breadcrumb-item>
          <a-breadcrumb-item>项目成员</a-breadcrumb-item>
        </a-breadcrumb>
        <div v-if="store.currentProject" class="pm-title-project">
          {{ store.currentProject.projectName }}
        </div>
      </div>
      <div class="pt-head-actions">
        <a-button @click="goBack">
          <ArrowLeftOutlined />
          返回项目
        </a-button>
        <a-button type="primary" @click="openAddModal">
          <PlusOutlined />
          添加成员
        </a-button>
      </div>
    </div>

    <div class="pt-kpi-strip">
      <div class="pt-kpi">
        <div class="pt-kpi-label">成员总数</div>
        <div class="pt-kpi-value">{{ memberStats.total }} <small>人</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">项目经理</div>
        <div class="pt-kpi-value">{{ memberStats.manager }} <small>人</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">业务人员</div>
        <div class="pt-kpi-value">{{ memberStats.business }} <small>人</small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">待确认</div>
        <div class="pt-kpi-value">{{ memberStats.pending }} <small>人</small></div>
      </div>
    </div>

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">成员清单</div>
        <a-table
          :data-source="store.members"
          :columns="columns"
          :loading="store.membersLoading"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'userId'">
              <div class="pm-user-cell">
                <a-avatar :size="28" class="pm-avatar">
                  {{ getUserName(record.userId).charAt(0) }}
                </a-avatar>
                <span class="pm-user-name">{{ getUserName(record.userId) }}</span>
              </div>
            </template>

            <template v-else-if="column.dataIndex === 'roleCode'">
              <a-select
                :value="record.roleCode"
                size="small"
                style="width: 130px"
                :options="ROLE_OPTIONS"
                @change="(val: string) => handleRoleChange(record, val)"
              />
            </template>

            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="record.status === 'ACTIVE' ? 'success' : 'default'">
                {{ record.status === 'ACTIVE' ? '在岗' : (record.status ?? '-') }}
              </a-tag>
            </template>

            <template v-else-if="column.dataIndex === 'ops'">
              <a-popconfirm
                title="确定移除该成员？"
                ok-text="确认"
                cancel-text="取消"
                @confirm="handleDelete(record)"
              >
                <a class="pt-link pt-danger">移除</a>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
        <a-empty
          v-if="!store.membersLoading && store.members.length === 0"
          description="暂无项目成员，点击上方按钮添加"
          style="padding: 48px 0"
        />
        <div class="pt-pagination">
          <span class="pt-total">共 {{ store.membersTotal }} 人</span>
        </div>
      </main>

      <aside class="pt-analysis-rail">
        <section class="pt-panel">
          <div class="pt-panel-header">角色分布</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in roleDistribution" :key="item.role" class="pt-compact-row">
                <span>{{ item.label }}</span>
                <b>{{ item.count }} 人</b>
              </li>
            </ul>
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">成员职责提示</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li class="pt-compact-row"><span>项目经理</span><b>总负责</b></li>
              <li class="pt-compact-row"><span>商务/成本</span><b>经营控制</b></li>
              <li class="pt-compact-row"><span>财务/物资</span><b>执行支持</b></li>
            </ul>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add Member Modal -->
    <a-modal
      v-model:open="addVisible"
      title="添加项目成员"
      :confirm-loading="addLoading"
      @ok="handleAdd"
      @cancel="addVisible = false"
    >
      <a-form layout="vertical" class="pm-form">
        <a-form-item label="选择用户" required>
          <a-select
            v-model:value="addForm.userId"
            placeholder="请搜索并选择用户"
            show-search
            :filter-option="
              (input: string, option: SelectOption) =>
                (option.label ?? '').toLowerCase().includes(input.toLowerCase())
            "
            :options="
              userList.map((u) => ({
                value: u.id,
                label: `${u.realName || u.username} (${u.username})`,
              }))
            "
            style="width: 100%"
            :dropdown-match-select-width="false"
          />
        </a-form-item>

        <a-form-item label="角色" required>
          <a-select
            v-model:value="addForm.roleCode"
            placeholder="请选择角色"
            :options="ROLE_OPTIONS"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="岗位名称">
          <a-input v-model:value="addForm.positionName" placeholder="如：施工员、安全员" />
        </a-form-item>

        <a-form-item label="开始日期">
          <a-date-picker
            v-model:value="addForm.startDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="选择开始日期"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.pm-page {
  padding: 4px 0;
}

.pm-title-project {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}

/* ── User cell ── */
.pm-user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pm-avatar {
  background: linear-gradient(135deg, #8ac1ff, #006dff);
  flex-shrink: 0;
  font-size: 12px;
}

.pm-user-name {
  font-size: 14px;
  color: #111827;
  font-weight: 500;
}

/* ── Form ── */
.pm-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.pm-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
</style>
