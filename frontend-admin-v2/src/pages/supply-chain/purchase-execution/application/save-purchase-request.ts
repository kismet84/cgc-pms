import type { PurchaseRequestCommand } from '@cgc-pms/frontend-contracts'

export interface PurchaseRequestApplicationDependencies {
  create(command: PurchaseRequestCommand): Promise<string>
  submit(id: string): Promise<unknown>
}

export function savePurchaseRequest(
  command: PurchaseRequestCommand,
  dependencies: PurchaseRequestApplicationDependencies,
): Promise<string> {
  return dependencies.create(command)
}

export function submitSavedPurchaseRequest(
  id: string,
  dependencies: PurchaseRequestApplicationDependencies,
): Promise<unknown> {
  return dependencies.submit(id)
}
