import { fileURLToPath } from 'node:url'
import { liveSpecs } from './e2e-spec-groups.mjs'

const liveSpecSet = new Set(liveSpecs.map((name) => `frontend-admin-v2/e2e/${name}`))

export function requiresLocalLiveEvidence(file) {
  const normalized = file.replaceAll('\\', '/')
  return (
    normalized.startsWith('scripts/demo/complete-project-v2/') ||
    normalized === 'frontend-admin-v2/e2e/live-test.ts' ||
    normalized === 'frontend-admin-v2/scripts/e2e-spec-groups.mjs' ||
    liveSpecSet.has(normalized)
  )
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  console.log(JSON.stringify(process.argv.slice(2).map((file) => requiresLocalLiveEvidence(file))))
}
