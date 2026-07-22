import {
  CLOSEOUT_API,
  type ArchiveTransferCommand,
  type CloseProjectCommand,
  type CloseoutOverview,
  type CloseoutTrace,
  type DefectCommand,
  type DefectVerificationCommand,
  type FinalAcceptanceCommand,
  type InitiateCloseoutCommand,
  type ProjectCloseoutRecord,
  type RectificationCommand,
  type SectionAcceptanceCommand,
  type SettlementBindingCommand,
  type WarrantyCommand,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export function loadCloseoutOverview(projectId: string, signal?: AbortSignal) {
  return apiRequest<Record<string, unknown>>(
    `${CLOSEOUT_API.overview}?projectId=${encoded(projectId)}`,
    { signal },
  ).then((row) => normalize(row) as unknown as CloseoutOverview)
}

export function initiateProjectCloseout(command: InitiateCloseoutCommand) {
  return write<ProjectCloseoutRecord, InitiateCloseoutCommand>(CLOSEOUT_API.initiate, command)
}

export function createSectionAcceptance(closeoutId: string, command: SectionAcceptanceCommand) {
  return write(CLOSEOUT_API.sectionAcceptances(encoded(closeoutId)), command)
}

export function confirmSectionAcceptance(sectionId: string) {
  return post(CLOSEOUT_API.confirmSectionAcceptance(encoded(sectionId)))
}

export function createFinalAcceptance(closeoutId: string, command: FinalAcceptanceCommand) {
  return write(CLOSEOUT_API.finalAcceptance(encoded(closeoutId)), command)
}

export function submitFinalAcceptance(finalAcceptanceId: string) {
  return post(CLOSEOUT_API.submitFinalAcceptance(encoded(finalAcceptanceId)))
}

export function bindFinalSettlement(closeoutId: string, command: SettlementBindingCommand) {
  return write(CLOSEOUT_API.finalSettlement(encoded(closeoutId)), command)
}

export function verifyTailCollection(closeoutId: string) {
  return post(CLOSEOUT_API.verifyTailCollection(encoded(closeoutId)))
}

export function registerWarranty(closeoutId: string, command: WarrantyCommand) {
  return write(CLOSEOUT_API.warranties(encoded(closeoutId)), command)
}

export function createCloseoutDefect(warrantyId: string, command: DefectCommand) {
  return write(CLOSEOUT_API.defects(encoded(warrantyId)), command)
}

export function rectifyCloseoutDefect(defectId: string, command: RectificationCommand) {
  return write(CLOSEOUT_API.rectifyDefect(encoded(defectId)), command)
}

export function verifyCloseoutDefect(defectId: string, command: DefectVerificationCommand) {
  return write(CLOSEOUT_API.verifyDefect(encoded(defectId)), command)
}

export function releaseWarranty(warrantyId: string) {
  return post(CLOSEOUT_API.releaseWarranty(encoded(warrantyId)))
}

export function createArchiveTransfer(closeoutId: string, command: ArchiveTransferCommand) {
  return write(CLOSEOUT_API.archiveTransfer(encoded(closeoutId)), command)
}

export function acceptArchiveTransfer(archiveTransferId: string) {
  return post(CLOSEOUT_API.acceptArchiveTransfer(encoded(archiveTransferId)))
}

export function closeProjectCloseout(closeoutId: string, command: CloseProjectCommand) {
  return write(CLOSEOUT_API.close(encoded(closeoutId)), command)
}

export function loadCloseoutTrace(closeoutId: string, signal?: AbortSignal) {
  return apiRequest<Record<string, unknown>>(CLOSEOUT_API.trace(encoded(closeoutId)), {
    signal,
  }).then((row) => normalize(row) as unknown as CloseoutTrace)
}

function post<T>(path: string): Promise<T> {
  return apiRequest<Record<string, unknown>>(path, { method: 'POST' }).then(
    (row) => normalize(row) as unknown as T,
  )
}

function write<T, B>(path: string, body: B): Promise<T> {
  return apiRequest<Record<string, unknown>, B>(path, { method: 'POST', body }).then(
    (row) => normalize(row) as unknown as T,
  )
}

function required(value: string): string {
  const safe = value.trim()
  if (!safe) throw new TypeError('ID不能为空')
  return safe
}

function encoded(value: string): string {
  return encodeURIComponent(required(value))
}

function normalize(value: unknown, key = ''): unknown {
  if (Array.isArray(value)) return value.map((item) => normalize(item))
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([name, item]) => {
        const camel = name.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())
        return [camel, normalize(item, camel)]
      }),
    )
  }
  if (value == null) return value
  if (key === 'id' || key.endsWith('Id') || key.endsWith('By')) return String(value)
  if (key.endsWith('Amount') || key === 'actualProgress') return String(value)
  return value
}
