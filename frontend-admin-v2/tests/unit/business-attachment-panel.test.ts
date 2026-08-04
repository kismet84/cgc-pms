import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import BusinessAttachmentPanel from '@/components/BusinessAttachmentPanel.vue'
import { getSiteFileUrl, listSiteFiles, uploadSiteFile } from '@/services/delivery'

vi.mock('@/services/delivery', () => ({
  listSiteFiles: vi.fn(),
  uploadSiteFile: vi.fn(),
  getSiteFileUrl: vi.fn(),
  deleteSiteFile: vi.fn(),
}))

describe('BusinessAttachmentPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listSiteFiles).mockResolvedValue([
      {
        id: '11',
        originalName: '合同原件.pdf',
        businessType: 'CONTRACT',
        businessId: '3',
        virusScanStatus: 'CLEAN',
        virusScanPassed: true,
      },
    ])
    vi.mocked(getSiteFileUrl).mockResolvedValue('https://files.example/signed')
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  it('loads public metadata and obtains a signed URL only on download', async () => {
    const close = vi.fn()
    const popup = { opener: window, location: { href: '' }, close } as unknown as Window
    vi.mocked(window.open).mockReturnValue(popup)
    const wrapper = mount(BusinessAttachmentPanel, {
      props: { businessType: 'CONTRACT', businessId: '3' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('合同原件.pdf')
    expect(wrapper.text()).toContain('扫描通过')
    expect(getSiteFileUrl).not.toHaveBeenCalled()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(getSiteFileUrl).toHaveBeenCalledWith('11')
    expect(window.open).toHaveBeenCalledWith('about:blank', '_blank')
    expect(popup.opener).toBeNull()
    expect(popup.location.href).toBe('https://files.example/signed')
    expect(close).not.toHaveBeenCalled()
  })

  it('uploads through the shared service and re-reads server state', async () => {
    vi.mocked(uploadSiteFile).mockResolvedValue({ id: '12', originalName: 'new.pdf' })
    const wrapper = mount(BusinessAttachmentPanel, {
      props: {
        businessType: 'SETTLEMENT',
        businessId: '7',
        documentType: 'OTHER',
        canUpload: true,
      },
    })
    await flushPromises()
    const input = wrapper.get('input[type="file"]')
    const file = new File(['%PDF-1.4'], 'new.pdf', { type: 'application/pdf' })
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })

    await input.trigger('change')
    await flushPromises()

    expect(uploadSiteFile).toHaveBeenCalledWith(file, 'SETTLEMENT', '7', 'OTHER')
    expect(listSiteFiles).toHaveBeenCalledTimes(2)
  })

  it('keeps only the latest business request when props change', async () => {
    let resolveFirst: ((value: Awaited<ReturnType<typeof listSiteFiles>>) => void) | undefined
    vi.mocked(listSiteFiles)
      .mockImplementationOnce(
        () => new Promise((resolve) => { resolveFirst = resolve }),
      )
      .mockResolvedValueOnce([
        { id: '22', originalName: 'new-business.pdf', virusScanStatus: 'CLEAN' },
      ])
    const wrapper = mount(BusinessAttachmentPanel, {
      props: { businessType: 'CONTRACT', businessId: 'old' },
    })

    await wrapper.setProps({ businessId: 'new' })
    await flushPromises()
    resolveFirst?.([{ id: '21', originalName: 'stale.pdf', virusScanStatus: 'CLEAN' }])
    await flushPromises()

    expect(wrapper.text()).toContain('new-business.pdf')
    expect(wrapper.text()).not.toContain('stale.pdf')
  })
})
