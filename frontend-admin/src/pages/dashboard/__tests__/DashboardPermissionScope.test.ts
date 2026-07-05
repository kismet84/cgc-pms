import { describe, expect, it } from 'vitest'
import {
  ALL_DASHBOARD_ROLES,
  resolveAvailableDashboardRoles,
} from '../composables/useDashboardData'

describe('Dashboard permission scope', () => {
  it('does not expand legacy dashboard:view into all dashboard tabs', () => {
    expect(resolveAvailableDashboardRoles(['USER'], ['dashboard:view'])).toEqual([])
  })

  it('keeps a single scoped dashboard permission limited to its own tab', () => {
    expect(resolveAvailableDashboardRoles(['USER'], ['dashboard:project-manager:view'])).toEqual([
      'pm',
    ])
  })

  it('keeps admin and super admin full dashboard coverage', () => {
    expect(resolveAvailableDashboardRoles(['ADMIN'], ['dashboard:view'])).toEqual(ALL_DASHBOARD_ROLES)
    expect(resolveAvailableDashboardRoles(['SUPER_ADMIN'], [])).toEqual(ALL_DASHBOARD_ROLES)
  })
})
