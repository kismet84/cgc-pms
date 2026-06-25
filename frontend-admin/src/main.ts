import { createApp } from 'vue'
import { message } from 'ant-design-vue'
import { createPinia } from 'pinia'
import dayjs from 'dayjs'
import localeData from 'dayjs/plugin/localeData'
import 'dayjs/locale/zh-cn'

dayjs.extend(localeData)

// VxeTable
import VxeUITable from 'vxe-table'
import VxeLoading from 'vxe-pc-ui/es/loading'
import 'vxe-table/lib/style.css'
import 'vxe-pc-ui/es/loading/style.css'

// ECharts (按需注册)
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'

import App from './App.vue'
import router from './router'
import vLoading from './directives/loading'
import vPermission from './directives/permission'
import './assets/styles/global.css'

use([
  CanvasRenderer,
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
])

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.directive('loading', vLoading)
app.directive('permission', vPermission)
app.use(VxeLoading)
app.use(VxeUITable)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Global Error]', err)
  // TODO: 接入监控服务 (Sentry / 日志上报)
  if (err instanceof Error) {
    try {
      message.error('系统异常，请稍后重试')
    } catch {
      console.error('Error boundary caught:', err.message)
    }
  }
}

app.mount('#app')
