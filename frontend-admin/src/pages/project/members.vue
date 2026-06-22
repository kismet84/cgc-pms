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

// 鈹€鈹€ Role definitions 鈹€鈹€
const ROLE_OPTIONS = [
  { value: 'PM', label: '椤圭洰缁忕悊' },
  { value: 'CM', label: '鍟嗗姟缁忕悊' },
  { value: 'CSTM', label: '鎴愭湰缁忕悊' },
  { value: 'MAT', label: '鏉愭枡鍛? },
  { value: 'SUBC', label: '鍒嗗寘绠＄悊鍛? },
  { value: 'FIN', label: '璐㈠姟' },
  { value: 'OTH', label: '鍏朵粬' },
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
  return rows.length ? rows : [{ role: 'EMPTY', label: '寰呮坊鍔犳垚鍛?, count: 0 }]
})

// 鈹€鈹€ User lookup 鈹€鈹€
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

// 鈹€鈹€ Add member modal 鈹€鈹€
const addVisible = ref(false)
const addForm = reactive<MemberFormParams>({
  userId: '',
  roleCode: '',
  positionName: '',
  startDate: '',
  status: 'ACTIVE',
})
const addLoading = ref(false)

// 鈹€鈹€ Load data 鈹€鈹€
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

// 鈹€鈹€ Handlers 鈹€鈹€
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
    message.warning('璇烽€夋嫨鐢ㄦ埛鍜岃鑹?)
    return
  }
  addLoading.value = true
  try {
    await store.addMember(projectId, { ...addForm })
    message.success('娣诲姞鎴愬姛')
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
    message.success('瑙掕壊鏇存柊鎴愬姛')
    member.roleCode = newRole
  } catch (e: unknown) {
    console.error(e)
    // error handled by interceptor
  }
}

function handleDelete(member: MemberVO) {
  Modal.confirm({
    title: '纭绉婚櫎',
    content: `纭畾瑕佷粠椤圭洰涓Щ闄ゆ垚鍛樸€?{getUserName(member.userId)}銆嶅悧锛焋,
    okText: '纭绉婚櫎',
    cancelText: '鍙栨秷',
    okType: 'danger',
    onOk: async () => {
      await store.removeMember(projectId, member.id)
      message.success('宸茬Щ闄?)
      await store.fetchMembers(projectId)
    },
  })
}

function goBack() {
  router.push('/project')
}

// 鈹€鈹€ Table columns 鈹€鈹€
const columns = [
  { title: '濮撳悕', dataIndex: 'userId', width: 140 },
  { title: '瑙掕壊', dataIndex: 'roleCode', width: 160 },
  { title: '宀椾綅', dataIndex: 'positionName', width: 140 },
  { title: '寮€濮嬫棩鏈?, dataIndex: 'startDate', width: 130 },
  { title: '鐘舵€?, dataIndex: 'status', width: 90 },
  { title: '鎿嶄綔', dataIndex: 'ops', width: 80 },
]
</script>

<template>
  <div class="pm-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>椤圭洰绠＄悊</a-breadcrumb-item>
          <a-breadcrumb-item>椤圭洰鎴愬憳</a-breadcrumb-item>
        </a-breadcrumb>
        <div v-if="store.currentProject" class="pm-title-project">
          {{ store.currentProject.projectName }}
        </div>
      </div>
      <div class="pt-head-actions">
        <a-button @click="goBack">
          <ArrowLeftOutlined />
          杩斿洖椤圭洰
        </a-button>
        <a-button type="primary" @click="openAddModal">
          <PlusOutlined />
          娣诲姞鎴愬憳
        </a-button>
      </div>
    </div>

    <div class="pt-kpi-strip">
      <div class="pt-kpi">
        <div class="pt-kpi-label">鎴愬憳鎬绘暟</div>
        <div class="pt-kpi-value">{{ memberStats.total }} <small>浜?/small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">椤圭洰缁忕悊</div>
        <div class="pt-kpi-value">{{ memberStats.manager }} <small>浜?/small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">涓氬姟浜哄憳</div>
        <div class="pt-kpi-value">{{ memberStats.business }} <small>浜?/small></div>
      </div>
      <div class="pt-kpi">
        <div class="pt-kpi-label">寰呯‘璁?/div>
        <div class="pt-kpi-value">{{ memberStats.pending }} <small>浜?/small></div>
      </div>
    </div>

    <div class="pt-ledger-layout">
      <main class="pt-panel pt-table-panel">
        <div class="pt-panel-header">鎴愬憳娓呭崟</div>
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
                {{ record.status === 'ACTIVE' ? '鍦ㄥ矖' : (record.status ?? '-') }}
              </a-tag>
            </template>

            <template v-else-if="column.dataIndex === 'ops'">
              <a-popconfirm
                title="纭畾绉婚櫎璇ユ垚鍛橈紵"
                ok-text="纭"
                cancel-text="鍙栨秷"
                @confirm="handleDelete(record)"
              >
                <a class="pt-link pt-danger">绉婚櫎</a>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
        <a-empty
          v-if="!store.membersLoading && store.members.length === 0"
          description="鏆傛棤椤圭洰鎴愬憳锛岀偣鍑讳笂鏂规寜閽坊鍔?
          style="padding: 48px 0"
        />
        <div class="pt-pagination">
          <span class="pt-total">鍏?{{ store.membersTotal }} 浜?/span>
        </div>
      </main>

      <aside class="pt-analysis-rail">
        <section class="pt-panel">
          <div class="pt-panel-header">瑙掕壊鍒嗗竷</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li v-for="item in roleDistribution" :key="item.role" class="pt-compact-row">
                <span>{{ item.label }}</span>
                <b>{{ item.count }} 浜?/b>
              </li>
            </ul>
          </div>
        </section>
        <section class="pt-panel">
          <div class="pt-panel-header">鎴愬憳鑱岃矗鎻愮ず</div>
          <div class="pt-panel-body">
            <ul class="pt-compact-list">
              <li class="pt-compact-row"><span>椤圭洰缁忕悊</span><b>鎬昏礋璐?/b></li>
              <li class="pt-compact-row"><span>鍟嗗姟/鎴愭湰</span><b>缁忚惀鎺у埗</b></li>
              <li class="pt-compact-row"><span>璐㈠姟/鐗╄祫</span><b>鎵ц鏀寔</b></li>
            </ul>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add Member Modal -->
    <a-modal
      v-model:open="addVisible"
      title="娣诲姞椤圭洰鎴愬憳"
      :confirm-loading="addLoading"
      @ok="handleAdd"
      @cancel="addVisible = false"
    >
      <a-form layout="vertical" class="pm-form">
        <a-form-item label="閫夋嫨鐢ㄦ埛" required>
          <a-select
            v-model:value="addForm.userId"
            placeholder="璇锋悳绱㈠苟閫夋嫨鐢ㄦ埛"
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

        <a-form-item label="瑙掕壊" required>
          <a-select
            v-model:value="addForm.roleCode"
            placeholder="璇烽€夋嫨瑙掕壊"
            :options="ROLE_OPTIONS"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="宀椾綅鍚嶇О">
          <a-input v-model:value="addForm.positionName" placeholder="濡傦細鏂藉伐鍛樸€佸畨鍏ㄥ憳" />
        </a-form-item>

        <a-form-item label="寮€濮嬫棩鏈?>
          <a-date-picker
            v-model:value="addForm.startDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="閫夋嫨寮€濮嬫棩鏈?
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

/* 鈹€鈹€ User cell 鈹€鈹€ */
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

/* 鈹€鈹€ Form 鈹€鈹€ */
.pm-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.pm-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
</style>
