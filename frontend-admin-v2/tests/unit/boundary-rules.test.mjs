import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { findBoundaryViolations } from '../../scripts/boundary-rules.mjs'

describe('Clean-room boundary rules', () => {
  it('rejects an intentional Legacy Vue import fixture', async () => {
    const fixture = await readFile(resolve('tests/fixtures/legacy-import.ts.txt'), 'utf8')
    expect(findBoundaryViolations(fixture, 'legacy-import.ts')).toEqual(
      expect.arrayContaining([expect.objectContaining({ rule: 'legacy-source-path' })]),
    )
  })

  it('allows the shared contract package', () => {
    const source = "import type { UserInfo } from '@cgc-pms/frontend-contracts'"
    expect(findBoundaryViolations(source)).toEqual([])
  })
})
