import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ProjectVO, MemberVO, MemberFormParams } from '@/types/project'
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
    } finally {
      loading.value = false
    }
  }

  async function fetchMembers(projectId: string, pageNo = 1, pageSize = 50) {
    membersLoading.value = true
    try {
      const res = await getMemberList(projectId, { pageNum: pageNo, pageSize })
      members.value = res.records
      membersTotal.value = res.total
    } finally {
      membersLoading.value = false
    }
  }

  async function addMember(projectId: string, data: MemberFormParams) {
    saving.value = true
    try {
      await addMemberApi(projectId, data)
    } finally {
      saving.value = false
    }
  }

  async function updateMember(projectId: string, memberId: string, data: MemberFormParams) {
    saving.value = true
    try {
      await updateMemberApi(projectId, memberId, data)
    } finally {
      saving.value = false
    }
  }

  async function removeMember(projectId: string, memberId: string) {
    saving.value = true
    try {
      await removeMemberApi(projectId, memberId)
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
