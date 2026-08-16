import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import * as commercial from '@/services/commercial'
import type { BidCostOption, BidOwnerOption, CostSubjectOption } from '@/services/commercial'
import * as finance from '@/services/finance'
import type { BidFundAccountOption, PaymentSourceOptionRecord } from '@/services/finance'
import * as supplyChain from '@/services/supply-chain'
import type {
  PurchaseRequestApprovalCommand,
  PurchaseRequestFormOptions,
  RequisitionFormOptions,
  SupplyFormMaterialOption,
} from '@/services/supply-chain'
import * as systemManagement from '@/services/system-management'
import type {
  AuditRecord,
  DataMaintenancePreview,
  DictDataRecord,
  DocumentDesignSchema,
  DocumentGenerationRecord,
  MenuRecord,
  RoleRecord,
  UserRecord,
} from '@/services/system-management'
import { describe, expect, expectTypeOf, it } from 'vitest'

const read = (path: string) => readFileSync(resolve(path), 'utf8')

const domainModules = {
  commercial: ['types', 'support', 'contract', 'variation', 'bid', 'cost', 'measurement'],
  'supply-chain': ['types', 'support', 'inventory', 'requisition', 'purchase', 'sourcing'],
  finance: ['types', 'support', 'payment', 'trace', 'cashbook', 'control', 'revenue'],
  'system-management': ['support', 'access', 'menu', 'dictionary', 'document', 'audit-maintenance'],
} as const

const runtimeExportHashes = {
  commercial: 'bb9aaf1406c228e7231246efb440d0e060c38876b11016d6e7fff1d73ad71ee1',
  'supply-chain': 'e548592d2a6465aed7839d569119e5ae7871a9ecc464c4a6e3e393bcea9a5c78',
  finance: 'cefd96ba3dd5285737480353a26211801c6b6582e071e63d492e2b06eb8ddc53',
  'system-management': 'd0f17314a8d9d61d2765e77d5ae3a781b453bff1acc4c4b5a18425cfadf6381a',
} as const

const typeExportHashes = {
  commercial: '73aa6feb3d3f10fcd745d52db41970d4a1485a35ceeaa238124a1d542664c56d',
  'supply-chain': 'ce12c8439b8d68390f35469a12f95d9429e6a9712bba78f50ce95c0a7738baf1',
  finance: 'cac6868daf44392f2730fedc01b29ed60b4048a29d41b6394adb01a12fc2306a',
  'system-management': 'e7b850bffe82a57df7885434cc591ae74246979ba597b9c32737f2ccfd668806',
} as const

const requestSurfaceHashes = {
  commercial: 'feedbb0e660149d81577edbe33f3492ccd689a8705579e6cbc1936d9b99eb479',
  'supply-chain': '1731f8c4bafd410bc6b98b5d8ead39b5df300346c0f83684a2c847db35669fe6',
  finance: 'f16dee2379e1a3c2cf3708288d108b6c4a88da59a39fb163ef3b19fa4d710f49',
  'system-management': 'd589588c009b1d6021783ca81ef2b48ccb89f7740dbd9fef9aa2d3e503151350',
} as const

const hash = (values: string[]) =>
  createHash('sha256')
    .update([...values].sort().join('\n'))
    .digest('hex')

const requestSurfaceHash = (source: string) =>
  hash([
    ...(source.match(/\b(?:COMMERCIAL_API|SUPPLY_CHAIN_API|FINANCE_API)\.[A-Za-z0-9_]+/g) ?? []),
    ...[...source.matchAll(/['"`](\/[A-Za-z0-9][^'"`$\r\n]*)/g)].map((match) => match[1]),
    ...(source.match(/\bmethod:\s*(?:['"][A-Z]+['"]|[A-Z_]+(?:\.[a-z]+)?)/g) ?? []),
    ...(source.match(/\bnotifyError:\s*false/g) ?? []),
  ])

describe('M94 frontend service boundaries', () => {
  it('keeps the cost-subject public path as a compatibility barrel', () => {
    const barrel = read('src/services/cost-subject.ts')

    for (const module of ['types', 'taxonomy', 'mapping', 'bid-transfer', 'finance-allocation']) {
      expect(barrel).toContain(`export * from './cost-subject/${module}'`)
    }
    expect(barrel).not.toContain('apiRequest')
  })

  it('keeps taxonomy, mapping, bid transfer and finance allocation independent', () => {
    const modules = {
      taxonomy: read('src/services/cost-subject/taxonomy.ts'),
      mapping: read('src/services/cost-subject/mapping.ts'),
      bid: read('src/services/cost-subject/bid-transfer.ts'),
      finance: read('src/services/cost-subject/finance-allocation.ts'),
    }

    expect(modules.taxonomy).toContain("'/cost-subjects/tree'")
    expect(modules.taxonomy).not.toContain('/bid-transfer')
    expect(modules.mapping).toContain("'/cost-subject-v2/mapping-versions'")
    expect(modules.mapping).not.toContain('/finance-allocation')
    expect(modules.bid).toContain("'/cost-subject-v2/bid-transfers'")
    expect(modules.bid).not.toContain('/finance-allocation')
    expect(modules.finance).toContain("'/cost-subject-v2/finance-allocations'")
    expect(modules.finance).not.toContain('/bid-transfer')
  })

  it('keeps four legacy service paths as focused compatibility barrels', () => {
    for (const [service, modules] of Object.entries(domainModules)) {
      const barrel = read(`src/services/${service}.ts`)
      for (const module of modules) {
        if (module === 'support') continue
        expect(barrel, `${service}/${module}`).toContain(`export * from './${service}/${module}'`)
      }
      expect(barrel).not.toContain('apiRequest')
    }
  })

  it('preserves exact runtime exports from every legacy public import path', () => {
    const namespaces = {
      commercial,
      'supply-chain': supplyChain,
      finance,
      'system-management': systemManagement,
    }
    for (const [service, namespace] of Object.entries(namespaces)) {
      expect(hash(Object.keys(namespace)), service).toBe(
        runtimeExportHashes[service as keyof typeof runtimeExportHashes],
      )
    }
  })

  it('preserves exact public type exports from every legacy public import path', () => {
    for (const [service, modules] of Object.entries(domainModules)) {
      const source = modules
        .map((module) => read(`src/services/${service}/${module}.ts`))
        .join('\n')
      const names = [...source.matchAll(/export\s+(?:interface|type)\s+([A-Za-z_]\w*)/g)].map(
        (match) => match[1],
      )
      expect(hash(names), service).toBe(typeExportHashes[service as keyof typeof typeExportHashes])
    }
  })

  it('preserves endpoint, method and local error-notification request tokens', () => {
    for (const [service, modules] of Object.entries(domainModules)) {
      const source = modules
        .map((module) => read(`src/services/${service}/${module}.ts`))
        .join('\n')
      expect(requestSurfaceHash(source), service).toBe(
        requestSurfaceHashes[service as keyof typeof requestSurfaceHashes],
      )
    }
  })

  it('keeps legacy type imports available', () => {
    expectTypeOf<[CostSubjectOption, BidOwnerOption, BidCostOption]>().toMatchTypeOf<
      [CostSubjectOption, BidOwnerOption, BidCostOption]
    >()
    expectTypeOf<
      [
        SupplyFormMaterialOption,
        PurchaseRequestFormOptions,
        RequisitionFormOptions,
        PurchaseRequestApprovalCommand,
      ]
    >().toMatchTypeOf<
      [
        SupplyFormMaterialOption,
        PurchaseRequestFormOptions,
        RequisitionFormOptions,
        PurchaseRequestApprovalCommand,
      ]
    >()
    expectTypeOf<[BidFundAccountOption, PaymentSourceOptionRecord]>().toMatchTypeOf<
      [BidFundAccountOption, PaymentSourceOptionRecord]
    >()
    expectTypeOf<
      [
        UserRecord,
        RoleRecord,
        MenuRecord,
        DictDataRecord,
        AuditRecord,
        DataMaintenancePreview,
        DocumentDesignSchema,
        DocumentGenerationRecord,
      ]
    >().toMatchTypeOf<
      [
        UserRecord,
        RoleRecord,
        MenuRecord,
        DictDataRecord,
        AuditRecord,
        DataMaintenancePreview,
        DocumentDesignSchema,
        DocumentGenerationRecord,
      ]
    >()
  })
})
