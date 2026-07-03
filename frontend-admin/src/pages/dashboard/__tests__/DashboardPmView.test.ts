import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DashboardPmView from '../components/DashboardPmView.vue'

function mountPmView(data: Record<string, unknown>) {
  return mount(DashboardPmView, {
    props: {
      data,
      loading: false,
    },
    global: {
      stubs: {
        ATable: { template: '<div class="table-stub"></div>' },
        ATooltip: { template: '<div><slot /></div>' },
        AuditOutlined: true,
        WarningOutlined: true,
        ClockCircleOutlined: true,
        FileTextOutlined: true,
      },
    },
  })
}

describe('DashboardPmView', () => {
  it('renders even when PM detail lists are null', () => {
    expect(() =>
      mountPmView({
        projectId: 'p-1',
        projectName: '项目A',
        pendingTaskCount: 1,
        laggingProjectCount: 1,
        pendingApprovalCount: 1,
        expiringContractCount: 1,
        pendingTasks: null,
        laggingProjects: null,
        pendingApprovals: null,
        expiringContracts: null,
      }),
    ).not.toThrow()
  })
})
