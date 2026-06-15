import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import { createPinia } from 'pinia'
import dayjs from 'dayjs'
import localeData from 'dayjs/plugin/localeData'
import 'dayjs/locale/zh-cn'

dayjs.extend(localeData)

// VxeTable
import VxeUITable from 'vxe-table'
import VxeUIAll from 'vxe-pc-ui'
import 'vxe-table/lib/style.css'
import 'vxe-pc-ui/lib/style.css'

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
app.use(Antd)
app.directive('loading', vLoading)
app.use(VxeUIAll)
app.use(VxeUITable)

app.mount('#app')
