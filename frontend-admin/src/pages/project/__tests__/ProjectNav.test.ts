import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))

describe('ProjectNav — navigation handlers', () => {
  // ── TEST 1: Project name click navigates to project overview ──
  it('wires project name click to router.push /project/:id/overview', () => {
    const source = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')
    // Template: @click wired with router.push to overview
    expect(source).toMatch(/@click="router\.push\(`\/project\/[^`]*overview`\)"/)
  })

  // ── TEST 2: Partner name is NOT a clickable link (no partner detail route) ──
  it('renders partner name as plain text, not a fake link', () => {
    const source = readFileSync(resolve(currentDir, '../../partner/index.vue'), 'utf-8')
    // Must NOT contain <a ...> with partnerName
    const hasFakeLink = /<a\b[^>]*"record\.partnerName/.test(source)
    expect(hasFakeLink).toBe(false)
    // partnerName exists in the source
    expect(source).toMatch(/partnerName/)
  })

  // ── TEST 3: Login forgot-password handler wired to message.info ──
  it('wires forgot-password to message.info with admin-reset message', () => {
    const source = readFileSync(resolve(currentDir, '../../login/index.vue'), 'utf-8')
    // handleForgotPassword function defined
    expect(source).toMatch(/function handleForgotPassword/)
    // Calls message.info with the correct message
    expect(source).toMatch(/message\.info\('请联系系统管理员重置密码'\)/)
    // Template wires @click to handleForgotPassword
    expect(source).toMatch(/@click="handleForgotPassword"/)
  })
})
