import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const apiSource = readFileSync(resolve(__dirname, '../../../../api/modules/user.ts'), 'utf8')
const pageSource = readFileSync(resolve(__dirname, '../index.vue'), 'utf8')

describe('system user authoritative detail loading', () => {
  it('calls the existing detail endpoint with GET', () => {
    expect(apiSource).toContain('export function getUserDetail(id: string)')
    expect(apiSource).toContain('url: `/system/users/${id}`')
    expect(apiSource).toContain("method: 'get'")
  })

  it('opens edit only after the current detail request succeeds', () => {
    const editHandler = pageSource.slice(
      pageSource.indexOf('async function handleEdit'),
      pageSource.indexOf('async function handleModalOk'),
    )

    expect(editHandler).toContain('const detail = await getUserDetail(record.id)')
    expect(editHandler).toContain('if (requestSequence !== editRequestSequence) return')
    expect(editHandler.indexOf('modalVisible.value = true')).toBeGreaterThan(
      editHandler.indexOf('await getUserDetail(record.id)'),
    )
    expect(editHandler).not.toContain('username: record.username')
    expect(editHandler).toContain("password: ''")
    expect(editHandler).toContain('roleIds: detail.roleIds ? [...detail.roleIds] : []')
    expect(editHandler).toContain('加载用户详情失败，请重试')
  })

  it('invalidates an in-flight edit when add is selected', () => {
    const addHandler = pageSource.slice(
      pageSource.indexOf('function handleAdd'),
      pageSource.indexOf('async function handleEdit'),
    )
    expect(addHandler).toContain('editRequestSequence += 1')
  })
})
