<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  BankOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/modules/auth'
import type { LoginParams } from '@/types/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const formState = reactive<LoginParams>({
  username: '',
  password: '',
  remember: true,
})

const scheduleDays = [
  '15',
  '16',
  '17',
  '18',
  '19',
  '20',
  '21',
  '22',
  '23',
  '24',
  '25',
  '26',
  '27',
  '28',
  '29',
  '30',
  '31',
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
]

const projectRows = [
  { name: '北京城市副中心综合体项目', status: 'green', task: '基坑支护施工', start: 14, width: 41, progress: '65%' },
  { name: '上海国际金融中心项目', status: 'green', task: '主体结构施工', start: 27, width: 35, progress: '42%' },
  { name: '深圳湾科技生态园项目', status: 'green', task: '幕墙工程施工', start: 38, width: 20, progress: '28%' },
  { name: '成都天府新区产业园项目', status: 'orange', task: '机电安装施工', start: 41, width: 47, progress: '60%' },
  { name: '杭州亚运村配套工程项目', status: 'blue', task: '装饰装修施工', start: 49, width: 33, progress: '15%' },
]

const costCards = [
  { icon: DollarOutlined, label: '合同总额（万元）', value: '1,245,678.90', extra: '' },
  { icon: FileDoneOutlined, label: '已完工程量（万元）', value: '532,368.45', extra: '42.76%' },
  { icon: BankOutlined, label: '预计总成本（万元）', value: '1,189,543.21', extra: '95.49%' },
  { icon: DollarOutlined, label: '成本偏差（万元）', value: '56,135.69', extra: '↓ 4.51%' },
]

const riskItems = [
  { name: '基坑降水超限风险', level: '高风险', project: '北京城市副中心...', time: '05-20 09:30' },
  { name: '钢材价格上涨风险', level: '中风险', project: '上海国际金融中...', time: '05-20 08:45' },
  { name: '塔吊设备安全风险', level: '高风险', project: '深圳湾科技生态...', time: '05-19 17:20' },
  { name: '劳务人员短缺风险', level: '中风险', project: '成都天府新区产...', time: '05-19 16:10' },
  { name: '材料供应延误风险', level: '低风险', project: '杭州亚运村配套...', time: '05-19 14:50' },
]

const approvalRows = [
  ['施工方案审批-地下室底板施工方案', '张伟', '北京城市副中心综合体项目', '05-20 09:15', '项目总工审批'],
  ['材料采购申请-钢筋采购计划', '李明', '上海国际金融中心项目', '05-20 08:50', '成本负责人审批'],
  ['设计变更申请-幕墙立面调整', '王强', '深圳湾科技生态园项目', '05-19 17:40', '设计负责人审批'],
  ['分包结算申请-机电安装工程', '赵磊', '成都天府新区产业园项目', '05-19 16:30', '商务负责人审批'],
]

const overviewItems = [
  { icon: BankOutlined, label: '在建项目', value: '26 个' },
  { icon: FileDoneOutlined, label: '合同总额', value: '245.67 亿元' },
  { icon: TeamOutlined, label: '项目人员', value: '5,432 人' },
  { icon: SafetyCertificateOutlined, label: '安全生产', value: '良好', accent: 'green' },
  { icon: CheckCircleOutlined, label: '质量合格率', value: '96.35 %' },
  { icon: DollarOutlined, label: '本月产值', value: '18.76 亿元' },
]

async function handleSubmit() {
  if (!formState.username || !formState.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    // 接入真实后端登录接口
    // tokens are now HttpOnly cookies set by the backend — frontend only stores userInfo
    const result = await login(formState)
    userStore.setUserInfo(result.userInfo)

    message.success('登录成功')
    router.push(normalizeRedirect(route.query.redirect))
  } catch (err) {
    message.error(err instanceof Error ? err.message : '登录失败，请重试')
  } finally {
    loading.value = false
  }
}

function normalizeRedirect(value: unknown) {
  const redirect = Array.isArray(value) ? value[0] : value
  if (typeof redirect !== 'string') {
    return '/'
  }
  if (!redirect.startsWith('/') || redirect.startsWith('//')) {
    return '/'
  }
  return redirect
}

function handleForgotPassword() {
  message.info('请联系系统管理员重置密码')
}
</script>

<template>
  <div class="login-page">
    <header class="login-topbar">
      <div class="brand-lockup">
        <div class="brand-mark" aria-hidden="true">
          <AppstoreOutlined />
        </div>
        <strong class="brand-code">CGC-PMS</strong>
        <span class="brand-divider"></span>
        <span class="brand-subtitle">工程项目指挥中心</span>
      </div>
    </header>

    <main class="login-shell">
      <section class="command-board" aria-label="项目指挥中心态势预览">
        <div class="panel schedule-panel">
          <div class="panel-head">
            <div>
              <h2>项目进度总览</h2>
              <span>2024年05月20日</span>
            </div>
            <div class="schedule-tools">
              <a-select value="全部项目" size="small" class="project-select">
                <a-select-option value="全部项目">全部项目</a-select-option>
              </a-select>
              <a-segmented size="small" :options="['周', '月', '季']" value="月" />
              <a-button size="small">
                <CalendarOutlined />
                今天
              </a-button>
            </div>
          </div>
          <div class="timeline">
            <div class="timeline-title">项目名称</div>
            <div class="timeline-month">2024年5月</div>
            <div class="timeline-month">2024年6月</div>
            <template v-for="day in scheduleDays" :key="day">
              <span class="timeline-day">{{ day }}</span>
            </template>
          </div>
          <div class="schedule-list">
            <div v-for="row in projectRows" :key="row.name" class="schedule-row">
              <div class="project-name">
                <span class="row-arrow">›</span>
                <span class="status-dot" :class="row.status"></span>
                <span>{{ row.name }}</span>
              </div>
              <div class="bar-track">
                <span class="today-line"></span>
                <span class="progress-bar" :style="{ left: `${row.start}%`, width: `${row.width}%` }"></span>
                <span class="progress-end" :style="{ left: `${row.start + row.width}%` }"></span>
                <span class="task-label" :style="{ left: `${row.start}%` }">{{ row.task }}</span>
                <span class="progress-value" :style="{ left: `${row.start + row.width + 3}%` }">
                  {{ row.progress }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel risk-panel">
          <div class="panel-head slim">
            <h2>成本与风险看板</h2>
            <a class="panel-link">查看全部 ›</a>
          </div>
          <div class="risk-grid">
            <div class="cost-stack">
              <div v-for="card in costCards" :key="card.label" class="cost-card">
                <span class="metric-icon"><component :is="card.icon" /></span>
                <div>
                  <p>{{ card.label }}</p>
                  <strong>{{ card.value }}</strong>
                </div>
                <em>{{ card.extra }}</em>
              </div>
            </div>
            <div class="risk-ring-block">
              <h3>风险分布</h3>
              <div class="risk-ring">
                <span>38</span>
                <small>风险总数</small>
              </div>
              <div class="risk-legend">
                <span><i class="red"></i>高风险 8（21.05%）</span>
                <span><i class="orange"></i>中风险 16（42.11%）</span>
                <span><i class="blue"></i>低风险 14（36.84%）</span>
              </div>
            </div>
            <div class="risk-table">
              <h3>风险预警</h3>
              <div class="risk-head">
                <span>风险名称</span>
                <span>风险等级</span>
                <span>关联项目</span>
                <span>预警时间</span>
              </div>
              <div v-for="item in riskItems" :key="item.name" class="risk-row">
                <span>{{ item.name }}</span>
                <strong
                  :class="{
                    high: item.level === '高风险',
                    medium: item.level === '中风险',
                    low: item.level === '低风险',
                  }"
                >
                  {{ item.level }}
                </strong>
                <span>{{ item.project }}</span>
                <span>{{ item.time }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="lower-grid">
          <div class="panel approval-panel">
            <div class="panel-head slim">
              <h2>待办审批（8）</h2>
            </div>
            <div class="approval-table">
              <div class="approval-head">
                <span>审批标题</span>
                <span>申请人</span>
                <span>所属项目</span>
                <span>申请时间</span>
                <span>当前节点</span>
                <span>操作</span>
              </div>
              <div v-for="row in approvalRows" :key="row[0]" class="approval-row">
                <span>{{ row[0] }}</span>
                <span>{{ row[1] }}</span>
                <span>{{ row[2] }}</span>
                <span>{{ row[3] }}</span>
                <span>{{ row[4] }}</span>
                <a-button size="small">处理</a-button>
              </div>
            </div>
          </div>

          <div class="panel overview-panel">
            <div class="panel-head slim">
              <h2>项目概况</h2>
            </div>
            <div class="overview-grid">
              <div v-for="item in overviewItems" :key="item.label" class="overview-item">
                <span class="overview-icon" :class="item.accent"><component :is="item.icon" /></span>
                <div>
                  <p>{{ item.label }}</p>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <aside class="login-card" aria-label="系统登录">
        <div class="login-card-inner">
          <div class="login-header">
            <h1>欢迎登录</h1>
            <p>CGC-PMS 工程项目指挥中心</p>
          </div>

          <a-form
            :model="formState"
            layout="vertical"
            class="login-form"
            :colon="false"
            @finish="handleSubmit"
          >
            <a-form-item label="用户名" name="username">
              <a-input
                v-model:value="formState.username"
                size="large"
                placeholder="请输入用户名"
                allow-clear
              >
                <template #prefix><UserOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item label="密码" name="password">
              <a-input-password
                v-model:value="formState.password"
                size="large"
                placeholder="请输入密码"
                @press-enter="handleSubmit"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item class="extra-item">
              <div class="form-extra">
                <a-checkbox v-model:checked="formState.remember">记住用户名</a-checkbox>
                <a
                  class="forgot"
                  role="button"
                  tabindex="0"
                  @click="handleForgotPassword"
                  @keydown.enter="handleForgotPassword"
                  >忘记密码？</a
                >
              </div>
            </a-form-item>

            <a-form-item class="submit-item">
              <a-button type="primary" size="large" block html-type="submit" :loading="loading">
                登录
              </a-button>
            </a-form-item>
          </a-form>

          <div class="safe-tip">
            <SafetyCertificateOutlined />
            <div>
              <strong>安全提示</strong>
              <p>为保障系统安全，请妥善保管您的账号密码，不要与他人共享账号信息。</p>
            </div>
          </div>
        </div>
      </aside>
    </main>

  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #1d2129;
  background:
    linear-gradient(90deg, rgba(238, 246, 255, 0.96) 0%, rgba(241, 247, 255, 0.76) 48%, rgba(226, 238, 252, 0.38) 100%),
    url('@/assets/images/login-command-center.png') center / cover no-repeat;
}

.login-page::before {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(232, 242, 255, 0.18)),
    radial-gradient(circle at 30% 40%, rgba(22, 119, 255, 0.08), transparent 35%);
}

.login-topbar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  height: 68px;
  padding: 0 28px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  min-width: 0;
}

.brand-lockup {
  gap: 16px;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #fff;
  font-size: 22px;
  background: #1677ff;
  border-radius: 8px;
  box-shadow: 0 10px 26px rgba(22, 119, 255, 0.24);
}

.brand-code {
  color: #0958d9;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0;
  white-space: nowrap;
}

.brand-divider {
  width: 1px;
  height: 26px;
  background: rgba(29, 33, 41, 0.18);
}

.brand-subtitle {
  color: #101828;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(760px, 1080px) minmax(380px, 440px);
  gap: clamp(36px, 4vw, 74px);
  align-items: center;
  box-sizing: border-box;
  width: min(100%, 1440px);
  min-height: calc(100vh - 88px);
  padding: 0 28px 28px;
  margin: 0 auto;
}

.command-board {
  display: grid;
  gap: 10px;
  width: calc(100% / 0.86);
  min-width: 0;
  transform: scale(0.86);
  transform-origin: left center;
}

.panel {
  overflow: hidden;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(210, 224, 243, 0.86);
  border-radius: 6px;
  box-shadow: 0 18px 48px rgba(30, 76, 132, 0.1);
  backdrop-filter: blur(10px);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 40px;
  padding: 0 12px;
  border-bottom: 1px solid rgba(216, 227, 242, 0.9);
}

.panel-head.slim {
  min-height: 34px;
}

.panel-head h2 {
  position: relative;
  margin: 0;
  padding-left: 12px;
  color: #172033;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.panel-head h2::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 3px;
  content: '';
  background: #1677ff;
  border-radius: 2px;
}

.panel-head span {
  margin-left: 8px;
  color: #667085;
  font-size: 13px;
}

.panel-link {
  color: #1677ff;
  font-size: 13px;
}

.schedule-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-select {
  width: 150px;
}

.timeline {
  display: grid;
  grid-template-columns: 280px repeat(24, minmax(20px, 1fr));
  align-items: center;
  min-height: 42px;
  border-bottom: 1px solid rgba(216, 227, 242, 0.9);
}

.timeline-title {
  grid-row: 1 / span 2;
  height: 42px;
  padding: 14px 0 0 22px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
  border-right: 1px solid rgba(216, 227, 242, 0.9);
}

.timeline-month {
  grid-column: span 12;
  padding-top: 6px;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
}

.timeline-day {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  color: #344054;
  font-size: 12px;
}

.schedule-list {
  background: linear-gradient(90deg, transparent 0 24.8%, rgba(22, 119, 255, 0.08) 24.8% 25.1%, transparent 25.1%);
}

.schedule-row {
  display: grid;
  grid-template-columns: 280px 1fr;
  min-height: 42px;
  border-bottom: 1px solid rgba(216, 227, 242, 0.72);
}

.schedule-row:last-child {
  border-bottom: 0;
}

.project-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0 14px 0 22px;
  color: #1f2937;
  font-size: 12px;
  border-right: 1px solid rgba(216, 227, 242, 0.9);
}

.project-name span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-arrow {
  display: grid;
  width: 16px;
  height: 16px;
  place-items: center;
  color: #6b7280;
  font-size: 18px;
  line-height: 1;
  background: #f3f7fb;
  border-radius: 50%;
}

.status-dot,
.risk-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.green,
.overview-icon.green {
  color: #16a34a;
}

.status-dot.green {
  background: #18b76a;
}

.status-dot.orange {
  background: #faad14;
}

.status-dot.blue {
  background: #1677ff;
}

.bar-track {
  position: relative;
  min-width: 0;
  background-image: repeating-linear-gradient(
    90deg,
    rgba(80, 119, 169, 0.08) 0,
    rgba(80, 119, 169, 0.08) 1px,
    transparent 1px,
    transparent calc(100% / 24)
  );
}

.today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 25%;
  width: 2px;
  background: #1677ff;
}

.progress-bar {
  position: absolute;
  top: 15px;
  height: 6px;
  background: #1677ff;
  border-radius: 8px;
  box-shadow: 0 5px 14px rgba(22, 119, 255, 0.22);
}

.progress-end {
  position: absolute;
  top: 12px;
  width: 12px;
  height: 12px;
  background: #fff;
  border: 3px solid #1677ff;
  border-radius: 50%;
  transform: translateX(-50%);
}

.task-label,
.progress-value {
  position: absolute;
  top: 25px;
  color: #344054;
  font-size: 12px;
  white-space: nowrap;
}

.risk-grid {
  display: grid;
  grid-template-columns: 1fr 0.8fr 1.45fr;
  gap: 12px;
  padding: 10px 14px;
}

.cost-stack {
  display: grid;
  gap: 6px;
}

.cost-card {
  display: grid;
  grid-template-columns: 34px 1fr auto;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 6px 10px;
  background: rgba(246, 249, 254, 0.92);
  border: 1px solid rgba(225, 234, 247, 0.9);
  border-radius: 4px;
}

.metric-icon,
.overview-icon {
  display: grid;
  place-items: center;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.1);
  border-radius: 8px;
}

.metric-icon {
  width: 28px;
  height: 28px;
  font-size: 16px;
}

.cost-card p,
.overview-item p,
.safe-tip p {
  margin: 0;
  color: #667085;
}

.cost-card p,
.overview-item p {
  font-size: 12px;
}

.cost-card strong {
  display: block;
  margin-top: 2px;
  color: #111827;
  font-size: 18px;
  line-height: 1.2;
}

.cost-card em {
  color: #16a34a;
  font-size: 12px;
  font-style: normal;
}

.risk-ring-block {
  border-right: 1px solid rgba(216, 227, 242, 0.9);
  border-left: 1px solid rgba(216, 227, 242, 0.9);
}

.risk-ring-block h3,
.risk-table h3 {
  margin: 0 0 10px;
  color: #344054;
  font-size: 15px;
  font-weight: 700;
}

.risk-ring {
  display: grid;
  width: 106px;
  height: 106px;
  place-items: center;
  margin: 0 auto 8px;
  background: conic-gradient(#ff4d4f 0 21%, #faad14 21% 63%, #1677ff 63% 100%);
  border-radius: 50%;
}

.risk-ring::before {
  position: absolute;
  width: 64px;
  height: 64px;
  content: '';
  background: rgba(255, 255, 255, 0.95);
  border-radius: 50%;
}

.risk-ring span,
.risk-ring small {
  position: relative;
  z-index: 1;
  display: block;
  grid-area: 1 / 1;
  text-align: center;
}

.risk-ring span {
  margin-top: -14px;
  font-size: 24px;
  font-weight: 800;
}

.risk-ring small {
  margin-top: 26px;
  color: #667085;
  font-size: 12px;
}

.risk-legend {
  display: grid;
  gap: 4px;
  max-width: 170px;
  margin: 0 auto;
  color: #475467;
  font-size: 12px;
}

.risk-legend span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-legend .red {
  background: #ff4d4f;
}

.risk-legend .orange {
  background: #faad14;
}

.risk-legend .blue {
  background: #1677ff;
}

.risk-head,
.risk-row {
  display: grid;
  grid-template-columns: 1.2fr 0.72fr 1fr 0.82fr;
  gap: 10px;
  align-items: center;
  min-height: 26px;
  color: #475467;
  font-size: 12px;
}

.risk-head {
  min-height: 28px;
  padding: 0 8px;
  color: #667085;
  background: rgba(247, 250, 255, 0.92);
  border-bottom: 1px solid rgba(216, 227, 242, 0.9);
}

.risk-row {
  padding: 0 8px;
  border-bottom: 1px solid rgba(216, 227, 242, 0.72);
}

.risk-row span,
.approval-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-row strong {
  font-size: 12px;
  font-weight: 700;
}

.risk-row .high {
  color: #ff4d4f;
}

.risk-row .medium {
  color: #fa8c16;
}

.risk-row .low {
  color: #1677ff;
}

.lower-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}

.approval-table {
  padding: 8px 10px 10px;
}

.approval-head,
.approval-row {
  display: grid;
  grid-template-columns: 1.55fr 0.55fr 1.15fr 0.78fr 0.85fr 56px;
  gap: 10px;
  align-items: center;
  color: #475467;
  font-size: 12px;
}

.approval-head {
  min-height: 26px;
  padding: 0 8px;
  color: #667085;
  font-weight: 600;
  background: rgba(247, 250, 255, 0.92);
}

.approval-row {
  min-height: 28px;
  padding: 0 8px;
  border-bottom: 1px solid rgba(216, 227, 242, 0.72);
}

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
  padding: 14px;
}

.overview-item {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 10px;
  align-items: center;
}

.overview-icon {
  width: 27px;
  height: 27px;
  font-size: 16px;
}

.overview-item strong {
  color: #111827;
  font-size: 16px;
}

.login-card {
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card-inner {
  width: min(100%, 420px);
  padding: 46px 36px 40px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(224, 234, 248, 0.96);
  border-radius: 14px;
  box-shadow: 0 24px 70px rgba(32, 78, 135, 0.18);
  backdrop-filter: blur(16px);
}

.login-header {
  margin-bottom: 26px;
}

.login-header h1 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.2;
}

.login-header p {
  margin: 0;
  color: #111827;
  font-size: 16px;
  line-height: 1.5;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.ant-form-item-label > label) {
  height: auto;
  color: #1d2129;
  font-size: 14px;
  font-weight: 600;
}

.login-form :deep(.ant-input-affix-wrapper) {
  height: 48px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.86);
  border-color: #d5deeb;
  border-radius: 4px;
}

.login-form :deep(.ant-input-affix-wrapper-focused),
.login-form :deep(.ant-input-affix-wrapper:focus),
.login-form :deep(.ant-input-affix-wrapper:hover) {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.1);
}

.login-form :deep(.ant-input-prefix) {
  margin-right: 10px;
  color: #98a2b3;
  font-size: 18px;
}

.extra-item {
  margin-bottom: 22px !important;
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.forgot {
  color: #1677ff;
  font-size: 14px;
  white-space: nowrap;
}

.submit-item {
  margin-bottom: 26px !important;
}

.submit-item :deep(.ant-btn) {
  height: 50px;
  font-size: 18px;
  font-weight: 700;
  border-radius: 4px;
  box-shadow: 0 12px 26px rgba(22, 119, 255, 0.24);
}

.safe-tip {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 12px;
  color: #1677ff;
}

.safe-tip strong {
  display: block;
  margin-bottom: 6px;
  color: #1d2129;
  font-size: 15px;
}

.safe-tip p {
  max-width: 330px;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1500px) {
  .login-shell {
    grid-template-columns: minmax(620px, 1fr) 430px;
    gap: 44px;
  }

  .brand-subtitle {
    display: none;
  }
}

@media (max-width: 1280px) {
  .risk-grid {
    grid-template-columns: 1fr 1fr;
  }

  .risk-table {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1180px) {
  .login-topbar {
    height: auto;
    min-height: 76px;
    padding: 18px 24px 0;
  }

  .login-shell {
    grid-template-columns: 1fr;
    gap: 28px;
    padding: 24px;
  }

  .command-board {
    order: 2;
  }

  .login-card {
    order: 1;
  }

  .login-card-inner {
    width: min(100%, 560px);
    padding: 44px 36px;
  }
}

@media (max-width: 760px) {
  .login-page {
    overflow: auto;
  }

  .login-topbar {
    padding: 18px 18px 0;
  }

  .brand-lockup {
    gap: 12px;
  }

  .brand-code {
    font-size: 24px;
  }

  .brand-divider {
    display: none;
  }

  .login-shell {
    padding: 22px 18px 32px;
  }

  .command-board {
    display: none;
  }

  .login-card-inner {
    padding: 34px 24px;
    border-radius: 10px;
  }

  .login-header h1 {
    font-size: 26px;
  }

  .login-header p {
    font-size: 15px;
  }
}
</style>
