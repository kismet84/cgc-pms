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
  <div class="pj-page lg-page app-page project-target-redesign">
    <div class="pt-page-head">
      <div>
        <a-breadcrumb class="pt-breadcrumb">
          <a-breadcrumb-item>
            <router-link to="/project/list">项目管理</router-link>
          </a-breadcrumb-item>
          <a-breadcrumb-item>编辑项目</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="pt-page-title">编辑项目</div>
        <div class="pj-page-subtitle">维护项目基础资料、周期和合同金额</div>
      </div>
      <div class="pt-head-actions">
        <a-button @click="handleCancel">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
      </div>
    </div>

    <!-- Error state -->
    <div v-if="notFound && !loading" class="pt-panel pj-empty">
      <div class="pj-empty-icon">项目</div>
      <div class="pj-empty-text">项目不存在或您没有访问权限</div>
      <a-button type="primary" @click="router.push('/project/list')">返回列表</a-button>
    </div>

    <!-- Form -->
    <div v-if="!notFound">
      <a-spin :spinning="loading" tip="加载中...">
        <a-form :model="formData" layout="vertical" class="pj-edit-form">
          <section class="pt-panel pj-form-panel">
            <div class="pt-panel-header">基础信息</div>
            <div class="pt-panel-body pt-form-grid">
              <a-form-item label="项目名称" required class="span-2">
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
            </div>
          </section>

          <section class="pt-panel pj-form-panel">
            <div class="pt-panel-header">项目周期</div>
            <div class="pt-panel-body pt-form-grid">
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
            </div>
          </section>

          <section class="pt-panel pj-form-panel">
            <div class="pt-panel-header">金额信息</div>
            <div class="pt-panel-body pt-form-grid">
              <a-form-item label="合同金额(万元)">
                <a-input-number
                  v-model:value="formData.contractAmount"
                  :min="0"
                  :precision="2"
                  placeholder="请输入合同金额"
                  style="width: 100%"
                />
              </a-form-item>
            </div>
          </section>

          <section class="pt-panel pj-form-panel">
            <div class="pt-panel-header">备注与附件</div>
            <div class="pt-panel-body">
              <div class="pj-quiet-note">
                当前接口暂未提供附件字段，本页保留项目信息编辑和后续附件扩展位置。
              </div>
            </div>
          </section>
        </a-form>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.pj-page {
  background: var(--bg);
}

.pj-page-subtitle {
  margin-top: 4px;
  color: var(--muted);
  font-size: 13px;
}

.pj-form-panel {
  max-width: 920px;
  margin-bottom: 10px;
}

.pj-empty {
  padding: 60px 20px;
  text-align: center;
}

.pj-empty-icon {
  display: inline-grid;
  place-items: center;
  width: 54px;
  height: 54px;
  margin-bottom: 16px;
  color: var(--primary);
  font-weight: 800;
  background: var(--surface-subtle);
  border-radius: 50%;
  border: 1px solid var(--border);
}
.pj-quiet-note {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.pj-empty-text {
  font-size: 14px;
  color: var(--muted);
  margin-bottom: 20px;
}
</style>
