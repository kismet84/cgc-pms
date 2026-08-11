import type {
  PurchaseOrderCommand,
  PurchaseOrderFromRequestCommand,
  PurchaseOrderItemRecord,
} from '@cgc-pms/frontend-contracts'

export interface PurchaseOrderApplicationDependencies {
  create(command: PurchaseOrderCommand): Promise<string>
  createFromRequest(command: PurchaseOrderFromRequestCommand): Promise<string>
  update(id: string, command: PurchaseOrderCommand & { orderCode: string }): Promise<unknown>
  saveItems(id: string, items: PurchaseOrderItemRecord[]): Promise<unknown>
  deleteDraft(id: string): Promise<unknown>
  submit(id: string): Promise<unknown>
}

export type SavePurchaseOrderCommand =
  | { kind: 'FROM_REQUEST'; command: PurchaseOrderFromRequestCommand }
  | {
      kind: 'CREATE_EXCEPTION'
      command: PurchaseOrderCommand
      items(id: string): PurchaseOrderItemRecord[]
      saveItems: boolean
    }
  | {
      kind: 'EDIT'
      id: string
      command: PurchaseOrderCommand & { orderCode: string }
      items(id: string): PurchaseOrderItemRecord[]
    }

export class NewPurchaseOrderSaveError extends Error {
  constructor(
    readonly saveError: unknown,
    readonly rollbackFailed: boolean,
    readonly rollbackError?: unknown,
  ) {
    super('Failed to save new purchase order')
    this.name = 'NewPurchaseOrderSaveError'
  }
}

export async function savePurchaseOrder(
  input: SavePurchaseOrderCommand,
  dependencies: PurchaseOrderApplicationDependencies,
): Promise<string> {
  if (input.kind === 'FROM_REQUEST') return dependencies.createFromRequest(input.command)
  if (input.kind === 'EDIT') {
    await dependencies.update(input.id, input.command)
    await dependencies.saveItems(input.id, input.items(input.id))
    return input.id
  }

  const id = await dependencies.create(input.command)
  if (!input.saveItems) return id
  try {
    await dependencies.saveItems(id, input.items(id))
    return id
  } catch (saveError) {
    try {
      await dependencies.deleteDraft(id)
    } catch (rollbackError) {
      throw new NewPurchaseOrderSaveError(saveError, true, rollbackError)
    }
    throw new NewPurchaseOrderSaveError(saveError, false)
  }
}

export function submitSavedPurchaseOrder(
  id: string,
  dependencies: PurchaseOrderApplicationDependencies,
): Promise<unknown> {
  return dependencies.submit(id)
}
