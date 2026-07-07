export const scheduleDays = [
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

export const projectRows = [
  {
    name: '北京城市副中心综合体项目',
    status: 'green',
    task: '基坑支护施工',
    start: 14,
    width: 41,
    progress: '65%',
  },
  {
    name: '上海国际金融中心项目',
    status: 'green',
    task: '主体结构施工',
    start: 27,
    width: 35,
    progress: '42%',
  },
  {
    name: '深圳湾科技生态园项目',
    status: 'green',
    task: '幕墙工程施工',
    start: 38,
    width: 20,
    progress: '28%',
  },
  {
    name: '成都天府新区产业园项目',
    status: 'orange',
    task: '机电安装施工',
    start: 41,
    width: 47,
    progress: '60%',
  },
  {
    name: '杭州亚运村配套工程项目',
    status: 'blue',
    task: '装饰装修施工',
    start: 49,
    width: 33,
    progress: '15%',
  },
]

export const costCards = [
  { label: '合同总额（万元）', value: '1,245,678.90', extra: '' },
  { label: '已完工程量（万元）', value: '532,368.45', extra: '42.76%' },
  { label: '预计总成本（万元）', value: '1,189,543.21', extra: '95.49%' },
  { label: '成本偏差（万元）', value: '56,135.69', extra: '↓ 4.51%' },
]

export const riskItems = [
  { name: '基坑降水超限风险', level: '高风险', project: '北京城市副中心...', time: '05-20 09:30' },
  { name: '钢材价格上涨风险', level: '中风险', project: '上海国际金融中...', time: '05-20 08:45' },
  { name: '塔吊设备安全风险', level: '高风险', project: '深圳湾科技生态...', time: '05-19 17:20' },
  { name: '劳务人员短缺风险', level: '中风险', project: '成都天府新区产...', time: '05-19 16:10' },
  { name: '材料供应延误风险', level: '低风险', project: '杭州亚运村配套...', time: '05-19 14:50' },
]

export const approvalRows = [
  [
    '施工方案审批-地下室底板施工方案',
    '张伟',
    '北京城市副中心综合体项目',
    '05-20 09:15',
    '项目总工审批',
  ],
  ['材料采购申请-钢筋采购计划', '李明', '上海国际金融中心项目', '05-20 08:50', '成本负责人审批'],
  ['设计变更申请-幕墙立面调整', '王强', '深圳湾科技生态园项目', '05-19 17:40', '设计负责人审批'],
  ['分包结算申请-机电安装工程', '赵磊', '成都天府新区产业园项目', '05-19 16:30', '商务负责人审批'],
]
