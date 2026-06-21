import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const orderSource = readFileSync(resolve(currentDir, '../order.vue'), 'utf-8')

describe('VariationOrderPage save chain integrity', () => {
  describe('createVarOrder returns string (not {id}) — Bug FE-01 fix', () => {
    it('handleSubmit uses returned string directly, not .id property', () => {
      // createVarOrder returns Promise<string>, so res is a string.
      // The fix: const id = await createVarOrder(formData) then saveVarOrderItems(id, ...)
      expect(orderSource).toMatch(
        /const\s+id\s+=\s+await\s+createVarOrder\(formData\)/,
      )
      expect(orderSource).toMatch(
        /await\s+saveVarOrderItems\(id,\s*itemList\.value\)/,
      )
    })

    it('does NOT reference res.id for createVarOrder result', () => {
      // The old buggy pattern was "const res = await createVarOrder(...); saveVarOrderItems(res.id, ...)"
      // This must not appear in the source
      expect(orderSource).not.toMatch(/saveVarOrderItems\(res\.id/)
    })
  })

  describe('handleEdit detail load failure — Bug FE-02 fix', () => {
    it('shows error message and returns early on getVarOrderDetail failure', () => {
      // On failure, the function should return without opening the modal
      expect(orderSource).toMatch(/message\.error\([\s\S]*?加载变更明细/)
      // The function should return early (before modalVisible.value = true)
      expect(orderSource).toMatch(/catch[\s\S]*?message\.error[\s\S]*?return/)
    })

    it('does NOT set itemList to empty array on failure', () => {
      // The old buggy pattern: catch { itemList.value = [] } would allow saving empty list
      // After the fix, the function returns early with message.error instead
      const handleEditFn = orderSource.match(
        /async function handleEdit[\s\S]*?\n\}/,
      )
      if (handleEditFn) {
        expect(handleEditFn[0]).not.toMatch(/catch[\s\S]*?itemList\.value\s*=\s*\[\]/)
      }
    })
  })
})
