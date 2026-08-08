import { createServer } from 'node:http'
import { readFileSync, writeFileSync } from 'node:fs'

const [modePath, readyPath, portText = '55173'] = process.argv.slice(2)
if (!modePath || !readyPath) throw new Error('modePath and readyPath are required')

const server = createServer((request, response) => {
  if (request.url !== '/api/actuator/health') {
    response.writeHead(404).end()
    return
  }
  const mode = readFileSync(modePath, 'utf8').trim()
  if (mode === 'up') {
    response.writeHead(200, { 'content-type': 'application/json' }).end('{"status":"UP"}')
  } else if (mode === 'nested') {
    response.writeHead(200, { 'content-type': 'application/json' }).end('{"detail":{"status":"UP"},"status":"DOWN"}')
  } else if (mode === 'bad') {
    response.writeHead(200, { 'content-type': 'application/json' }).end('{"status":"DOWN"}')
  } else {
    response.writeHead(503, { 'content-type': 'application/json' }).end('{"status":"DOWN"}')
  }
})

server.on('error', (error) => {
  writeFileSync(readyPath, `ERROR:${error.code ?? error.message}`)
  process.exitCode = 1
})
server.listen(Number(portText), '127.0.0.1', () => writeFileSync(readyPath, String(process.pid)))
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)))
