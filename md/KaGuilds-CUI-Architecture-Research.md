# KaGuilds 箱子菜单架构研究与 Dominion 复用评估

> 研究日期：2026-07-11  
> 参考项目：`/home/plugins/KaGuilds`  
> 目标：为 Dominion 第二阶段配置化 CUI 设计确定可复用约定、禁止复制的实现和最小落地顺序。

## 1. 结论

KaGuilds 最值得借鉴的是配置表达方式：

- 1 至 6 行、每行 9 个字符的槽位布局。
- 一个字符对应一份物品显示配置，重复字符复用同一按钮外观。
- 动态列表字符占据多个槽位，槽位数量自然成为页容量。
- 同一物品可按左键、右键、Shift 左键和 Q 键配置不同动作。
- `material/name/lore/amount/custom_data/item_model` 形成直观的物品外观配置。

KaGuilds 的运行时实现不适合直接复制。它把具体业务列表类型、数据库读取、分页、物品构建、动作解析和刷新集中在两个大类中。Dominion 已有可信 route、provider、`PageSlice`、Actions、callback session 和 revision，应该只新增箱子菜单表现层与 Inventory callback transport。

## 2. KaGuilds 实际实现

### 2.1 资源结构

KaGuilds 按语言复制完整菜单：

```text
src/main/resources/gui_CN/*.yml
src/main/resources/gui_EN/*.yml
```

菜单主要字段：

```yaml
title: '&0公会列表'
update: 20
Layout:
  - '#########'
  - '111111111'
  - 'B######<>'
button:
  '1':
    type: GUILDS_LIST
    display:
      material: PAPER
      name: '&a{name}'
      lore: []
    actions:
      left:
        - 'command: kg join {id}'
```

源码兼容 `layout/layouts`、`button/buttons` 和多种 `material` 拼写，但默认资源并不完全统一，例如同时存在 `Layout` 与 `layout`。

### 2.2 打开与分派

`MenuManager.openMenu` 每次打开时直接从磁盘加载 YAML，然后遍历按钮的业务 `type`：

```text
GUILDS_LIST
MEMBERS_LIST
ALL_PLAYER
BUFF_LIST
GUILD_VAULTS
GUILD_UPGRADE
TASK_DAILY
TASK_GLOBAL
```

它根据检测到的类型进入不同 `open*Menu` 方法。普通菜单进入 `renderStandardMenu`。

`menuCache` 虽然存在，但只在 reload 时清空，实际打开路径没有使用该缓存。

### 2.3 动态列表与分页

每种动态列表都重复以下流程：

1. 扫描 layout，找出业务 `type` 对应的所有槽位。
2. 用槽位数量作为 page size。
3. 查询数据库或缓存并截取当前页。
4. 构造特定业务 ItemStack。
5. 其他字符使用普通按钮构造器。

翻页动作由监听器处理。监听器根据 `holder.menuName` 是否包含 `member/player/buff/list/task` 推测总条目数，再次扫描 layout 计算页容量。

这使配置中的数据类型、菜单文件名、渲染方法和监听器分页判断形成隐式耦合。

### 2.4 ItemStack 与条目身份

普通按钮支持：

- Bukkit `Material`。
- name、lore、amount。
- PlaceholderAPI 和插件内部变量。
- CustomModelData。
- 1.21.4+ ItemModel，失败时回退 CustomModelData。

动态条目把业务身份写入 ItemStack PDC，例如：

```text
guild_id
member_uuid
player_uuid
buff_keyname
vault_num
upgrade_level_num
```

监听器点击时从当前物品 PDC 恢复业务目标，再做字符串变量替换。

### 2.5 Holder 与事件

`GuildMenuHolder` 保存：

```text
title
layout
buttons ConfigurationSection
currentPage
menuName
Player
updateTask
Inventory
```

`MenuListener`：

- 通过 `InventoryHolder` 识别菜单。
- 点击后立即取消事件。
- 用 raw slot 映射 layout 字符。
- 根据 click type 选择动作列表。
- 关闭时取消定时刷新任务。
- 拖拽时禁止向菜单移动物品。
- 玩家退出时清理聊天输入状态。

Holder 识别 Inventory 的思路正确，但其内部保存了可变 YAML section、Player 和刷新任务，职责过多。

### 2.6 动作执行

KaGuilds 支持：

```text
tell/actionbar/title/hovertext
command/console/sound
open/close/update
PAGE_NEXT/PAGE_PREV
catcher/kamenu
wait
condition/actions/deny
```

动作列表会先转换为多个延迟任务，再分别调度。它不是严格串行 future 链：前一个动作失败、关闭或跳转不会可靠阻止已经提交的后续任务。

Dominion 已经拥有严格顺序、失败终止、workflow timeout 和 whitelist action，因此不应替换为这套执行器。

### 2.7 自动刷新与 reload

`update` 使用 Bukkit repeating task 调用 `refreshMenu(holder)`。刷新过程会直接读取数据库并逐槽重建条目，而且运行在服务器主线程。列表缩短时，没有数据的旧动态槽位不一定被显式清空。

reload 会清理默认图标缓存并关闭在线玩家当前打开的 KaGuilds 菜单，但没有先构建并验证完整的新 revision，也没有失败时保留旧 registry 的原子切换。

## 3. 可直接复用的部分

这里的“直接复用”指配置概念或 Bukkit 标准模式，不是复制 KaGuilds 类文件。

| KaGuilds 能力 | Dominion 采用方式 |
| --- | --- |
| 9 字符槽位布局 | 用于 `uis/<locale>/chest_menu/*.yml` |
| 重复 symbol 复用按钮 | 同一 symbol 渲染到所有对应槽位 |
| 动态列表占槽 | `source: entries`，槽位数作为 provider page size |
| 多点击类型 | 支持 left/right/shift-left/shift-right/middle/drop |
| material/name/lore/amount | 作为第一版物品显示字段 |
| custom model/item model | 加载期校验，按目标版本能力应用或明确降级 |
| InventoryHolder 识别 | 使用 Dominion 自有不可变 CUI holder |
| click/drag/close listener | 使用新的无业务知识 CUI listener |

## 4. 只能借鉴、不能复制的部分

### 4.1 业务列表 type

不引入 `GUILDS_LIST` 一类业务组件。Dominion 使用 `_shared.data.<source>.provider` 和现有 `MenuDataProvider`，renderer 只认识 `source`，不认识领地、成员或权限组。

### 4.2 PDC 业务参数

PDC 可用于调试标记或视觉元数据，但不能作为业务目标的唯一真相。领地 ID、成员名和权限组名继续保存在服务端 callback session 的 trusted arguments 中。

### 4.3 任意 command/console 与条件表达式

Dominion 不开放通用控制台命令、任意条件语言、`wait` 或 KaMenu 式复杂判断。CUI 只引用现有共享 operation，或使用已注册的安全 action 类型。

### 4.4 菜单名推断分页

分页只使用当前 `PageSlice` 和 layout 中 source symbol 的槽位数量，不检查文件名，也不在 listener 中重新查询业务数据。

### 4.5 定时数据库刷新

第一版不做自动刷新。`REFRESH` 或重新打开 route 时由 provider 异步加载最新数据。若未来加入定时刷新，也必须复用 request id/revision 检查，并通过 scheduler bridge 渲染 Inventory。

### 4.6 每次打开解析 YAML

菜单必须在 reload 时一次性解析、校验并生成不可变定义。reload 成功后原子替换 revision，失败继续使用旧定义。

### 4.7 大型业务 switch

不复制 KaGuilds 的 `MenuManager`/`MenuListener` 结构。CUI renderer、listener 和 item builder 中都不能出现 `DominionList/MemberList/Flags` 等业务分支。

### 4.8 版本判断与 ItemModel 反射

不复制 KaGuilds 在多个构建方法中解析 Bukkit 版本字符串并重复反射 `setItemModel` 的做法。Dominion 第一版以 `Material + CustomModelData` 为通用能力；`item-model` 通过单一 capability adapter 应用，不支持的平台明确降级，避免 core 直接链接高版本 API。

## 5. Dominion 已有可复用基底

第二阶段直接复用：

```text
SharedMenuDefinition
MenuRoute / MenuContext / ActionContext
MenuDataRegistry / MenuDataProvider
MenuEntry / PageSlice
ActionParser / ActionExecutor / ActionResult
DominionActionRegistry
UiCallbackSessionManager
UiRequestTracker
现有 22 页 provider 与 operation
LegacyToMiniMessage / PlaceholderAPI bridge
Scheduler bridge
```

因此不为 CUI 再写一套业务 provider、分页器、动作 parser 或 Dominion handler。

## 6. 第一版 CUI 配置建议

目录继续使用既定结构：

```text
uis/_shared/<menu>.yml
uis/<locale>/text_menu/<menu>.yml
uis/<locale>/chest_menu/<menu>.yml
```

建议配置：

```yaml
schema-version: 1
Title: '&0我的领地'
Layout:
  - '#########'
  - 'DDDDDDDDD'
  - 'DDDDDDDDD'
  - 'DDDDDDDDD'
  - 'DDDDDDDDD'
  - 'B######<>'
Buttons:
  '#':
    Display:
      material: GRAY_STAINED_GLASS_PANE
      name: ' '
  'D':
    source: entries
    Display:
      material: GRASS_BLOCK
      name: '&a{entry.name}'
      lore:
        - '&e左键管理'
        - '&b右键传送'
    Clicks:
      left:
        operation: manage-dominion
      right:
        operation: teleport-dominion
  'B':
    Display:
      material: RED_STAINED_GLASS_PANE
      name: '&c返回'
    Clicks:
      left:
        operation: back-main
  '<':
    Display:
      material: PAPER
      name: '&a上一页'
      lore: ['&7第 {page.current}/{page.total} 页']
    Clicks:
      left:
        operation: previous-page
```

每个 click 节点只能二选一：

```yaml
operation: shared-operation-id
```

或：

```yaml
actions:
  - 'tell: ...'
```

第一版不支持 condition、deny、wait、command、console 和自动 update。

## 7. 加载期约束

- Layout 只能为 1 至 6 行，每行严格 9 个 ASCII symbol。
- 空格是空槽；其他 symbol 必须存在于 Buttons。
- 一个菜单第一版最多一个 `source`。
- source 必须存在于 `_shared.data`，不能在 chest YAML 指定 Java provider。
- click type 只能来自固定枚举。
- operation 必须存在于 `_shared.operations`。
- inline actions 必须通过现有全局 action whitelist。
- Material、amount、模型字段、文本长度和变量键必须在 reload 时校验。
- 动态条目的 click 必须同时通过 `availableOperations` 检查；隐藏或无点击能力不能替代 action handler 的二次鉴权。
- 所有定义不可变，reload 后使用统一 menu revision。

## 8. 最小运行时结构

第一版只新增必要的 CUI 类：

```text
cui/
  ChestMenuDefinition
  ChestButtonDefinition
  ChestDisplayDefinition
  ChestMenuRepository
  CuiRenderer
  CuiInventoryHolder
  CuiListener
```

物品构建先作为 `CuiRenderer` 的内部职责；出现真实重复后再提取 `ChestItemFactory`。不提前建立条件引擎、皮肤服务、自动刷新器或业务 list renderer。

### 8.1 Holder

Holder 只保存不可变服务端状态：

```text
owner UUID
session UUID
MenuRoute
menu revision
slot -> callback token
```

不保存 DTO、业务 Provider、可变 YAML section 或 repeating task。

### 8.2 Callback transport

`UiCallbackSessionManager` 当前返回 TUI command 字符串。第二阶段应将“注册 callback”和“TUI command transport”拆开：

```text
register callback -> opaque token
TUI adapter -> /dominion ui_callback <token>
CUI adapter -> holder slot stores token
Dialog adapter -> platform callback stores token
```

token 仍保持玩家绑定、一次消费、5 分钟过期和 revision 校验。

### 8.3 点击生命周期

点击时：

1. 校验 top inventory holder、owner、revision、raw slot 和 click type。
2. 取消菜单交互及拖拽，防止物品进入顶层 Inventory。
3. 从 holder 获取服务端 token，不读取 ItemStack 业务参数。
4. 立即使当前 holder 的其他 callback 失效，避免并发双击。
5. 交给同一个 `ActionExecutor`。
6. `OPEN/REFRESH` 继续使用 CUI renderer；显式切换 UI 才改变 surface。
7. `STOP` 时若原 Inventory 仍打开，可重新渲染当前 route 生成新 callback；若动作已关闭菜单或进入聊天输入，不重新打开。

关闭 Inventory 时只清理该 holder 的 tokens，不能按玩家清空刚打开的新 session。

## 9. 与旧 scui 的关系

旧 `ChestView/ChestListView/ChestButton` 在迁移期间继续服务尚未转发到新 CUI 的页面，但不作为新 renderer 的内部依赖：

- 旧类用匿名闭包保存业务 callback，无法直接引用共享 operation。
- View ID 固定为玩家 UUID，没有独立 session/revision。
- 点击身份依赖 ItemStack PDC 的玩家 UUID。
- 没有 InventoryClose/Drag 的完整生命周期。
- `ChestListView` 在内存中收集全部条目后分页，无法直接复用异步 provider page size。

每迁移一个 CUI 页面，只在对应旧 `showCUI` 增加配置化 route 转发；旧实现继续作为故障回退。全部页面和服务器回归完成前不删除 `utils/scui`。

## 10. 推荐实施顺序

### 阶段 C1：定义与验证

- 实现 chest definition/repository。
- 加入两种 locale 路径和 manifest 安装。
- 完成 layout、display、click、source 和 operation 语义测试。
- 暂不打开 Inventory。

### 阶段 C2：callback 与生命周期

- 从 `UiCallbackSessionManager` 分离 opaque token transport。
- 实现不可变 holder 和无业务 listener。
- 覆盖 owner、slot、重复点击、旧 revision、close 和 drag 测试。

### 阶段 C3：静态样板

- 首先迁移 `MainMenu`。
- 验证静态布局、operation、OPEN/CLOSE、权限隐藏和回退。

### 阶段 C4：动态样板

- 迁移 `DominionList`。
- 验证 source 槽位容量、`PageSlice`、左右键、跨服 trusted arguments 和边界页。

### 阶段 C5：修改样板

- 迁移 `EnvFlags`。
- 验证 available operations、异步业务 future、REFRESH 和事件取消。

完成三个样板并进行逻辑测试后，再按现有 22 页 provider/action 批量补 chest YAML，不新增第二套业务逻辑。

## 11. 暂不实现

- KaMenu 式任意条件表达式。
- command/console 动作。
- wait/delayed action。
- 自动定时刷新。
- 多动态 source 或多个独立分页器。
- provider 驱动任意 Material/模型脚本。
- 从 ItemStack PDC 恢复业务目标。
- CUI 专属业务 handler。

这些能力只有在 Dominion 出现明确菜单需求且现有 route/provider/action 无法表达时再评估。

## 12. 当前实施状态

C1 配置契约已经实现：

- 新增不可变 chest menu/button/display/click 模型和严格 `ChestMenuLoader`。
- manifest 已登记 `main_menu` chest 资源，并由默认资源安装流程释放到数据目录。
- 中英文 MainMenu chest YAML 只引用 `_shared` operation。
- 自动测试覆盖合法样板、动态 source 容量和主要非法结构。

当前进度：

- C2 已拆分 opaque token 与 TUI command transport。
- `UiSurface` 已贯穿 callback、ActionContext 和聊天输入结果。
- 已实现不可变 `CuiInventoryHolder`、无业务 `CuiListener`、共享 `UiCallbackDispatcher` 和 `UiResultRouter`。
- Listener 尚未注册，Inventory renderer 尚未实现，因此旧 CUI 行为保持不变。

下一步 C3 接入 chest registry、ItemStack renderer 和静态 MainMenu。
