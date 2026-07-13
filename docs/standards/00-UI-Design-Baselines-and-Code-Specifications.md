UI风格的设计基准和代码参考规范文档

> 适用版本：v1.5。历史结论和旧路径仅作回溯，当前实现以本分支代码、配置和实时验证为准。

---
### 第一部分：项目列表页设计 Token 文档（唯一设计基准）

请将以下提取的视觉参数作为全局基础配置（CSS Variables 或 Tailwind Config）。

#### 1. 字体体系 (Typography)
*   **字体家族 (Font Family)**: `'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif`（企业级标准无衬线字体）。
*   **字号层级 (Font Sizes)**:
    *   `--font-size-xxl`: `24px` (统计卡片中的数值，如 "2个", "1,000.00" 等，加粗)
    *   `--font-size-xl`: `18px` (顶部面包屑/页面标题，如 "项目管理 / 项目列表")
    *   `--font-size-lg`: `16px` (卡片标题、表格行中的 "施工总承包" 标签、操作按钮文本)
    *   `--font-size-md`: `14px` (表格主体内容、输入框占位符、统计卡片的副标题)
    *   `--font-size-sm`: `12px` (表格底部的 "共 2 条"、卡片底部的辅助说明、表头极小说明)
*   **字重 (Font Weights)**:
    *   `Bold (600)`: 数据可视化数字、主要标题。
    *   `Medium (500)`: 按钮文字、表格表头。
    *   `Regular (400)`: 大部分正文。
*   **行高 (Line Height)**: 正文 `1.5`，标题 `1.2`。

#### 2. 配色方案 (Color Palette)
*   **主色 (Primary)**: `#1677FF` (品牌蓝，用于“新建项目”按钮、侧边栏选中态、环形统计图主导色)
*   **辅色/状态色**:
    *   悬停态 (Hover): `#4096FF`
    *   点击态 (Active): `#0958D9`
    *   警告/风险 (Warning/Error): `#FF4D4F` (用于右侧红框“风险项目”卡片，以及表格中的“风险”标签)
    *   成功/安全 (Success): `#52C41A` (用于右侧“无高风险项目”前面的绿点)
    *   蓝色标签背景: `#E6F4FF` (表格中“施工总承包”标签的底色)
    *   红色标签背景: `#FFF1F0` (风险卡片的背景色)
*   **中性色/背景色**:
    *   全局背景 (Body Background): `#F5F7FA` (浅灰，提供区域感)
    *   卡片/面板背景 (Card Background): `#FFFFFF` (纯白)
    *   侧边栏背景: 浅灰/白，顶部 Logo 区为深蓝 `#002C8F`。
*   **文本色 (Text)**:
    *   主文本: `#1D2129` (深灰/黑)
    *   次级文本 (表头、占位符): `#86909C`
    *   禁用文本: `#C9CDD4`
*   **边框色 (Border)**:
    *   表格线、输入框默认边框: `#E5E6EB`
    *   输入框 Focus/Active 边框: `#1677FF`
*   **阴影 (Shadows)**:
    *   卡片阴影: `box-shadow: 0px 2px 8px rgba(0, 0, 0, 0.04);`

#### 3. 布局系统 (Layout System)
*   **整体结构**: 左侧固定导航 (Flex 垂直布局) + 右侧自适应内容区 (Flex 1)。
*   **内容区排列**: 顶部面包屑 -> 搜索/筛选栏 -> 统计卡片 (Flex Grid) -> 操作栏 -> 表格区 -> 底部栏。
*   **间距标准 (Spacing)**:
    *   基础间距 (Base): `4px`
    *   `--spacing-xs`: `8px` (图标与文字的间距，表格单元格内边距)
    *   `--spacing-sm`: `12px` (输入框内部的垂直间距)
    *   `--spacing-md`: `16px` (卡片之间的间距，表格列头之间的间距)
    *   `--spacing-lg`: `24px` (卡片内边距，右侧面板与主列表的间距)

#### 4. 组件样式库 (Component Styles)
*   **按钮**:
    *   主要按钮 (Primary): 蓝色填充，白色文字，圆角 `4px`，内边距 `0 16px`，高度 `32px`。
    *   默认按钮 (Default): 白色背景，灰色边框/文字，圆角 `4px`。
    *   图标按钮: 仅有图标或带文字，行高与高度需对齐。
*   **表格 (Table)**:
    *   头部背景: `#FAFAFA` 或 `#F5F7FA`。
    *   行悬停态 (Row Hover): `#F5F7FA`。
    *   操作列: 使用纯文字链接（查看、编辑、删除），无下划线，点击/悬停变蓝。
*   **表单输入框 (Input)**:
    *   高度: `32px`，带搜索图标 (Left Icon)。
    *   圆角: `4px`。
    *   占位符颜色: `#C9CDD4`。
*   **统计卡片 (Statistics Card)**:
    *   图标居左，数字与文本垂直分布。
    *   右上角（如风险卡片）带有红色的 `-` 或折线趋势图标。

---

### 第二部分：其他页面的差异点比对与修改方案清单

您需要将当前应用中的**所有其他页面**（成本管理、采购与库存、分包管理等）与上述设计 Token 逐一比对，检查以下 **4 个关键差异点**并修改：

1.  **【文案与语体系】**：将所有非页面独有的报错提示（如“接口请求失败”）、空状态（如“暂无数据”）、弹窗按钮（“确认”、“取消”）全部替换为图中的中文习惯（力求简洁，语气中性，无英文混排）。
2.  **【颜色与阴影异动】**：检查其他页面是否有随意使用自己定义的颜色（如其他颜色蓝、绿、红，而非设计的 `#1677FF` 或 `#FF4D4F`）；确保所有卡片、弹窗、气泡的阴影程度统一。
3.  **【统计卡片与表格尺寸】**：如果其他页面包含统计卡片，强制统一使用此处提取的 **5 列/4 列弹性网格布局**，并确保统计数字 `xx个` 或 `xx万元` 的字体、字号、对齐方式完全一致。表格的**行高**、**字体大小**、**边框颜色**必须直接引用设计 Token。
4.  **【侧边栏激活与行高】**：检查“项目列表”页当前的蓝色左侧菜单激活背景色，必须应用到所有侧边栏的选中所属父级和子级菜单中。

---

### 第三部分：可直接使用的代码调整方案 (CSS / Tailwind)

请将以下代码整合进项目的全局 CSS 或 Tailwind 配置文件中。

#### 1. 全局 CSS 变量 (`global.css`)
```css
:root {
  /* 色彩体系 */
  --color-primary: #1677FF;
  --color-primary-hover: #4096FF;
  --color-primary-active: #0958D9;
  --color-success: #52C41A;
  --color-warning: #FAAD14;
  --color-error: #FF4D4F;
  --color-text-main: #1D2129;
  --color-text-secondary: #4E5969;
  --color-text-placeholder: #86909C;
  --color-border: #E5E6EB;
  --color-bg-body: #F5F7FA;
  --color-bg-card: #FFFFFF;
  --color-bg-table-header: #FAFAFA;
  --color-bg-hover: #F5F7FA;
  --color-tag-blue-bg: #E6F4FF;
  --color-tag-blue-text: #1677FF;

  /* 字体体系 */
  --font-family: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 14px;
  --font-size-base: 16px;
  --font-size-lg: 18px;
  --font-size-xl: 24px;

  /* 间距体系 */
  --spacing-xs: 8px;
  --spacing-sm: 12px;
  --spacing-md: 16px;
  --spacing-lg: 24px;

  /* 边框与阴影 */
  --border-radius-base: 4px;
  --shadow-card: 0px 2px 8px rgba(0, 0, 0, 0.04);
}
```

#### 2. Tailwind 配置 (`tailwind.config.js`) —— 如果您使用 Vue/React + Tailwind
```javascript
module.exports = {
  theme: {
    extend: {
      colors: {
        primary: '#1677FF',
        'primary-hover': '#4096FF',
        'bg-body': '#F5F7FA',
        'text-main': '#1D2129',
        'text-secondary': '#4E5969',
        'text-placeholder': '#86909C',
        'border-color': '#E5E6EB',
        'tag-blue': { bg: '#E6F4FF', text: '#1677FF' },
      },
      fontFamily: {
        sans: ['PingFang SC', 'Microsoft YaHei', 'sans-serif'],
      },
      fontSize: {
        'xs': ['12px', { lineHeight: '1.5' }], // 辅助文字
        'sm': ['14px', { lineHeight: '1.5' }], // 正文
        'base': ['16px', { lineHeight: '1.2' }], // 卡片标题
        'xl': ['24px', { lineHeight: '1.2' }], // 统计数字
      },
      spacing: {
        '4': '4px',
        '8': '8px', // 间距xs
        '12': '12px',
        '16': '16px', // 间距md
        '24': '24px', // 间距lg
        '32': '32px', // 按钮/输入框高度
      },
      boxShadow: {
        'card': '0px 2px 8px rgba(0, 0, 0, 0.04)',
      },
      borderRadius: {
        'DEFAULT': '4px',
      }
    },
  },
}
```

#### 3. 针对表格中“施工总承包”标签的 CSS 类
```css
/* 表格中的状态/类型标签，需对齐高度和圆角 */
.table-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  height: 20px; /* 视觉高度统一 */
  border-radius: 4px; /* 非胶囊形，4px直角圆角 */
  font-size: 12px;
  font-weight: 500;
  background-color: var(--color-tag-blue-bg);
  color: var(--color-tag-blue-text);
}
```

#### 4. 卡片与表格的组件代码参考 (Vue/React 伪代码)
主要操作栏（对应图中的“新建项目”、“刷新”、“列设置”）：
```html
<div class="flex justify-between items-center mb-4">
  <div class="flex gap-2">
    <button class="bg-primary text-white px-4 py-1.5 rounded flex items-center gap-1 text-sm">
      + 新建项目
    </button>
    <button class="bg-white border border-border-color text-text-main px-3 py-1.5 rounded flex items-center gap-1 text-sm">
      <icon-refresh /> 刷新
    </button>
    <button class="bg-white border border-border-color text-text-main px-3 py-1.5 rounded flex items-center gap-1 text-sm">
      <icon-setting /> 列设置
    </button>
  </div>
</div>
```

---

### 第四步：给您的下一步行动建议（手动提取引导）
由于我无法访问您的源代码，请您督促前端开发团队执行以下操作：
1.  **建立文件结构**：在项目根目录的全局样式文件夹中，创建一个名为 `design-tokens.css` 或 `tailwind.config.js` 的文件，**完整复制**第三部分的代码。
2.  **清理污染代码**：全局搜索代码中带有硬编码颜色（如 `#666`, `#eee`, `#3182ce`）和硬编码大小（如 `height: 30px`）的 CSS/JSX 代码，统一替换为上述的 `var(--color-x)` 或 Tailwind 类名。
3.  **照镜子测试**：挑选 1 个非“项目列表”的页面（如“成本管理”），应用上述样式规范。截图发回给团队评审，查看统计数字、按钮、输入框的尺寸是否 **100% 像素级** 对齐项目列表页。若对齐，则批量推广到所有功能模块。