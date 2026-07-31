import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import type { ProjectVO, MemberVO, MemberFormParams } from '@/types/project'
import { normalizeArray } from '@/utils/normalizeArray'
import {
  getProjectDetail,
  getMemberList,
  addMember as addMemberApi,
  updateMember as updateMemberApi,
  removeMember as removeMemberApi,
} from '@/api/modules/project'

export const useProjectStore = defineStore('project', () => {
  const currentProject = ref<ProjectVO | null>(null)
  const members = ref<MemberVO[]>([])
  const membersTotal = ref(0)
  const loading = ref(false)
  const saving = ref(false)
  const membersLoading = ref(false)

  async function fetchProject(id: string) {
    loading.value = true
    try {
      currentProject.value = await getProjectDetail(id)
    } catch (error) {
      message.error('加载项目信息失败')
      throw error
    } finally {
      loading.value = false
    }
  }

  async function fetchMembers(projectId: string, pageNo = 1, pageSize = 50) {
    membersLoading.value = true
    try {
      const res = await getMemberList(projectId, { pageNum: pageNo, pageSize })
      members.value = normalizeArray<MemberVO>(res.records)
      membersTotal.value = Number(res.total) || members.value.length
    } catch (error) {
      message.error('加载项目成员失败')
      members.value = []
      membersTotal.value = 0
      if (import.meta.env.DEV) {
        console.error('Project store error:', error)
      }
    } finally {
      membersLoading.value = false
    }
  }

  async function addMember(projectId: string, data: MemberFormParams) {
    saving.value = true
    try {
      await addMemberApi(projectId, data)
    } catch (error) {
      message.error('添加项目成员失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function updateMember(projectId: string, memberId: string, data: MemberFormParams) {
    saving.value = true
    try {
      await updateMemberApi(projectId, memberId, data)
    } catch (error) {
      message.error('更新项目成员失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function removeMember(projectId: string, memberId: string) {
    saving.value = true
    try {
      await removeMemberApi(projectId, memberId)
    } catch (error) {
      message.error('移除项目成员失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  function resetState() {
    currentProject.value = null
    members.value = []
    membersTotal.value = 0
    loading.value = false
    saving.value = false
    membersLoading.value = false
  }

  return {
    currentProject,
    members,
    membersTotal,
    loading,
    saving,
    membersLoading,
    fetchProject,
    fetchMembers,
    addMember,
    updateMember,
    removeMember,
    resetState,
  }
})
