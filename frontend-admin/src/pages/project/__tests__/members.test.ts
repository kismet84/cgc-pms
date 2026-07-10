import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectMembersPage from '../members.vue'

const mocks = vi.hoisted(() => ({
  permissions: new Set(['project:member:list']),
  projectStore: {
    currentProject: { projectName: '测试项目' },
    members: [
      {
        id: 'member-1',
        projectId: 'project-1',
        userId: 'user-1',
        roleCode: 'PM',
        status: 'ACTIVE',
      },
    ],
    membersTotal: 1,
    membersLoading: false,
    fetchProject: vi.fn().mockResolvedValue(undefined),
    fetchMembers: vi.fn().mockResolvedValue(undefined),
    addMember: vi.fn().mockResolvedValue(undefined),
    updateMember: vi.fn().mockResolvedValue(undefined),
    removeMember: vi.fn().mockResolvedValue(undefined),
  },
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ hasPermission: (code: string) => mocks.permissions.has(code) }),
}))

vi.mock('@/stores/project', () => ({
  useProjectStore: () => mocks.projectStore,
}))

vi.mock('@/api/modules/system', () => ({
  getUserList: vi.fn().mockResolvedValue({
    records: [{ id: 'user-1', username: 'tester', realName: '测试成员' }],
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { projectId: 'project-1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), warning: vi.fn() },
  Modal: { confirm: vi.fn() },
}))

const ATableStub = defineComponent({
  props: {
    dataSource: { type: Array, default: () => [] },
    columns: { type: Array, default: () => [] },
  },
  template: `
    <div class="table-stub">
      <template v-for="record in dataSource" :key="record.id">
        <template v-for="column in columns" :key="column.dataIndex">
          <slot name="bodyCell" :column="column" :record="record" />
        </template>
      </template>
    </div>
  `,
})

const AModalStub = defineComponent({
  props: { open: { type: Boolean, default: false } },
  template: '<div v-if="open" class="modal-stub"><slot /></div>',
})

const ASelectStub = defineComponent({
  props: {
    value: { type: String, default: '' },
    options: { type: Array, default: () => [] },
  },
  template: '<div class="role-edit"></div>',
})

function mountPage() {
  return mount(ProjectMembersPage, {
    global: {
      stubs: {
        ColumnSettingsButton: true,
        'a-avatar': { template: '<span><slot /></span>' },
        'a-breadcrumb': { template: '<div><slot /></div>' },
        'a-breadcrumb-item': { template: '<span><slot /></span>' },
        'a-button': { template: '<button><slot /></button>' },
        'a-date-picker': true,
        'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
        'a-empty': true,
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<div><slot /></div>' },
        'a-input': true,
        'a-menu': { template: '<div><slot /></div>' },
        'a-menu-item': { template: '<button class="delete-action"><slot /></button>' },
        'a-modal': AModalStub,
        'a-select': ASelectStub,
        'a-table': ATableStub,
        'a-tag': { template: '<span><slot /></span>' },
        ArrowLeftOutlined: true,
        MoreOutlined: true,
        PlusOutlined: true,
      },
    },
  })
}

describe('project/members.vue permissions', () => {
  beforeEach(() => {
    mocks.permissions = new Set(['project:member:list'])
    vi.clearAllMocks()
  })

  it('只读成员权限不会渲染新增、角色编辑和移除控件', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).not.toContain('添加成员')
    expect(wrapper.find('.role-edit').exists()).toBe(false)
    expect(wrapper.find('.delete-action').exists()).toBe(false)
  })
})
