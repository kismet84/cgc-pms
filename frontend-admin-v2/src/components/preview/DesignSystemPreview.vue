<script setup lang="ts">
import { ref } from 'vue'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import {
  V2ActionMenu,
  V2Alert,
  V2Badge,
  V2Button,
  V2Card,
  V2Cluster,
  V2ConfirmDialog,
  V2Dialog,
  V2ErrorBoundary,
  V2Input,
  V2PageState,
  V2Select,
  V2Skeleton,
  V2Stack,
  V2ToastHost,
  showToast,
  type V2SelectOption,
} from '@/components'

const dialogOpen = ref(true)
const confirmOpen = ref(false)
const standardOpen = ref(false)
const wideOpen = ref(false)
const sheetOpen = ref(false)
const keyword = ref('中建国际金融中心项目')
const emptyKeyword = ref('')
const period = ref('2026-07')
const optionalPeriod = ref('')
const options: V2SelectOption[] = [
  { value: '2026-07', label: '2026年7月' },
  { value: '2026-06', label: '2026年6月' },
  { value: '2026-05', label: '2026年5月', disabled: true },
]

const domains = [
  { id: 'workbench', label: '工作台' },
  { id: 'delivery', label: '项目履约' },
  { id: 'commercial', label: '商务合约' },
  { id: 'supply', label: '供应链与物资' },
  { id: 'subcontract-settlement', label: '分包与结算' },
  { id: 'finance', label: '资金财务' },
  { id: 'master-data', label: '基础资料' },
  { id: 'system-management', label: '系统管理' },
]

function exportApprovalPreview() {
  showToast('info', '导出任务已创建', '本页仅展示视觉效果，不会生成真实文件。')
}

function confirmApprovalPreview() {
  showToast('success', '审批详情已确认', '本页不会执行任何业务写入。')
  dialogOpen.value = false
}
</script>

<template>
  <main id="shell-main-content" class="preview-page">
    <header class="preview-header">
      <div>
        <p>CGC-PMS / CLEAN-ROOM V2</p>
        <h1>设计系统单一预览基线</h1>
      </div>
      <V2Badge tone="success" dot>唯一预览入口</V2Badge>
    </header>

    <V2Stack :gap="4">
      <V2Alert title="视觉源已锁定" tone="info">
        本页集中展示 V2 全局令牌、共享结构与 V3 弹窗基线；仅使用模拟数据，不执行任何业务写入。
      </V2Alert>

      <nav class="preview-nav" aria-label="设计系统章节">
        <ul>
          <li><a href="#foundations">基础令牌</a></li>
          <li><a href="#page-data">页面与数据</a></li>
          <li><a href="#actions">操作与导航</a></li>
          <li><a href="#forms-feedback">表单与反馈</a></li>
          <li><a href="#dialogs">弹窗规格</a></li>
        </ul>
      </nav>

      <div id="foundations" class="preview-anchor">
        <V2Card
          title="基础令牌"
          subtitle="排版、颜色、间距和圆角均来自 tokens.css，不在业务页面重新定义"
          title-id="preview-foundations-title"
        >
          <div class="preview-grid preview-grid--foundations">
            <section aria-labelledby="preview-type-title">
              <h3 id="preview-type-title" class="preview-subtitle">文字层级</h3>
              <div class="preview-type-scale">
                <p class="preview-type-scale__display">经营驾驶舱</p>
                <p class="preview-type-scale__page">项目台账</p>
                <p class="preview-type-scale__section">合同执行概况</p>
                <p class="preview-type-scale__body">用于业务事实、表格和表单的标准正文。</p>
                <p class="preview-type-scale__caption">辅助说明、时间和补充口径</p>
              </div>
            </section>
            <section aria-labelledby="preview-color-title">
              <h3 id="preview-color-title" class="preview-subtitle">语义颜色</h3>
              <ul class="preview-swatches">
                <li><span class="preview-swatch preview-swatch--surface"></span>基础表面</li>
                <li><span class="preview-swatch preview-swatch--primary"></span>主要操作</li>
                <li><span class="preview-swatch preview-swatch--info"></span>信息</li>
                <li><span class="preview-swatch preview-swatch--success"></span>成功</li>
                <li><span class="preview-swatch preview-swatch--warning"></span>关注</li>
                <li><span class="preview-swatch preview-swatch--danger"></span>危险</li>
              </ul>
            </section>
          </div>
        </V2Card>
      </div>

      <div id="page-data" class="preview-anchor">
        <V2Stack :gap="4">
          <V2Card
            title="项目台账"
            subtitle="页面标题、查询控件和主要操作共享同一标题栏"
            :heading-level="1"
            title-id="preview-page-heading-title"
          >
            <template #title-extra><V2Badge tone="neutral">页面标题栏</V2Badge></template>
            <template #actions>
              <V2Input
                model-value=""
                type="search"
                label="项目关键词"
                hide-label
                placeholder="项目编号或名称"
              />
              <V2Select
                model-value=""
                label="项目状态"
                hide-label
                allow-empty
                :options="[
                  { value: 'ACTIVE', label: '进行中' },
                  { value: 'CLOSED', label: '已关闭' },
                ]"
                placeholder="全部状态"
              />
              <V2Button size="small">查询</V2Button>
              <V2Button size="small" variant="secondary">新建项目</V2Button>
            </template>
          </V2Card>

          <V2Card title="台账指标与表格" subtitle="KPI、状态、金额、换行及操作列统一结构">
            <dl class="v2-ledger-kpis" aria-label="项目汇总">
              <div>
                <dt>项目数量</dt>
                <dd>28</dd>
              </div>
              <div>
                <dt>合同金额</dt>
                <dd>¥ 86,520,000.00</dd>
              </div>
              <div>
                <dt>执行中</dt>
                <dd>19</dd>
              </div>
              <div>
                <dt>需关注</dt>
                <dd>3</dd>
              </div>
            </dl>
            <div class="preview-table-wrap" role="region" aria-label="项目台账示例" tabindex="0">
              <table class="v2-table--top">
                <caption class="v2-visually-hidden">
                  项目台账共享表格示例
                </caption>
                <thead>
                  <tr>
                    <th scope="col">项目编号</th>
                    <th scope="col">项目名称</th>
                    <th scope="col">状态</th>
                    <th scope="col" class="v2-table-cell--numeric">合同金额</th>
                    <th scope="col" class="v2-table-cell--actions">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>
                      <button type="button" class="v2-table__record-link">XM-20260718-001</button>
                    </td>
                    <td class="v2-table-cell--wrap">CGC-PMS 全业务闭环演示项目</td>
                    <td class="v2-table-cell--status"><V2Badge tone="success">进行中</V2Badge></td>
                    <td class="v2-table-cell--numeric">¥ 52,000,000.00</td>
                    <td class="v2-table-cell--actions">
                      <V2ActionMenu label="项目操作">
                        <V2Button size="small" variant="ghost">查看</V2Button>
                        <V2Button size="small" variant="ghost">编辑</V2Button>
                      </V2ActionMenu>
                    </td>
                  </tr>
                  <tr>
                    <td>
                      <button type="button" class="v2-table__record-link">XM-20260720-002</button>
                    </td>
                    <td class="v2-table-cell--wrap">产业园二期施工总承包项目</td>
                    <td class="v2-table-cell--status"><V2Badge tone="warning">需关注</V2Badge></td>
                    <td class="v2-table-cell--numeric">¥ 34,520,000.00</td>
                    <td class="v2-table-cell--actions">
                      <V2Button size="small" variant="ghost">查看</V2Button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </V2Card>
        </V2Stack>
      </div>

      <V2Card
        id="actions"
        class="preview-anchor"
        title="操作与导航"
        subtitle="按钮尺寸、状态徽标、操作菜单和八个业务域图标"
      >
        <V2Stack :gap="4">
          <V2Cluster :gap="2">
            <V2Button size="small">小型操作</V2Button>
            <V2Button>主要操作</V2Button>
            <V2Button size="touch" variant="secondary">触控操作</V2Button>
            <V2Button variant="ghost">文字操作</V2Button>
            <V2Button variant="danger">危险操作</V2Button>
            <V2Button loading>处理中</V2Button>
            <V2Button disabled>不可用</V2Button>
            <V2ActionMenu label="示例操作菜单" trigger-text="更多操作">
              <V2Button size="small" variant="ghost">查看详情</V2Button>
              <V2Button size="small" variant="ghost">导出记录</V2Button>
            </V2ActionMenu>
          </V2Cluster>
          <V2Cluster :gap="2">
            <V2Badge tone="neutral">草稿</V2Badge>
            <V2Badge tone="info" dot>处理中</V2Badge>
            <V2Badge tone="success" dot>正常</V2Badge>
            <V2Badge tone="warning" dot>需关注</V2Badge>
            <V2Badge tone="danger" dot>高风险</V2Badge>
          </V2Cluster>
          <ul class="preview-domains" aria-label="八个业务域图标">
            <li v-for="domain in domains" :key="domain.id">
              <DomainNavigationIcon :domain-id="domain.id" />
              <span>{{ domain.label }}</span>
            </li>
          </ul>
        </V2Stack>
      </V2Card>

      <div id="forms-feedback" class="preview-anchor">
        <div class="preview-grid preview-grid--forms">
          <V2Card title="表单控件" subtitle="标签、提示、校验、空值、加载和禁用状态">
            <V2Stack :gap="4">
              <V2Input v-model="keyword" label="当前项目" hint="支持项目名称或编号" />
              <V2Input
                model-value=""
                type="search"
                label="列表搜索"
                hide-label
                placeholder="隐藏标签仍保留无障碍名称"
              />
              <V2Select v-model="period" label="报告期" required :options="options" />
              <V2Select
                v-model="optionalPeriod"
                label="可选报告期"
                allow-empty
                hint="允许清空选择"
                :options="options"
              />
              <V2Input v-model="emptyKeyword" label="校验示例" error="请输入有效内容" required />
              <V2Input model-value="正在加载" label="加载状态" loading />
              <V2Input model-value="无权修改" label="禁用状态" disabled />
              <V2Select model-value="" label="禁用下拉框" disabled :options="options" />
            </V2Stack>
          </V2Card>

          <V2Card title="反馈与骨架" subtitle="语义提示、Toast 和异步加载状态">
            <V2Stack :gap="3">
              <V2Alert title="信息提示" tone="info">用于解释当前上下文。</V2Alert>
              <V2Alert title="保存成功" tone="success">变更已进入当前 V2 会话。</V2Alert>
              <V2Alert title="需要关注" tone="warning">请检查截止日期。</V2Alert>
              <V2Alert title="存在校验错误" tone="danger">请检查必填项后重试。</V2Alert>
              <V2Cluster :gap="2">
                <V2Button @click="showToast('success', '操作成功', '模拟数据已保存')"
                  >成功提示</V2Button
                >
                <V2Button variant="secondary" @click="showToast('info', '提示信息', '新版本已可用')"
                  >信息提示</V2Button
                >
                <V2Button variant="secondary" @click="showToast('warn', '注意', '报告期即将截止')"
                  >警告提示</V2Button
                >
                <V2Button variant="danger" @click="showToast('error', '错误', '模拟网络中断')"
                  >错误提示</V2Button
                >
              </V2Cluster>
              <V2Skeleton variant="rect" label="面板加载中" />
              <V2Cluster :gap="3">
                <V2Skeleton variant="circle" label="头像加载中" />
                <V2Stack :gap="2" class="preview-skeleton-copy">
                  <V2Skeleton label="标题加载中" />
                  <V2Skeleton label="说明加载中" />
                </V2Stack>
              </V2Cluster>
            </V2Stack>
          </V2Card>
        </div>
      </div>

      <V2ErrorBoundary>
        <div class="preview-grid preview-grid--states">
          <V2PageState
            kind="empty"
            title="暂无组件数据"
            description="空状态提供下一步建议。"
            :heading-level="2"
          >
            <template #actions><V2Button variant="secondary">刷新状态</V2Button></template>
          </V2PageState>
          <V2PageState
            kind="loading"
            title="正在读取数据"
            description="保留页面结构，避免布局跳动。"
            :heading-level="2"
          />
          <V2PageState
            kind="error"
            code="DATA-001"
            title="数据读取失败"
            description="说明原因并提供恢复入口。"
            :heading-level="2"
          >
            <template #actions><V2Button variant="secondary">重新加载</V2Button></template>
          </V2PageState>
        </div>
      </V2ErrorBoundary>

      <div id="dialogs" class="preview-anchor">
        <V2Card
          title="弹窗规格"
          subtitle="标准表单、V3详情、宽详情、底部抽屉和确认框共用 V2Dialog 行为"
        >
          <V2Cluster :gap="2">
            <V2Button variant="secondary" @click="standardOpen = true">标准表单</V2Button>
            <V2Button variant="secondary" @click="dialogOpen = true">V3审批详情</V2Button>
            <V2Button variant="secondary" @click="wideOpen = true">宽详情</V2Button>
            <V2Button variant="secondary" @click="sheetOpen = true">底部抽屉</V2Button>
            <V2Button variant="danger" @click="confirmOpen = true">危险确认</V2Button>
          </V2Cluster>
        </V2Card>
      </div>
    </V2Stack>

    <V2Dialog
      v-model:open="standardOpen"
      title="新建演示记录"
      description="标准编辑弹窗只能通过显式操作关闭。"
      panel-class="v2-dialog-standard"
      :close-on-backdrop="false"
    >
      <V2Stack :gap="4">
        <V2Input model-value="DS-20260726-001" label="记录编号" required />
        <V2Input model-value="设计系统演示记录" label="记录名称" required />
        <V2Select model-value="2026-07" label="报告期" :options="options" />
      </V2Stack>
      <template #footer>
        <V2Button variant="secondary" @click="standardOpen = false">取消</V2Button>
        <V2Button @click="standardOpen = false">保存</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="wideOpen"
      title="宽详情演示"
      description="用于字段较多或包含宽表格的只读详情。"
      panel-class="v2-dialog-wide"
    >
      <dl class="v2-detail-dialog__facts">
        <div>
          <dt>业务编号</dt>
          <dd>WIDE-20260726-001</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd class="v2-detail-dialog__accent">已确认</dd>
        </div>
        <div>
          <dt>项目</dt>
          <dd>CGC-PMS 全业务闭环演示项目</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>2026-07-26 17:20</dd>
        </div>
      </dl>
      <template #footer>
        <V2Button variant="secondary" @click="wideOpen = false">关闭</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="sheetOpen"
      title="底部抽屉演示"
      description="移动端和临时上下文操作使用底部对齐形态。"
      panel-class="v2-dialog-bottom-sheet"
    >
      <V2Alert title="只读演示" tone="info">关闭后不会改变任何业务数据。</V2Alert>
      <template #footer>
        <V2Button variant="secondary" @click="sheetOpen = false">关闭</V2Button>
      </template>
    </V2Dialog>

    <V2Dialog
      v-model:open="dialogOpen"
      title="审批详情"
      description="您可以查看审批的详细信息及审批流程记录。"
      panel-class="v2-detail-dialog"
    >
      <template #title-suffix>
        <V2Badge tone="success" dot>审批通过</V2Badge>
      </template>

      <dl class="v2-detail-dialog__facts">
        <div>
          <dt>审批单号</dt>
          <dd>AP202405240001</dd>
        </div>
        <div>
          <dt>申请日期</dt>
          <dd>2024-05-24 10:30:45</dd>
        </div>
        <div>
          <dt>审批类型</dt>
          <dd>费用报销</dd>
        </div>
        <div>
          <dt>审批状态</dt>
          <dd class="v2-detail-dialog__accent">审批通过</dd>
        </div>
        <div>
          <dt>申请人</dt>
          <dd>张晓明</dd>
        </div>
        <div>
          <dt>当前节点</dt>
          <dd>流程已结束</dd>
        </div>
        <div>
          <dt>申请部门</dt>
          <dd>产品设计部</dd>
        </div>
        <div>
          <dt>备注</dt>
          <dd>出差拜访客户，产生交通及住宿费用</dd>
        </div>
      </dl>

      <section class="v2-detail-dialog__section" aria-labelledby="previewExpenseTitle">
        <div class="v2-detail-dialog__section-heading">
          <h3 id="previewExpenseTitle">费用明细</h3>
          <V2Badge tone="info" dot>3笔</V2Badge>
        </div>
        <div class="v2-detail-dialog__table" role="region" aria-label="费用明细表格" tabindex="0">
          <table>
            <thead>
              <tr>
                <th scope="col">费用类别</th>
                <th scope="col">费用说明</th>
                <th scope="col">发生日期</th>
                <th scope="col">金额（元）</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>交通费用</td>
                <td>上海－北京 往返机票</td>
                <td>2024-05-20</td>
                <td>¥ 1,620.00</td>
              </tr>
              <tr>
                <td>住宿费用</td>
                <td>北京商务酒店住宿2晚</td>
                <td>2024-05-20～2024-05-21</td>
                <td>¥ 960.00</td>
              </tr>
              <tr>
                <td>餐饮费用</td>
                <td>客户商务餐费</td>
                <td>2024-05-21</td>
                <td>¥ 340.00</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th scope="row" colspan="3">合计</th>
                <td class="v2-detail-dialog__accent">¥ 2,920.00</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <template #footer>
        <V2Button variant="secondary" @click="dialogOpen = false">返回列表</V2Button>
        <V2Button variant="ghost" @click="exportApprovalPreview">导出明细</V2Button>
        <V2Button @click="confirmApprovalPreview">确认</V2Button>
      </template>
    </V2Dialog>
    <V2ConfirmDialog
      :open="confirmOpen"
      title="确认演示操作"
      description="组件预览不会执行业务写入。"
      danger
      @close="confirmOpen = false"
      @confirm="confirmOpen = false"
    />
    <V2ToastHost />
  </main>
</template>

<style scoped>
.preview-page {
  width: min(var(--v2-page-max-width), 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: var(--v2-page-gutter);
}

.preview-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--v2-space-4);
  margin-bottom: var(--v2-space-5);
  padding-bottom: var(--v2-space-4);
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
}

.preview-header p,
.preview-header h1 {
  margin: 0;
}

.preview-header p {
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
  letter-spacing: 0.08em;
}

.preview-header h1 {
  margin-top: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-28);
  line-height: var(--v2-line-height-tight);
}

.preview-skeleton-copy {
  min-width: 12rem;
  flex: 1;
}

.preview-anchor {
  scroll-margin-top: calc(var(--v2-space-10) + var(--v2-control-height-md));
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, var(--preview-grid-min)), 1fr));
  gap: var(--v2-space-3);
}

.preview-grid--foundations {
  --preview-grid-min: 19rem;
  gap: var(--v2-space-4);
}

.preview-grid--forms {
  --preview-grid-min: 20rem;
}

.preview-grid--states {
  --preview-grid-min: 18rem;
}

.preview-nav {
  position: sticky;
  z-index: var(--v2-z-sticky);
  top: var(--v2-space-2);
  overflow-x: auto;
  padding: var(--v2-space-2);
  background: color-mix(in srgb, var(--v2-color-surface) 88%, transparent);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  box-shadow: var(--v2-shadow-control);
  backdrop-filter: blur(14px) saturate(140%);
}

.preview-nav ul,
.preview-swatches,
.preview-domains {
  margin: 0;
  padding: 0;
  list-style: none;
}

.preview-nav ul {
  width: max-content;
  display: flex;
  gap: var(--v2-space-2);
}

.preview-nav a {
  min-height: var(--v2-control-height-sm);
  display: inline-flex;
  align-items: center;
  padding-inline: var(--v2-space-3);
  color: var(--v2-color-text-secondary);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-semibold);
  text-decoration: none;
}

.preview-nav a:hover,
.preview-nav a:focus-visible {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.preview-subtitle {
  margin: 0 0 var(--v2-space-3);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-13);
}

.preview-type-scale p {
  margin: 0;
}

.preview-type-scale {
  display: grid;
  gap: var(--v2-space-3);
}

.preview-type-scale__display {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-28);
  font-weight: var(--v2-font-weight-bold);
  line-height: var(--v2-line-height-tight);
}

.preview-type-scale__page {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-21);
  font-weight: var(--v2-font-weight-bold);
}

.preview-type-scale__section {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-15);
  font-weight: var(--v2-font-weight-semibold);
}

.preview-type-scale__body {
  color: var(--v2-color-text);
  font-size: var(--v2-font-size-13);
  line-height: var(--v2-line-height-body);
}

.preview-type-scale__caption {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.preview-swatches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--v2-space-3);
}

.preview-swatches li {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}

.preview-swatch {
  width: var(--v2-space-8);
  height: var(--v2-space-8);
  flex: 0 0 auto;
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-sm);
}

.preview-swatch--surface {
  background: var(--v2-color-surface);
}

.preview-swatch--primary {
  background: var(--v2-color-primary);
}

.preview-swatch--info {
  background: var(--v2-color-info);
}

.preview-swatch--success {
  background: var(--v2-color-success);
}

.preview-swatch--warning {
  background: var(--v2-color-warning);
}

.preview-swatch--danger {
  background: var(--v2-color-danger);
}

.preview-table-wrap {
  min-width: 0;
  overflow-x: auto;
  border-top: var(--v2-border-width) solid var(--v2-color-border-subtle);
}

.preview-domains {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--v2-space-2);
}

.preview-domains li {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  padding: var(--v2-space-3);
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface-subtle);
  border: var(--v2-border-width) solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
}

.preview-domains svg {
  width: var(--v2-space-6);
  height: var(--v2-space-6);
  flex: 0 0 auto;
  color: var(--v2-color-primary);
}

@media (max-width: 30rem) {
  .preview-header {
    align-items: flex-start;
  }

  .preview-header h1 {
    font-size: var(--v2-font-size-21);
  }

  .preview-swatches,
  .preview-domains {
    grid-template-columns: 1fr;
  }
}
</style>
