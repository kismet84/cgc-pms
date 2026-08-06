import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DocumentCanvas from '@/components/document/DocumentCanvas.vue'
import type { DocumentDesignSchema, DocumentCatalogField } from '@/services/system-management'

const blank = (): DocumentDesignSchema => ({
  schemaVersion: 'sub-measure.v1',
  page: {
    size: 'A4',
    orientation: 'PORTRAIT',
    marginMm: { top: 12, right: 12, bottom: 12, left: 12 },
  },
  elements: [],
  tables: [],
})

const fields: DocumentCatalogField[] = [
  {
    path: 'measure.code',
    label: '计量编号',
    valueType: 'TEXT',
    nullable: false,
    group: '基本信息',
    collectionPath: null,
    masked: false,
  },
  {
    path: 'items.name',
    label: '清单项名称',
    valueType: 'TEXT',
    nullable: false,
    group: '业务明细',
    collectionPath: 'items',
    masked: false,
  },
]

describe('document canvas', () => {
  it('adds scalar fields and creates a detail table for collection fields', async () => {
    const wrapper = mount(DocumentCanvas, { props: { modelValue: blank(), fields } })

    await wrapper.findAll('.document-canvas__field')[0]!.trigger('click')
    const scalar = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(scalar.elements[0]).toMatchObject({ type: 'FIELD', fieldPath: 'measure.code' })

    await wrapper.setProps({ modelValue: blank() })
    await wrapper.findAll('.document-canvas__field')[1]!.trigger('click')
    const collection = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(collection.tables).toEqual([
      expect.objectContaining({
        collectionPath: 'items',
        heightMm: 38,
        columns: [{ fieldPath: 'items.name', header: '清单项名称', widthMm: 170 }],
      }),
    ])
  })

  it('keeps millimetre positions unchanged on orientation switch and blocks overflow', async () => {
    const schema = blank()
    schema.elements.push({
      id: 'field-1',
      type: 'FIELD',
      fieldPath: 'measure.code',
      text: '计量编号',
      xMm: 180,
      yMm: 20,
      widthMm: 30,
      heightMm: 12,
    })
    const wrapper = mount(DocumentCanvas, { props: { modelValue: schema, fields } })

    expect(wrapper.findAll('[data-testid="orientation-toggle"]')).toHaveLength(1)
    await wrapper.get('[data-testid="orientation-toggle"]').trigger('click')
    const changed = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(changed.page.orientation).toBe('LANDSCAPE')
    expect(changed.elements[0]?.xMm).toBe(180)
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(true)

    await wrapper.setProps({ modelValue: { ...schema, page: changed.page } })
    await wrapper.get('[data-testid="orientation-toggle"]').trigger('click')
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(false)
  })

  it('offers component presets and switches the shared canvas area to HTML preview', async () => {
    const wrapper = mount(DocumentCanvas, {
      props: { modelValue: blank(), fields, previewHtml: '<p>server preview</p>' },
    })

    expect(wrapper.findAll('.document-canvas__component')).toHaveLength(6)
    expect(wrapper.findAll('.document-canvas__component').map((item) => item.text())).toEqual(
      expect.arrayContaining(['分割线横向分隔内容', '表格业务明细表']),
    )

    await wrapper
      .findAll('.document-canvas__component')
      .find((item) => item.text().includes('分割线'))!
      .trigger('click')
    expect(
      (wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema).elements[0],
    ).toMatchObject({ type: 'DIVIDER', heightMm: 2 })

    await wrapper.setProps({ modelValue: blank() })
    await wrapper
      .findAll('.document-canvas__component')
      .find((item) => item.text().includes('表格'))!
      .trigger('click')
    expect(
      (wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema).tables[0],
    ).toMatchObject({ collectionPath: 'items', columns: [{ fieldPath: 'items.name' }] })
    expect(wrapper.get('.document-canvas__page').classes()).toContain('has-grid')

    await wrapper.get('[data-testid="preview-toggle"]').trigger('click')

    expect(wrapper.find('.document-canvas__page').exists()).toBe(false)
    expect(wrapper.get('.document-canvas__preview-page iframe').attributes('srcdoc')).toContain(
      'server preview',
    )
    expect(wrapper.get('.document-canvas__viewport').findAll('iframe')).toHaveLength(1)
  })
})
