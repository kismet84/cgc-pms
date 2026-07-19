import { readdir, stat } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(fileURLToPath(new URL('..', import.meta.url)))
const assets = resolve(root, 'dist/assets')
const limit = 300 * 1024
const files = await readdir(assets)
const oversized = []

for (const file of files.filter((name) => name.endsWith('.js'))) {
  const bytes = (await stat(resolve(assets, file))).size
  if (bytes > limit) oversized.push({ file, bytes })
}

if (oversized.length) {
  for (const item of oversized) console.error(`${item.file}: ${item.bytes} bytes > ${limit}`)
  process.exit(1)
}

console.log(
  `V2 bundle size passed: ${files.filter((name) => name.endsWith('.js')).length} JS assets.`,
)
