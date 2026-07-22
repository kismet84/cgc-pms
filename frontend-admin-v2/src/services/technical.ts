import {
  TECHNICAL_API,
  type AcceptanceArchive,
  type ArchiveCommand,
  type ConstructionReference,
  type ConstructionReferenceCommand,
  type DisclosureCommand,
  type DrawingReview,
  type DrawingReceiptCommand,
  type DrawingTrace,
  type DrawingVersion,
  type DrawingVersionCommand,
  type ResponseReviewCommand,
  type ReviewCommand,
  type RfiCommand,
  type RfiResponse,
  type RfiResponseCommand,
  type SchemeCommand,
  type TechnicalDisclosure,
  type TechnicalOverview,
  type TechnicalRfi,
  type TechnicalScheme,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export function loadTechnicalOverview(projectId: string, signal?: AbortSignal) {
  return apiRequest<Record<string, unknown>>(
    `${TECHNICAL_API.overview}?projectId=${encoded(projectId)}`,
    { signal },
  ).then((row) => normalize(row) as unknown as TechnicalOverview)
}
export function createTechnicalScheme(command: SchemeCommand) {
  return write<TechnicalScheme, SchemeCommand>(TECHNICAL_API.schemes, command)
}
export function submitTechnicalScheme(id: string) {
  return post<TechnicalScheme>(TECHNICAL_API.submitScheme(encoded(id)))
}
export function receiveTechnicalDrawing(command: DrawingReceiptCommand) {
  return write<DrawingTrace, DrawingReceiptCommand>(TECHNICAL_API.drawings, command)
}
export function receiveDrawingVersion(drawingId: string, command: DrawingVersionCommand) {
  return write<DrawingVersion, DrawingVersionCommand>(
    TECHNICAL_API.versions(encoded(drawingId)),
    command,
  )
}
export function createDrawingReview(versionId: string, command: ReviewCommand) {
  return write<DrawingReview, ReviewCommand>(TECHNICAL_API.reviews(encoded(versionId)), command)
}
export function confirmDrawingReview(id: string) {
  return post<DrawingReview>(TECHNICAL_API.confirmReview(encoded(id)))
}
export function createTechnicalRfi(reviewId: string, command: RfiCommand) {
  return write<TechnicalRfi, RfiCommand>(TECHNICAL_API.rfis(encoded(reviewId)), command)
}
export function submitTechnicalRfi(id: string) {
  return post<TechnicalRfi>(TECHNICAL_API.submitRfi(encoded(id)))
}
export function respondTechnicalRfi(id: string, command: RfiResponseCommand) {
  return write<RfiResponse, RfiResponseCommand>(TECHNICAL_API.responses(encoded(id)), command)
}
export function reviewTechnicalRfiResponse(id: string, command: ResponseReviewCommand) {
  return write<RfiResponse, ResponseReviewCommand>(
    TECHNICAL_API.reviewResponse(encoded(id)),
    command,
  )
}
export function createTechnicalDisclosure(projectId: string, command: DisclosureCommand) {
  return write<TechnicalDisclosure, DisclosureCommand>(
    TECHNICAL_API.disclosures(encoded(projectId)),
    command,
  )
}
export function confirmTechnicalDisclosure(id: string) {
  return post<TechnicalDisclosure>(TECHNICAL_API.confirmDisclosure(encoded(id)))
}
export function createConstructionReference(
  projectId: string,
  command: ConstructionReferenceCommand,
) {
  return write<ConstructionReference, ConstructionReferenceCommand>(
    TECHNICAL_API.references(encoded(projectId)),
    command,
  )
}
export function createAcceptanceArchive(projectId: string, command: ArchiveCommand) {
  return write<AcceptanceArchive, ArchiveCommand>(
    TECHNICAL_API.archives(encoded(projectId)),
    command,
  )
}
export function confirmAcceptanceArchive(id: string) {
  return post<AcceptanceArchive>(TECHNICAL_API.confirmArchive(encoded(id)))
}
export function loadDrawingTrace(id: string, signal?: AbortSignal) {
  return apiRequest<Record<string, unknown>>(TECHNICAL_API.trace(encoded(id)), { signal }).then(
    (row) => normalize(row) as unknown as DrawingTrace,
  )
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
  return value != null && (key === 'id' || key.endsWith('Id') || key.endsWith('By'))
    ? String(value)
    : value
}
