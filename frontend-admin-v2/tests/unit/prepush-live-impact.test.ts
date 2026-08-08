import { spawnSync } from 'node:child_process'
import { describe, expect, it } from 'vitest'

function classify(files: string[]): boolean[] {
  const result = spawnSync(process.execPath, ['scripts/prepush-live-impact.mjs', ...files], {
    cwd: process.cwd(),
    encoding: 'utf8',
  })
  expect(result.status, result.stderr).toBe(0)
  return JSON.parse(result.stdout) as boolean[]
}

describe('pre-push local live impact classifier', () => {
  it('classifies demo scripts, live helpers and live specs as requiring separate evidence', () => {
    expect(
      classify([
        'scripts/demo/complete-project-v2/verify-live-all.ps1',
        'frontend-admin-v2/e2e/live-test.ts',
        'frontend-admin-v2/e2e/shell-live.spec.ts',
      ]),
    ).toEqual([true, true, true])
  })

  it('does not classify ordinary contract-only files as live evidence inputs', () => {
    expect(
      classify(['frontend-admin-v2/e2e/auth.spec.ts', 'frontend-admin-v2/src/App.vue']),
    ).toEqual([false, false])
  })
})
