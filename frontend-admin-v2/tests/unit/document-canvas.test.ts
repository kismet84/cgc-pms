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

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '横向')!
      .trigger('click')
    const changed = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(changed.page.orientation).toBe('LANDSCAPE')
    expect(changed.elements[0]?.xMm).toBe(180)
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(true)

    await wrapper.setProps({ modelValue: { ...schema, page: changed.page } })
    await wrapper
      .findAll('button')
      .find((item) => item.text() === '纵向')!
      .trigger('click')
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(false)
  })
})
