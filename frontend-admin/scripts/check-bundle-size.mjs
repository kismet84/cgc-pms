import { readdir, stat } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const limitBytes = 500 * 1024
const assetsDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist', 'assets')

const files = await readdir(assetsDir)
const jsChunks = []

for (const file of files) {
  if (!file.endsWith('.js')) continue
  const size = (await stat(join(assetsDir, file))).size
  jsChunks.push({ file, size })
}

jsChunks.sort((a, b) => b.size - a.size)

const oversized = jsChunks.filter((chunk) => chunk.size > limitBytes)
if (oversized.length > 0) {
  console.error(`Bundle size check failed: ${oversized.length} JS chunk(s) exceed 500 KiB.`)
  for (const chunk of oversized) {
    console.error(`- ${chunk.file}: ${formatKiB(chunk.size)}`)
  }
  process.exit(1)
}

const largest = jsChunks[0]
if (largest) {
  console.log(`Bundle size check PASS: largest JS chunk is ${largest.file} (${formatKiB(largest.size)}).`)
} else {
  console.log('Bundle size check PASS: no JS chunks found.')
}

function formatKiB(bytes) {
  return `${(bytes / 1024).toFixed(2)} KiB`
}
