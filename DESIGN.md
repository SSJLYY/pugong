---
name: 普攻永久增加生命上限
description: 双窗修习台与多抽屉放置游戏界面
colors:
  primary: "#e8c568"
  primary-bright: "#f6dc82"
  surface-backdrop: "#0a0a0c"
  surface: "#131318"
  surface-raised: "#1a1a21"
  text: "#eae7df"
  text-muted: "#9a978c"
  danger: "#e05555"
  success: "#5cb87e"
  info: "#5b8dd9"
typography:
  display:
    fontFamily: "PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "26px"
    fontWeight: 900
    lineHeight: 1
    letterSpacing: "0.02em"
  title:
    fontFamily: "PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "14px"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "0.01em"
  body:
    fontFamily: "PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "0em"
  label:
    fontFamily: "PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "0.01em"
rounded:
  sm: "8px"
  md: "9px"
  lg: "12px"
  xl: "18px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
components:
  drawer-surface:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.lg}"
    padding: "{spacing.lg}"
  button-secondary:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.primary-bright}"
    rounded: "{rounded.md}"
    padding: "6px 10px"
  input-search:
    backgroundColor: "{colors.surface-backdrop}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
    padding: "7px 10px"
---

# Design System: 普攻永久增加生命上限

## Overview

**Creative North Star: "双窗修习台"**

玩家来到这里是为了长时间挂机与高频管理，不是浏览展示页。界面以战斗为常驻上下文，以当前任务为第二个工作区；地图、功能目录和设置只在需要时进入。视觉延续暗金拳圣气质，但用实底、细边界和明确层级取代无意义的堆叠。

**Key Characteristics:**
- 单屏最多两个核心模块，战斗始终保留。
- 抽屉按任务拆分，不用一个大型弹窗装下整个游戏。
- 八套主题共享相同拓扑，色彩与材料变化不破坏操作习惯。
- 手机采用底部功能坞和底部工作抽屉，而不是缩小桌面三栏。
- 纯文字与 emoji 继续服务游戏表达，不另造装饰性图标语言。

## Colors

默认玄夜主题以近黑表面承载暗金动作与关键数值，红、绿、蓝只承担危险、成功与法力等信息职责。

### Primary
- **拳金**：用于当前功能、焦点、选中态与生命上限等关键成长信息。
- **亮拳金**：用于抽屉标题和主要动作的清晰强调，不用于大面积文字铺陈。

### Neutral
- **夜幕底色**：页面与输入的最低层。
- **工作表面**：战斗、当前功能和抽屉的实底。
- **抬升表面**：标题栏、按钮与局部层级。
- **主文字与次文字**：次文字只用于辅助信息，弱文字不得降到无法阅读。

### Named Rules
**The Real-Surface Rule.** 抽屉必须使用不透明工作表面；遮罩负责弱化背景，不能依靠玻璃透视掩盖层级混乱。

## Typography

**Display Font:** PingFang SC / Microsoft YaHei（系统中文无衬线回退）。
**Body Font:** 同一中文工作字体，保证长时间挂机时的快速扫读。
**Numeric:** 关键数值使用 tabular-nums，避免实时跳动造成横向抖动。

### Hierarchy
- **Display**（900，26px，行高 1）：生命上限等核心大数字，使用实色。
- **Headline**（700，17px，行高 1.3）：抽屉标题。
- **Title**（700，14px，行高 1.3）：当前工作区与分组标题。
- **Body**（400，13px，行高 1.6）：游戏内容与操作说明。
- **Label**（600，12px，行高 1.4）：按钮、状态和辅助标签。

### Named Rules
**The Scan-First Rule.** 字号层级服务扫读，不用渐变文字、过度字距或装饰字体制造虚假层次。

## Layout

桌面在 900px 以上采用固定双窗：左侧战斗、右侧当前功能；内容超出时在各自工作区滚动。最大内容宽度 1560px，1180px 以下隐藏次要 HUD 筹码，1500px 以上增加双窗留白而非新增列。矮视口压缩顶栏和战斗区间距。

手机在 900px 以下仅常驻战斗；五入口底部功能坞负责战斗、装备、委托、地图和全部功能。当前功能以最高 82dvh 的底部抽屉出现，地图、目录与设置使用侧抽屉。480px 以下地图区域和已装备列表改单列，主要动作改全宽。

### Named Rules
**The Two-Window Rule.** 任一视口最多常驻两个核心模块；新增功能先进入对应抽屉，不得直接增加主屏列数。

## Elevation & Depth

深度由实底、1px 边界、遮罩和带偏移的柔和阴影共同构成。主工作区保持安静，抽屉打开时才提高层级；不用零偏移彩色光晕替代结构。

### Shadow Vocabulary
- **工作区阴影**（`0 8px 24px rgba(0,0,0,.20)`）：当前功能的轻微分离。
- **右抽屉阴影**（`-12px 0 40px rgba(0,0,0,.36)`）：右侧地图与设置。
- **左抽屉阴影**（`12px 0 40px rgba(0,0,0,.36)`）：左侧功能目录。
- **移动工作抽屉**（`0 -16px 44px rgba(0,0,0,.42)`）：从底部抬升的当前任务。

## Shapes

工作区以 12px 圆角为主，按钮以 8–9px 小圆角为主，手机底部抽屉以 18px 顶角表达抬升。边界使用 1px 细线，标题只用 1px 标识线；不使用粗重色边、硬偏移阴影或连续嵌套卡片制造层级。

## Components

### Buttons
- 普通动作使用工作表面、细边界与 8–9px 圆角；选中时提升为拳金文字和底边标识。
- hover 只改变表面、边界与少量位移；active 回落 1px；disabled 同时降低饱和度并保留可辨识文字。
- 键盘焦点统一为 2px 亮拳金外环，偏移 3px，不能只依赖 hover。

### Cards / Containers
- 战斗与当前功能是同一层级的工作表面，不再叠加第三列。
- 区域卡在桌面抽屉双列、手机窄屏单列；当前区域以拳金边界表示。
- 功能区段优先用间距和分隔线，不为每组内容再套一张卡。

### Inputs / Fields
- 搜索框使用夜幕底色、主文字和至少 36px 高度。
- 占位文字使用可读的次文字色；光标、文本选择和滚动条继承主题。

### Navigation
- 功能目录使用双列大号页签，支持方向键、Home/End、aria-selected 与焦点循环。
- 手机功能坞固定五入口，当前工作区与战斗态互斥高亮。
- M 打开地图、N 打开功能目录、Esc 返回；原游戏快捷键保持有效。

### Drawers
- 目录从左侧进入，地图与设置从右侧进入，当前功能在手机上从底部进入。
- 同一时刻只展开一个侧抽屉；支持遮罩、Esc、焦点恢复、inert 与手机手势关闭。
- 抽屉过渡为 260ms 指数缓出；静态模式和系统减弱动效偏好下取消过渡。

### Settings
- 主题、字号、动效、音效与帮助按任务分组，音效按钮不再悬浮遮挡日志。
- 八套主题只改变色彩与材料，不改变抽屉位置、导航模型和操作语义。

## Do's and Don'ts

### Do:
- **Do** 让战斗持续可见，并把新增管理功能归入对应抽屉。
- **Do** 在桌面和手机上分别使用自然的双窗与底部抽屉形式。
- **Do** 用实底、细边界、可读文字和键盘焦点建立清晰层级。
- **Do** 在每次静态资源变更时统一提升全部 `?v=` 版本。

### Don't:
- **Don't** 把地图、功能目录或设置重新塞回主屏形成三列。
- **Don't** 用渐变文字、过度辉光、玻璃透视或自动滚动公告作为新组件规范。
- **Don't** 让悬浮按钮覆盖底部功能坞、战斗日志或抽屉操作。
- **Don't** 为视觉改造复制游戏数值逻辑或绕过既有 API。
