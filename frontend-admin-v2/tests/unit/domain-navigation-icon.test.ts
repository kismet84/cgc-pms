import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'

describe('DomainNavigationIcon', () => {
  it('renders every navigation domain with currentColor geometry', () => {
    const domainIds = [
      'workbench',
      'delivery',
      'commercial',
      'supply',
      'subcontract-settlement',
      'finance',
      'master-data',
      'system-management',
    ]

    for (const domainId of domainIds) {
      const icon = mount(DomainNavigationIcon, { props: { domainId } })
      expect(icon.find('svg').attributes('aria-hidden')).toBe('true')
      expect(icon.findAll('[stroke="currentColor"], [fill="currentColor"]')).not.toHaveLength(0)
    }
  })
})
