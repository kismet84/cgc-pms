import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DocumentCanvas from '@/components/document/DocumentCanvas.vue'
import {
  applyCanvasLayout,
  flowLayoutConflict,
  snapCanvasMove,
  validDocumentDesignSchema,
} from '@/components/document/documentCanvasEngine'
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
  it('keeps validation, layout and snapping in the pure canvas engine', () => {
    const schema = blank()
    schema.elements = [
      { id: 'a', type: 'TEXT', xMm: 20, yMm: 30, widthMm: 20, heightMm: 10 },
      { id: 'b', type: 'TEXT', xMm: 60, yMm: 50, widthMm: 20, heightMm: 20 },
    ]

    expect(flowLayoutConflict(schema)).toBe('')
    expect(validDocumentDesignSchema(schema)).toBe(true)
    expect(
      applyCanvasLayout({
        action: 'TOP',
        items: schema.elements,
        primaryId: 'a',
        alignmentReference: 'SELECTION',
        canvasBounds: { xMm: 12, yMm: 12, widthMm: 186, heightMm: 273 },
        gap: 5,
      }).map((item) => item.yMm),
    ).toEqual([30, 30])
    expect(
      snapCanvasMove({
        dx: 3,
        dy: 2,
        moving: [schema.elements[0]!],
        references: [{ xMm: 25, yMm: 32, widthMm: 20, heightMm: 10 }],
        pxPerMm: 2,
        snapToGrid: false,
        smartGuides: true,
      }),
    ).toMatchObject({ dx: 5, dy: 2, guideLines: { xMm: 25, yMm: 32 } })
  })

  it('shows only Chinese business field labels while keeping path search', async () => {
    const wrapper = mount(DocumentCanvas, { props: { modelValue: blank(), fields } })
    const fieldButtons = wrapper.findAll('.document-canvas__field')

    expect(wrapper.find('.document-canvas__workspace > .document-canvas__toolbar').exists()).toBe(
      false,
    )
    expect(wrapper.find('.document-canvas__properties > .document-canvas__toolbar').exists()).toBe(
      true,
    )
    expect(
      wrapper.get('.document-canvas__properties').element.firstElementChild?.classList,
    ).toContain('document-canvas__toolbar')
    expect(wrapper.get('.document-canvas__property-fields h3').text()).toBe('属性')
    expect(fieldButtons.map((item) => item.text())).toEqual(['计量编号', '清单项名称'])
    expect(fieldButtons.every((item) => item.attributes('title') === undefined)).toBe(true)
    expect(wrapper.text()).not.toContain('measure.code')

    await wrapper.get('input[type="search"]').setValue('measure.code')
    expect(wrapper.findAll('.document-canvas__field').map((item) => item.text())).toEqual([
      '计量编号',
    ])
  })

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

    await wrapper.setProps({ modelValue: collection })
    expect(wrapper.get('.document-canvas__table-content th').text()).toBe('清单项名称')
    expect(wrapper.get('.document-canvas__table-content td code').text()).toBe('{{name}}')
    expect(wrapper.text()).toContain('高度为最小占位')
  })

  it('blocks overlapping flow anchors and body elements below a table', async () => {
    const schema = blank()
    schema.tables = [
      {
        id: 'table-a',
        collectionPath: 'items',
        xMm: 12,
        yMm: 80,
        widthMm: 100,
        heightMm: 40,
        columns: [{ fieldPath: 'items.name', header: 'A', widthMm: 100 }],
      },
      {
        id: 'table-b',
        collectionPath: 'items',
        xMm: 12,
        yMm: 100,
        widthMm: 100,
        heightMm: 30,
        columns: [{ fieldPath: 'items.name', header: 'B', widthMm: 100 }],
      },
    ]
    const wrapper = mount(DocumentCanvas, { props: { modelValue: schema, fields } })

    expect(wrapper.get('[role="alert"]').text()).toContain('table-b 与 table-a')
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(false)

    schema.tables = [schema.tables[0]!]
    schema.elements = [
      {
        id: 'body-after',
        type: 'TEXT',
        text: '签字',
        xMm: 12,
        yMm: 140,
        widthMm: 40,
        heightMm: 10,
      },
    ]
    await wrapper.setProps({ modelValue: { ...schema } })
    expect(wrapper.get('[role="alert"]').text()).toContain('body-after')
    expect(wrapper.emitted('update:valid')!.at(-1)![0]).toBe(false)
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

  it('box-selects multiple components and aligns them', async () => {
    const schema = blank()
    schema.elements = [
      {
        id: 'field-1',
        type: 'FIELD',
        text: '字段一',
        xMm: 20,
        yMm: 30,
        widthMm: 20,
        heightMm: 10,
      },
      {
        id: 'field-2',
        type: 'FIELD',
        text: '字段二',
        xMm: 60,
        yMm: 50,
        widthMm: 20,
        heightMm: 20,
      },
      {
        id: 'field-3',
        type: 'FIELD',
        text: '字段三',
        xMm: 150,
        yMm: 100,
        widthMm: 20,
        heightMm: 10,
      },
    ]
    const wrapper = mount(DocumentCanvas, { props: { modelValue: schema, fields } })
    const page = wrapper.get('.document-canvas__page')
    vi.spyOn(page.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      top: 0,
      width: 210,
      height: 297,
      right: 210,
      bottom: 297,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    })

    page.element.dispatchEvent(
      new MouseEvent('pointerdown', { bubbles: true, clientX: 10, clientY: 20 }),
    )
    page.element.dispatchEvent(
      new MouseEvent('pointermove', { bubbles: true, clientX: 100, clientY: 90 }),
    )
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.is-selected')).toHaveLength(2)
    expect(wrapper.text()).toContain('已选 2 个')
    expect(wrapper.get('.v2-action-menu__trigger').text()).toBe('排版（2）')
    expect(
      wrapper.findAll('.document-canvas__alignment-group > strong').map((group) => group.text()),
    ).toEqual(['对齐基准', '对齐', '分布与间距', '尺寸', '批量排列', '精度', '移动辅助'])
    expect(wrapper.findAll('[data-testid^="layout-"]')).toHaveLength(20)

    await wrapper.get('[data-testid="layout-top"]').trigger('click')
    const alignedTop = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(alignedTop.elements.slice(0, 2).map((item) => item.yMm)).toEqual([30, 30])

    await wrapper.setProps({ modelValue: alignedTop })
    await wrapper.get('[data-testid="layout-right"]').trigger('click')
    const alignedRight = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(alignedRight.elements.slice(0, 2).map((item) => item.xMm)).toEqual([60, 60])
    expect(alignedRight.elements[2]?.xMm).toBe(150)
  })

  it('distributes, sizes, arranges, and aligns selected components by reference', async () => {
    const schema = blank()
    schema.elements = [
      { id: 'a', type: 'FIELD', text: 'A', xMm: 20, yMm: 30, widthMm: 20, heightMm: 10 },
      { id: 'b', type: 'FIELD', text: 'B', xMm: 60, yMm: 50, widthMm: 30, heightMm: 20 },
      { id: 'c', type: 'FIELD', text: 'C', xMm: 150, yMm: 80, widthMm: 20, heightMm: 12 },
    ]
    const wrapper = mount(DocumentCanvas, { props: { modelValue: schema, fields } })
    const page = wrapper.get('.document-canvas__page')
    vi.spyOn(page.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      top: 0,
      width: 210,
      height: 297,
      right: 210,
      bottom: 297,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    })
    page.element.dispatchEvent(
      new MouseEvent('pointerdown', { bubbles: true, clientX: 10, clientY: 20 }),
    )
    page.element.dispatchEvent(
      new MouseEvent('pointermove', { bubbles: true, clientX: 190, clientY: 110 }),
    )
    await wrapper.vm.$nextTick()

    await wrapper.get('[data-testid="layout-distribute-horizontal"]').trigger('click')
    const distributed = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(distributed.elements.map((item) => item.xMm)).toEqual([20, 80, 150])

    await wrapper.setProps({ modelValue: distributed })
    await wrapper.get('[data-testid="layout-equal-size"]').trigger('click')
    const equal = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(equal.elements.map((item) => [item.widthMm, item.heightMm])).toEqual([
      [20, 12],
      [20, 12],
      [20, 12],
    ])

    await wrapper.setProps({ modelValue: equal })
    await wrapper.get('[data-testid="layout-spacing"]').setValue('7')
    await wrapper.get('[data-testid="layout-arrange-horizontal"]').trigger('click')
    const arranged = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(arranged.elements.map((item) => [item.xMm, item.yMm])).toEqual([
      [20, 30],
      [47, 30],
      [74, 30],
    ])

    await wrapper.setProps({ modelValue: arranged })
    await wrapper.get('[data-testid="align-reference-canvas"]').trigger('click')
    await wrapper.get('[data-testid="layout-top"]').trigger('click')
    const canvasAligned = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(canvasAligned.elements.map((item) => item.yMm)).toEqual([12, 12, 12])
    expect(wrapper.get('[data-testid="snap-grid"]').attributes('aria-pressed')).toBe('false')
    await wrapper.get('[data-testid="snap-grid"]').trigger('click')
    expect(wrapper.get('[data-testid="snap-grid"]').attributes('aria-pressed')).toBe('true')
  })

  it('snaps movement to the grid and shows smart guides', async () => {
    const schema = blank()
    schema.elements = [
      { id: 'a', type: 'FIELD', text: 'A', xMm: 22, yMm: 33, widthMm: 20, heightMm: 10 },
      { id: 'b', type: 'FIELD', text: 'B', xMm: 60, yMm: 70, widthMm: 20, heightMm: 10 },
    ]
    const wrapper = mount(DocumentCanvas, { props: { modelValue: schema, fields } })
    const page = wrapper.get('.document-canvas__page')
    vi.spyOn(page.element, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      top: 0,
      width: 210,
      height: 297,
      right: 210,
      bottom: 297,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    })
    const first = wrapper.findAll('.document-canvas__element')[0]!
    await wrapper.get('[data-testid="snap-grid"]').trigger('click')
    first.element.dispatchEvent(
      new MouseEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }),
    )
    first.element.dispatchEvent(
      new MouseEvent('pointermove', { bubbles: true, clientX: 2, clientY: 1 }),
    )
    await wrapper.vm.$nextTick()
    const snapped = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(snapped.elements[0]).toMatchObject({ xMm: 25, yMm: 35 })

    await wrapper.setProps({ modelValue: schema })
    await wrapper.get('[data-testid="snap-grid"]').trigger('click')
    first.element.dispatchEvent(
      new MouseEvent('pointerdown', { bubbles: true, clientX: 0, clientY: 0 }),
    )
    first.element.dispatchEvent(
      new MouseEvent('pointermove', { bubbles: true, clientX: 38, clientY: 0 }),
    )
    await wrapper.vm.$nextTick()
    const guided = wrapper.emitted('update:modelValue')!.at(-1)![0] as DocumentDesignSchema
    expect(guided.elements[0]?.xMm).toBe(60)
    expect(wrapper.get('.document-canvas__guide.is-vertical').attributes('style')).toContain('60mm')
  })
})
