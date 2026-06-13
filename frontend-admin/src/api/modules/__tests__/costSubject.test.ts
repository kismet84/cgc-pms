import { describe, it, expect, vi } from 'vitest'
import { getCostSubjectTree, type CostSubjectTreeNode } from '../costSubject'

// Mock the request module - must return a Promise to match real behavior
vi.mock('@/api/request', () => ({
  request: vi.fn(() => Promise.resolve([])),
}))

describe('costSubject API module', () => {
  describe('exports', () => {
    it('should export getCostSubjectTree as a function', () => {
      expect(getCostSubjectTree).toBeDefined()
      expect(typeof getCostSubjectTree).toBe('function')
    })
  })

  describe('CostSubjectTreeNode interface', () => {
    it('should accept a valid tree node shape', () => {
      const node: CostSubjectTreeNode = {
        id: '1',
        subjectCode: 'CS-001',
        subjectName: '材料费',
        parentId: null,
        level: 1,
        children: [
          {
            id: '2',
            subjectCode: 'CS-001-01',
            subjectName: '钢材',
            parentId: '1',
            level: 2,
          },
        ],
      }
      expect(node.id).toBe('1')
      expect(node.subjectCode).toBe('CS-001')
      expect(node.subjectName).toBe('材料费')
      expect(node.parentId).toBeNull()
      expect(node.level).toBe(1)
      expect(node.children).toHaveLength(1)
      expect(node.children![0].subjectName).toBe('钢材')
    })
  })

  describe('getCostSubjectTree', () => {
    it('should return a Promise', () => {
      const result = getCostSubjectTree()
      expect(result).toBeInstanceOf(Promise)
    })
  })
})
