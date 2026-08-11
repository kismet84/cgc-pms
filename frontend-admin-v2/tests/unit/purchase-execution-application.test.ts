import type {
  PurchaseOrderCommand,
  PurchaseOrderFromRequestCommand,
  PurchaseOrderItemRecord,
  PurchaseRequestCommand,
  ReceiptCommand,
  ReceiptItemRecord,
} from '@cgc-pms/frontend-contracts'
import { describe, expect, it, vi } from 'vitest'
import {
  NewMaterialReceiptSaveError,
  saveMaterialReceipt,
  submitSavedMaterialReceipt,
} from '@/pages/supply-chain/purchase-execution/application/save-material-receipt'
import {
  NewPurchaseOrderSaveError,
  savePurchaseOrder,
  submitSavedPurchaseOrder,
} from '@/pages/supply-chain/purchase-execution/application/save-purchase-order'
import {
  savePurchaseRequest,
  submitSavedPurchaseRequest,
} from '@/pages/supply-chain/purchase-execution/application/save-purchase-request'

const requestCommand: PurchaseRequestCommand = { header: { projectId: 'P1' }, items: [] }
const fromRequestCommand: PurchaseOrderFromRequestCommand = {
  projectId: 'P1',
  contractId: 'C1',
  requestId: 'R1',
  deliveryTerms: 'site',
}
const orderCommand: PurchaseOrderCommand = {
  projectId: 'P1',
  contractId: 'C1',
  exceptionPurchaseFlag: 1,
  exceptionReason: 'urgent',
}
const orderItem: PurchaseOrderItemRecord = { quantity: '2', unitPrice: '3' }
const receiptCommand: ReceiptCommand = { projectId: 'P1', orderId: 'O1' }
const receiptItem: ReceiptItemRecord = { orderItemId: 'OI1', acceptedQuantity: '2' }
const orderItems = (id: string): PurchaseOrderItemRecord[] => [{ ...orderItem, orderId: id }]
const receiptItems = (id: string): ReceiptItemRecord[] => [{ ...receiptItem, receiptId: id }]

function orderDependencies() {
  return {
    create: vi.fn(async () => 'O1'),
    createFromRequest: vi.fn(async () => 'O2'),
    update: vi.fn(async () => undefined),
    saveItems: vi.fn(async () => undefined),
    deleteDraft: vi.fn(async () => undefined),
    submit: vi.fn(async () => undefined),
  }
}

function receiptDependencies() {
  return {
    create: vi.fn(async () => 'MR1'),
    update: vi.fn(async () => undefined),
    saveItems: vi.fn(async () => undefined),
    deleteDraft: vi.fn(async () => undefined),
    submit: vi.fn(async () => undefined),
  }
}

describe('purchase execution application', () => {
  it('saves and submits purchase requests through injected dependencies', async () => {
    const dependencies = {
      create: vi.fn(async () => 'R1'),
      submit: vi.fn(async () => undefined),
    }

    await expect(savePurchaseRequest(requestCommand, dependencies)).resolves.toBe('R1')
    await submitSavedPurchaseRequest('R1', dependencies)

    expect(dependencies.create).toHaveBeenCalledWith(requestCommand)
    expect(dependencies.submit).toHaveBeenCalledWith('R1')
  })

  it('creates an order from the approved request without saving client items', async () => {
    const dependencies = orderDependencies()

    await expect(
      savePurchaseOrder({ kind: 'FROM_REQUEST', command: fromRequestCommand }, dependencies),
    ).resolves.toBe('O2')

    expect(dependencies.createFromRequest).toHaveBeenCalledWith(fromRequestCommand)
    expect(dependencies.create).not.toHaveBeenCalled()
    expect(dependencies.saveItems).not.toHaveBeenCalled()
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('saves exception order items with the new string id', async () => {
    const dependencies = orderDependencies()

    await expect(
      savePurchaseOrder(
        { kind: 'CREATE_EXCEPTION', command: orderCommand, items: orderItems, saveItems: true },
        dependencies,
      ),
    ).resolves.toBe('O1')

    expect(dependencies.saveItems).toHaveBeenCalledWith('O1', [{ ...orderItem, orderId: 'O1' }])
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('skips item writes when caller lacks item-save authority', async () => {
    const dependencies = orderDependencies()

    await savePurchaseOrder(
      { kind: 'CREATE_EXCEPTION', command: orderCommand, items: orderItems, saveItems: false },
      dependencies,
    )

    expect(dependencies.saveItems).not.toHaveBeenCalled()
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('rolls back a newly created order when item save fails', async () => {
    const dependencies = orderDependencies()
    const saveError = new Error('items failed')

    await expect(
      savePurchaseOrder(
        {
          kind: 'CREATE_EXCEPTION',
          command: orderCommand,
          items: () => {
            throw saveError
          },
          saveItems: true,
        },
        dependencies,
      ),
    ).rejects.toMatchObject<NewPurchaseOrderSaveError>({ saveError, rollbackFailed: false })
    expect(dependencies.saveItems).not.toHaveBeenCalled()
    expect(dependencies.deleteDraft).toHaveBeenCalledWith('O1')
  })

  it('exposes order rollback failure for manual reconciliation', async () => {
    const dependencies = orderDependencies()
    const saveError = new Error('items failed')
    const rollbackError = new Error('delete failed')
    dependencies.saveItems.mockRejectedValueOnce(saveError)
    dependencies.deleteDraft.mockRejectedValueOnce(rollbackError)

    await expect(
      savePurchaseOrder(
        { kind: 'CREATE_EXCEPTION', command: orderCommand, items: orderItems, saveItems: true },
        dependencies,
      ),
    ).rejects.toMatchObject<NewPurchaseOrderSaveError>({
      saveError,
      rollbackFailed: true,
      rollbackError,
    })
  })

  it('updates order header before items and never deletes an edited order', async () => {
    const dependencies = orderDependencies()
    const calls: string[] = []
    dependencies.update.mockImplementation(async () => {
      calls.push('update')
    })
    dependencies.saveItems.mockImplementation(async () => {
      calls.push('items')
    })

    await expect(
      savePurchaseOrder(
        {
          kind: 'EDIT',
          id: 'O9',
          command: { ...orderCommand, orderCode: 'PO-9' },
          items: orderItems,
        },
        dependencies,
      ),
    ).resolves.toBe('O9')

    expect(calls).toEqual(['update', 'items'])
    expect(dependencies.saveItems).toHaveBeenCalledWith('O9', [{ ...orderItem, orderId: 'O9' }])
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('does not roll back an edited order when item save fails', async () => {
    const dependencies = orderDependencies()
    dependencies.saveItems.mockRejectedValueOnce(new Error('items failed'))

    await expect(
      savePurchaseOrder(
        {
          kind: 'EDIT',
          id: 'O9',
          command: { ...orderCommand, orderCode: 'PO-9' },
          items: orderItems,
        },
        dependencies,
      ),
    ).rejects.toThrow('items failed')

    expect(dependencies.update).toHaveBeenCalled()
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('submits purchase orders with the original string id', async () => {
    const dependencies = orderDependencies()
    await submitSavedPurchaseOrder('O1', dependencies)
    expect(dependencies.submit).toHaveBeenCalledWith('O1')
  })

  it('creates a receipt and saves items with the new string id', async () => {
    const dependencies = receiptDependencies()

    await expect(
      saveMaterialReceipt(
        { command: receiptCommand, items: receiptItems, saveItems: true },
        dependencies,
      ),
    ).resolves.toBe('MR1')

    expect(dependencies.saveItems).toHaveBeenCalledWith('MR1', [
      { ...receiptItem, receiptId: 'MR1' },
    ])
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('rolls back a newly created receipt when item save fails', async () => {
    const dependencies = receiptDependencies()
    const saveError = new Error('items failed')

    await expect(
      saveMaterialReceipt(
        {
          command: receiptCommand,
          items: () => {
            throw saveError
          },
          saveItems: true,
        },
        dependencies,
      ),
    ).rejects.toMatchObject<NewMaterialReceiptSaveError>({ saveError, rollbackFailed: false })
    expect(dependencies.saveItems).not.toHaveBeenCalled()
    expect(dependencies.deleteDraft).toHaveBeenCalledWith('MR1')
  })

  it('exposes receipt rollback failure for manual reconciliation', async () => {
    const dependencies = receiptDependencies()
    const saveError = new Error('items failed')
    const rollbackError = new Error('delete failed')
    dependencies.saveItems.mockRejectedValueOnce(saveError)
    dependencies.deleteDraft.mockRejectedValueOnce(rollbackError)

    await expect(
      saveMaterialReceipt(
        { command: receiptCommand, items: receiptItems, saveItems: true },
        dependencies,
      ),
    ).rejects.toMatchObject<NewMaterialReceiptSaveError>({
      saveError,
      rollbackFailed: true,
      rollbackError,
    })
  })

  it('updates a receipt without deleting it when item save fails', async () => {
    const dependencies = receiptDependencies()
    dependencies.saveItems.mockRejectedValueOnce(new Error('items failed'))

    await expect(
      saveMaterialReceipt(
        { id: 'MR9', command: receiptCommand, items: receiptItems, saveItems: true },
        dependencies,
      ),
    ).rejects.toThrow('items failed')

    expect(dependencies.update).toHaveBeenCalledWith('MR9', receiptCommand)
    expect(dependencies.deleteDraft).not.toHaveBeenCalled()
  })

  it('submits material receipts with the original string id', async () => {
    const dependencies = receiptDependencies()
    await submitSavedMaterialReceipt('MR1', dependencies)
    expect(dependencies.submit).toHaveBeenCalledWith('MR1')
  })
})
