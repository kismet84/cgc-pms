import type { ReceiptCommand, ReceiptItemRecord } from '@cgc-pms/frontend-contracts'

export interface MaterialReceiptApplicationDependencies {
  create(command: ReceiptCommand): Promise<string>
  update(id: string, command: ReceiptCommand): Promise<unknown>
  saveItems(id: string, items: ReceiptItemRecord[]): Promise<unknown>
  deleteDraft(id: string): Promise<unknown>
  submit(id: string): Promise<unknown>
}

export type SaveMaterialReceiptCommand = {
  id?: string
  command: ReceiptCommand
  items(id: string): ReceiptItemRecord[]
  saveItems: boolean
}

export class NewMaterialReceiptSaveError extends Error {
  constructor(
    readonly saveError: unknown,
    readonly rollbackFailed: boolean,
    readonly rollbackError?: unknown,
  ) {
    super('Failed to save new material receipt')
    this.name = 'NewMaterialReceiptSaveError'
  }
}

export async function saveMaterialReceipt(
  input: SaveMaterialReceiptCommand,
  dependencies: MaterialReceiptApplicationDependencies,
): Promise<string> {
  if (input.id) {
    await dependencies.update(input.id, input.command)
    if (input.saveItems) {
      await dependencies.saveItems(input.id, input.items(input.id))
    }
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
      throw new NewMaterialReceiptSaveError(saveError, true, rollbackError)
    }
    throw new NewMaterialReceiptSaveError(saveError, false)
  }
}

export function submitSavedMaterialReceipt(
  id: string,
  dependencies: MaterialReceiptApplicationDependencies,
): Promise<unknown> {
  return dependencies.submit(id)
}
