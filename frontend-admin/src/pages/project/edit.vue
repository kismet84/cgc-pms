<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getProjectDetail, updateProject } from '@/api/modules/project'
import type { ProjectVO } from '@/types/project'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

const loading = ref(false)
const saving = ref(false)
const notFound = ref(false)

const formData = reactive({
  projectName: '',
  projectType: undefined as string | undefined,
  projectAddress: '',
  ownerUnit: '',
  supervisorUnit: '',
  designUnit: '',
  contractAmount: undefined as number | undefined,
  plannedStartDate: undefined as string | undefined,
  plannedEndDate: undefined as string | undefined,
})

const projectTypeOptions = [
  { value: '施工总承包', label: '施工总承包' },
  { value: '专业分包', label: '专业分包' },
  { value: '劳务分包', label: '劳务分包' },
  { value: '材料采购', label: '材料采购' },
]

onMounted(async () => {
  loading.value = true
  try {
    const project: ProjectVO = await getProjectDetail(projectId)
    formData.projectName = project.projectName
    formData.projectType = project.projectType
    formData.projectAddress = project.projectAddress || ''
    formData.ownerUnit = project.ownerUnit || ''
    formData.supervisorUnit = project.supervisorUnit || ''
    formData.designUnit = project.designUnit || ''
    formData.contractAmount = project.contractAmount
      ? parseFloat(project.contractAmount) / 10000
      : undefined
    formData.plannedStartDate = project.plannedStartDate
    formData.plannedEndDate = project.plannedEndDate
  } catch (e: unknown) {
    console.error(e)
    notFound.value = true
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  saving.value = true
  try {
    await updateProject(projectId, {
      projectName: formData.projectName,
      projectType: formData.projectType,
      projectAddress: formData.projectAddress || undefined,
      ownerUnit: formData.ownerUnit || undefined,
      supervisorUnit: formData.supervisorUnit || undefined,
      designUnit: formData.designUnit || undefined,
      contractAmount:
        formData.contractAmount != null ? String(formData.contractAmount * 10000) : undefined,
      plannedStartDate: formData.plannedStartDate,
      plannedEndDate: formData.plannedEndDate,
    })
    message.success('项目更新成功')
    router.push('/project/list')
  } catch (e: unknown) {
    console.error(e)
    message.error('更新项目失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/project/list')
}
</script>

<template>
  <div class="pj-page">
    <a-breadcrumb class="pj-breadcrumb">
      <a-breadcrumb-item>
        <router-link to="/project/list">项目管理</router-link>
      </a-breadcrumb-item>
      <a-breadcrumb-item>编辑项目</a-breadcrumb-item>
    </a-breadcrumb>

    <a-page-header title="编辑项目" class="pj-header" />

    <!-- Error state -->
    <div v-if="notFound && !loading" class="pj-card pj-empty">
      <div class="pj-empty-icon">📋</div>
      <div class="pj-empty-text">项目不存在或您没有访问权限</div>
      <a-button type="primary" @click="router.push('/project/list')">返回列表</a-button>
    </div>

    <!-- Form -->
    <div v-if="!notFound" class="pj-card pj-form-card">
      <a-spin :spinning="loading" tip="加载中...">
        <a-form
          :model="formData"
          :label-col="{ span: 6 }"
          :wrapper-col="{ span: 14 }"
          class="pj-edit-form"
        >
          <a-form-item label="项目名称" required>
            <a-input v-model:value="formData.projectName" placeholder="请输入项目名称" />
          </a-form-item>
          <a-form-item label="项目类型" required>
            <a-select
              v-model:value="formData.projectType"
              placeholder="请选择项目类型"
              :options="projectTypeOptions"
            />
          </a-form-item>
          <a-form-item label="项目地址">
            <a-input v-model:value="formData.projectAddress" placeholder="请输入项目地址" />
          </a-form-item>
          <a-form-item label="建设单位">
            <a-input v-model:value="formData.ownerUnit" placeholder="请输入建设单位" />
          </a-form-item>
          <a-form-item label="监理单位">
            <a-input v-model:value="formData.supervisorUnit" placeholder="请输入监理单位" />
          </a-form-item>
          <a-form-item label="设计单位">
            <a-input v-model:value="formData.designUnit" placeholder="请输入设计单位" />
          </a-form-item>
          <a-form-item label="合同金额(万元)">
            <a-input-number
              v-model:value="formData.contractAmount"
              :min="0"
              :precision="2"
              placeholder="请输入合同金额"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="计划开工日期">
            <a-date-picker
              v-model:value="formData.plannedStartDate"
              placeholder="请选择计划开工日期"
              style="width: 100%"
              value-format="YYYY-MM-DD"
            />
          </a-form-item>
          <a-form-item label="计划竣工日期">
            <a-date-picker
              v-model:value="formData.plannedEndDate"
              placeholder="请选择计划竣工日期"
              style="width: 100%"
              value-format="YYYY-MM-DD"
            />
          </a-form-item>

          <a-form-item :wrapper-col="{ offset: 6, span: 14 }">
            <div class="pj-form-actions">
              <a-button type="primary" :loading="saving" @click="handleSave"> 保存 </a-button>
              <a-button @click="handleCancel">取消</a-button>
            </div>
          </a-form-item>
        </a-form>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.pj-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}

.pj-breadcrumb {
  margin-bottom: 8px;
  padding: 0 4px;
}

.pj-header {
  background: transparent;
  padding-bottom: 8px;
}

.pj-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

.pj-form-card {
  padding: 24px;
  max-width: 720px;
}

.pj-edit-form {
  padding-top: 8px;
}

.pj-form-actions {
  display: flex;
  gap: 12px;
}

.pj-empty {
  padding: 60px 20px;
  text-align: center;
}

.pj-empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.pj-empty-text {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 20px;
}
</style>
