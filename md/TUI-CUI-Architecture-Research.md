# Dominion TUI/CUI 架构与回调研究报告

> 研究日期：2026-07-11  
> 研究范围：Dominion 当前 TUI、CUI、Console UI 分流、组件构建、点击回调、输入器、分页、配置和业务调用链  
> 目的：在引入 Spigot Dialog UI 前明确现有边界，避免形成互相调用的多套业务逻辑  
> 本报告只记录研究结论，不包含 Dialog 第一版实现

## 1. 总体结论

Dominion 当前 UI 不是“一个菜单模型由两个渲染器显示”，而是每个 UI 类分别编写 TUI、CUI 和 Console 三套表现代码。

当前共有 22 个类继承 `AbstractUI`。每个类通常具有：

```java
protected void showTUI(Player player, String... args)
protected void showCUI(Player player, String... args)
protected void showConsole(CommandSender sender, String... args)
```

三套表现代码虽然重复构建按钮和导航，但实际业务修改通常进入相同的静态 `*Command` 方法或 Provider。例如 TUI 和 CUI 的环境权限按钮最终都会调用：

```java
DominionFlagCommand.setEnv(...)
```

该命令完成校验、修改后，再调用 `EnvFlags.show(...)` 刷新当前 UI。

因此引入 Dialog 时最重要的原则是：

1. 不复制 Dominion 创建、删除、权限、成员等业务逻辑。
2. 不复用 TUI 的随机临时命令作为 Dialog callback。
3. 不复用 CUI 的 `ChestButton` 或槽位回调对象。
4. Dialog 通过稳定的 Dominion action 路由调用现有业务入口。
5. 后续刷新统一回到 UI 路由，由路由决定 TUI、CUI 或 Dialog。
6. 不直接给 22 个现有 UI 类全部增加第四个抽象方法，否则会立即产生第三套玩家 UI 分支。

## 2. 初始化顺序

插件在 `Dominion.onEnable()` 中按以下顺序初始化相关对象：

```text
Notification
  -> XLogger
  -> Scheduler
  -> Configuration / Language / TUI / CUI 配置
  -> XVersionManager / NMSManager
  -> CacheManager
  -> Inputter
  -> TextUserInterfaceManager
  -> ChestUserInterfaceManager
  -> DominionInterface / HooksManager
  -> EventsRegister
  -> InitCommands
  -> CommandManager
```

关键影响：

- UI 打开前缓存、语言、Scheduler 和两个 UI Manager 均已初始化。
- `Inputter`、`ChestUserInterfaceManager` 和 `CommandManager` 在构造时自行注册 Bukkit Listener。
- `/dominion` 无参数时由 `CommandManager.rootCommandConsumer` 调用 `MainMenu.show(sender, "1")`。
- 现有 UI 回调依赖 `CommandManager` 已初始化，因为 TUI 构建按钮时会动态注册子命令。

## 3. UI 入口与偏好分流

所有主要 UI 的静态入口遵循相同模式：

```java
public static void show(CommandSender sender, String... args) {
    new SomeUI().displayByPreference(sender, args);
}
```

`AbstractUI.displayByPreference(...)` 是当前唯一的 UI 类型分流点。

### 3.1 玩家分流规则

```text
CommandSender 是 Player？
  ├─ 否 -> showConsole(...)
  └─ 是
      ├─ PlayerDTO 不存在 -> showTUI(...)
      ├─ UUID 以 00000000 开头 -> 强制 showCUI(...)
      ├─ default-ui-type = BY_PLAYER
      │   ├─ 玩家偏好 CUI -> showCUI(...)
      │   └─ 玩家偏好 TUI -> showTUI(...)
      ├─ default-ui-type = CUI -> showCUI(...)
      └─ default-ui-type = TUI -> showTUI(...)
```

Bedrock 玩家通过 UUID 前缀判断，并始终使用 CUI。这是当前兼容策略，Dialog 接入不能绕过该规则。

### 3.2 UI 类型存储

`PlayerDTO.UI_TYPE` 当前只有：

```text
BY_PLAYER
CUI
TUI
```

玩家偏好写入数据库。`Configuration.defaultUiType` 决定是使用全局固定模式还是读取玩家偏好。

需要注意：`PlayerDOO.getUiPreference()` 遇到数据库中的 `BY_PLAYER` 时会将其转换为 CUI；空值回退为 TUI，非法值则尝试写成 CUI。因此“全局 BY_PLAYER”和“玩家持久化值”不是完全相同的概念。

### 3.3 Console 分支

Console 不使用 TUI click event 或 CUI Inventory。`AbstractUI` 会输出分隔线，然后调用 `showConsole(...)`。各 Console 实现通常打印现有命令 usage 和描述。

Dialog 只能面向 Player，不能替代 Console 分支。

### 3.4 异常处理

`displayByPreference(...)` 使用一个外层 `try/catch` 包住分流和渲染：

```java
try {
    // Resolve and render UI.
} catch (Exception exception) {
    Notification.error(sender, exception);
}
```

因此现有 UI 打开失败时不会继续抛到命令系统，但也没有自动切换到另一种 UI。未来 Dialog fallback 必须在 Dialog 路由层明确处理，不能依赖该 catch 自动回退。

## 4. TUI 架构

### 4.1 TUI 组件层次

```text
View
├── title
├── subtitle / navigator
├── content_lines
└── actionbar

ListView
├── View
├── page_size
├── List<Line>
├── ListViewButton page command
└── Pagination

Line
└── List<Component> + divider

Button
├── text / prefix / suffix
├── hover
├── ClickEvent
└── disabled style
```

`View` 按顺序向聊天栏发送标题、分隔线、内容行和底部分页栏。它不是单个消息，而是多条 Adventure `Component`。

### 4.2 Paper 与 Spigot 的消息发送

`TextUserInterfaceManager` 根据运行核心选择发送方式：

```text
Paper
  -> player.sendMessage(Adventure Component)

Spigot
  -> BungeeComponentSerializer.serialize(Component)
  -> player.spigot().sendMessage(BaseComponent...)
```

这样 TUI 内的 click event 在 Spigot 上仍能工作。当前 `Button.legacyClickEvent(...)` 还会通过反射优先构造 Adventure 4 风格 click action，再回退到新版静态工厂方法，用于兼容不同 Adventure 运行时。

### 4.3 TUI 普通按钮

纯展示按钮 `Button` 只负责构造文本、hover 和 disabled 样式。以下按钮直接绑定客户端 click event：

| 类型 | click event |
| --- | --- |
| `CommandButton` | `RUN_COMMAND` |
| `CopyButton` | `COPY_TO_CLIPBOARD` |
| `UrlButton` | `OPEN_URL` |

这些按钮没有服务端闭包状态，客户端直接执行对应静态行为。

### 4.4 FunctionalButton 回调链

`FunctionalButton` 是 TUI 业务点击的主要实现。

构造时执行：

```text
创建随机 UUID
  -> 创建 SecondaryCommand("tui_btn_future_" + UUID)
  -> 标记 dynamic
  -> 注册到 CommandManager.commands
  -> 创建 RUN_COMMAND click event
```

玩家点击后：

```text
客户端执行 /dominion tui_btn_future_<UUID>
  -> Bukkit PluginCommand
  -> CommandManager.onCommand(...)
  -> SecondaryCommand.run(...)
  -> 再次校验 FunctionalButton.permissions
  -> FunctionalButton.function()
  -> UI 静态方法或业务 Command 方法
```

`FunctionalButton.function()` 是匿名内部类闭包，通常捕获创建 UI 时的 `player`、DTO、页码和目标名称。

### 4.5 ListViewButton 与分页

`ListViewButton` 和 `FunctionalButton` 相同，但动态命令带一个 page 参数：

```text
/dominion tui_page_btn_future_<UUID> [page]
```

`Pagination.create(...)` 还会分别为上一页和下一页创建新的 `FunctionalButton`。点击箭头后不是运行固定分页命令，而是调用原 `ListViewButton.function(newPage)`，通常重新执行对应 `UI.show(...)`。

TUI 每次渲染一个含业务按钮和分页按钮的菜单，都会继续创建新的随机动态命令。

### 4.6 动态命令生命周期

动态命令保存在 `CommandManager.commands` 静态 Map 中。它们：

- 不出现在 tab completion 和 help 中。
- 点击后不会立即删除。
- 重新渲染时不会替换旧命令，而是新增随机命令。
- 只有 `PlayerQuitEvent` 发生且服务器已经没有在线玩家时，才批量注销全部 dynamic command。

这意味着动态命令不是一个适合 Dialog session 的生命周期模型。Dialog 应使用有玩家绑定、菜单绑定、超时和单次消费状态的独立 session。

### 4.7 TUI 权限与身份耦合

动态命令执行时检查的是“实际执行该命令的 sender”权限，但 `function()` 经常使用构建按钮时捕获的原玩家对象。

```text
命令 sender
  -> 用于权限检查

闭包捕获 player
  -> 用于实际业务调用
```

随机 UUID 让命令难以猜测，但这仍是身份与执行上下文分离的结构。Dialog action 不能复制这种模型；应始终以 `PlayerCustomClickEvent.getPlayer()` 作为操作者，并把 payload 中的玩家信息视为不可信数据。

### 4.8 TUI 输入器

TUI 输入不是组件内输入，而是“点击按钮后等待下一条聊天消息”。

```text
按钮 function()
  -> new InputterRunner(player, hint)
  -> Inputter.cachedInputters.put(player, runner)
  -> 玩家发送聊天消息
  -> AsyncPlayerChatEvent LOWEST
  -> 如果存在 runner，取消聊天事件
  -> Scheduler.runTask(...)
  -> runner 先注销自己
  -> 输入 C：cancelRun()
  -> 其他内容：run(input)
  -> 调用业务 Command
```

输入器特征：

- 同一玩家只能保存一个 pending inputter，新输入器会覆盖旧输入器。
- 输入只消费一次。
- 玩家退出时删除。
- 没有固定超时时间。
- `AsyncPlayerChatEvent` 会读取一个普通 `HashMap`，而注册/注销主要发生在服务器任务线程；这是当前实现中的线程边界。
- CUI 需要文本输入时也会关闭 Inventory，然后复用同一个 TUI Inputter。

Dialog 自带 TextInput 后不需要复用聊天输入器，但应直接调用聊天输入器最终调用的业务方法，例如 `DominionCreateCommand.autoCreate(...)`。

## 5. CUI 架构

### 5.1 CUI 组件层次

```text
ChestUserInterfaceManager
└── Map<player UUID, ChestView>

ChestView
├── Player owner
├── title
├── layout string
└── Map<slot, ChestButton>

ChestListView extends ChestView
├── item list
├── current page
├── item symbol
└── previous/next buttons

ChestButton
├── ItemStack
├── display name / lore
├── layout symbol
└── onClick(ClickType)
```

### 5.2 View 注册

`ChestView` 构造时立即向 `ChestUserInterfaceManager` 注册。View ID 不是随机 session，而是玩家 UUID：

```java
public UUID getViewId() {
    return viewOwner.getUniqueId();
}
```

因此每个玩家在 Manager 中最多对应一个当前 `ChestView`。

`getViewOf(player)`：

- 已有普通 `ChestView`：清空按钮和布局后复用。
- 已有 `ChestListView`：创建新的普通 View。
- 没有 View：创建新的普通 View。

`getListViewOf(player)` 总是创建新的 `ChestListView`，并覆盖该玩家旧 View 注册。

### 5.3 Layout

普通 View 使用长度为 9 的倍数、最多 54 个字符的 layout。字符代表槽位语义：

```text
#########
##A#B#C##
##D#E#F##
#########
####S####
```

`setButton(symbol, button)` 会将按钮设置到 layout 中该字符出现的全部槽位。

`ChestListView` 使用 `ListViewConfiguration.itemSymbol` 计算页面容量，并要求 layout 同时包含 item、上一页和下一页符号。

### 5.4 Item 构建

`ChestButton` 从 `ButtonConfiguration` 构造 `ItemStack`：

- 普通 Bukkit Material。
- Base64 皮肤的 Player Head。
- URL 皮肤的 Player Head。
- 按玩家名称查找皮肤的 Player Head。

显示名称、lore 支持：

- `{0}` 一类参数格式化。
- PlaceholderAPI。
- legacy color code。
- Paper 上转成 Adventure item meta。
- Spigot 上使用 legacy `setDisplayName/setLore`。

Player Head 纹理在异步 Scheduler 中写入 ItemStack，和首次 View render 之间存在异步时序。

### 5.5 打开与刷新

`ChestView.open()` 首先检查玩家当前打开 Inventory 的首个 item：

```text
首个 item 有 Dominion chest_view tag，并且槽位数相同
  -> refresh(existing InventoryView)

否则
  -> 创建新 Inventory
  -> player.openInventory(...)
  -> refresh(...)
```

`refresh(...)` 会：

1. 处理 title placeholder 和颜色。
2. 构建每个 `ChestButton`。
3. 给按钮 item 写入 PDC tag 和玩家 UUID。
4. 填充到指定 slot。
5. 用灰色玻璃 placeholder 填满空槽。

### 5.6 CUI 点击回调链

每个真实按钮 item 包含：

```text
dominion:chest_view = "chest_view"
dominion:view_id = <player UUID>
```

点击链路：

```text
InventoryClickEvent
  -> currentItem 是否有 chest_view tag
  -> event.setCancelled(true)
  -> 从 item PDC 读取 view_id
  -> ChestUserInterfaceManager.views.get(view_id)
  -> view.handleClick(slot, ClickType)
  -> buttons.get(slot)
  -> ChestButton.onClick(ClickType)
  -> UI 静态方法或业务 Command 方法
```

callback 是 `ChestButton` 匿名内部类，和 TUI 一样会捕获 player、DTO、页码和目标对象，但不会经过命令字符串。

### 5.7 ClickType

多数 CUI 按钮忽略 `ClickType`，但部分列表用左右键表达不同动作。例如领地列表：

```text
左键 -> DominionManage.show(...)
右键 -> teleportToDominion(...)
```

Dialog ActionButton 没有 Bukkit Inventory 的左右键语义。迁移时必须拆成两个明确按钮，不能让一个 Dialog 按钮隐式承担两种动作。

### 5.8 CUI 分页

`ChestListView.open()` 根据 `currentPage` 和 layout 中 item symbol 数量，将对应区间的按钮放入槽位。

上一页/下一页 callback 直接：

```text
applyListConfiguration(config, page +/- 1)
  -> open()
  -> refresh 当前 Inventory 或创建新 Inventory
```

分页发生在同一个 View 实例内，不重新进入 `AbstractUI.displayByPreference(...)`。

### 5.9 CUI 生命周期

当前 Manager：

- 玩家退出时删除 View。
- Inventory 关闭时不删除 View。
- 打开另一个 Dominion View 时覆盖该玩家旧 View。
- 没有 View timeout。
- 没有 `InventoryCloseEvent` 生命周期回调。
- 点击异常时记录错误并保持事件取消。

placeholder item 只有 `chest_view` tag，没有 `view_id`，因此点击 placeholder 会被取消，但不会进入按钮 callback。

当前只监听 `InventoryClickEvent`，没有独立的 `InventoryDragEvent` 处理逻辑。

### 5.10 CUI 到聊天输入器

需要字符串输入时，CUI callback 通常执行：

```text
Inputter.createOn(player, context...)
  -> view.close()
  -> 等待 AsyncPlayerChatEvent
```

例如重命名领地、编辑进出提示、地图颜色、创建模板等。

Dialog 输入组件可以直接提交这些值，因此不应先打开 Dialog、再切换到聊天输入器。

## 6. 配置与语言加载

Dominion 当前把语言和两种 UI 外观分开：

```text
plugins/Dominion/languages/<lang>.yml
plugins/Dominion/languages/tui/<lang>.yml
plugins/Dominion/languages/cui/<lang>.yml
```

源码默认文件对应：

```text
languages/<lang>.yml
languages/tui/<lang>.yml
languages/cui/<lang>.yml
```

`Language.loadLanguageFiles(...)` 会依次加载：

1. `Language.class`
2. `ChestUserInterface.class`
3. `TextUserInterface.class`

`ConfigurationManager` 使用 public field 反射完成：

- Java 字段名到 kebab-case YAML key。
- 缺失字段写回默认值。
- Headers、Comments。
- PreProcess/PostProcess。
- 配置 reload。

TUI 文案和样式集中在 `TextUserInterface`；CUI 的 layout、material、name、lore 集中在 `ChestUserInterface`。业务 UI 类同时声明默认配置结构和实际 render 方法。

未来 Dialog 独立 YAML 不适合强行塞进这套“固定 Java public field”反射配置，因为 Dialog 菜单需要任意菜单 ID、动态 body/input/button 列表。应使用 Bukkit `YamlConfiguration` 解析独立 `dialogs/*.yml`，但文本解析、默认资源释放和 reload 生命周期可以复用现有约定。

## 7. 当前业务调用链

### 7.1 读取型页面

UI render 直接从 `CacheManager` 获取 DTO 并构造内容：

```text
UI.show(...)
  -> displayByPreference(...)
  -> showTUI/showCUI
  -> Converts.toDominionDTO(...) / CacheManager
  -> Asserts.assertDominionAdmin(...)
  -> 构造当前快照的按钮和文本
```

UI 打开时通常已经重新读取缓存，而不是使用客户端提供的完整对象。

### 7.2 修改型动作

典型修改链路：

```text
TUI FunctionalButton.function()
或 CUI ChestButton.onClick()
  -> Dominion *Command static method
  -> Converts / Asserts
  -> Dominion Provider
  -> Cache 更新
  -> Notification
  -> UI.show(...)
  -> 再次按偏好选择并重绘
```

例如环境权限：

```text
DominionFlagCommand.setEnv(...)
  -> toDominionDTO(...)
  -> toEnvFlag(...)
  -> toBoolean(...)
  -> DominionProvider.setDominionEnvFlag(...)
  -> EnvFlags.show(...)
```

这条链路说明 `*Command` 静态方法当前同时承担：

- 参数转换。
- 调用 Provider。
- 捕获异常并通知。
- 决定修改后的页面刷新。

Dialog 第一版可以调用这些方法以避免复制业务，但中长期应把“修改业务”和“打开哪个 UI”拆开，否则 action handler 很难精确控制 Dialog 的 CLOSE、NONE 和 WAIT_FOR_RESPONSE 生命周期。

### 7.3 权限校验层次

权限和业务校验分散在多个层次：

| 层次 | 当前职责 |
| --- | --- |
| `SecondaryCommand` | Bukkit permission node |
| `FunctionalButton` | 点击动态命令时重复检查按钮 permission |
| `showTUI/showCUI` | 部分页面调用 `assertDominionAdmin` |
| `*Command` | 参数转换、部分权限和异常处理 |
| Provider | 最终数据操作约束 |

Dialog action 必须以事件中的 Player 重新走 `*Command`/Provider 校验，不能因为按钮只向管理员显示就跳过服务端校验。

## 8. TUI 与 CUI 对比

| 项目 | TUI | CUI |
| --- | --- | --- |
| 表现介质 | 多条聊天 Component | Bukkit Inventory |
| callback 标识 | 随机动态子命令 | 玩家 UUID + slot |
| callback 保存位置 | `CommandManager.commands` | `ChestUserInterfaceManager.views` |
| callback 对象 | `FunctionalButton/ListViewButton` 闭包 | `ChestButton` 闭包 |
| 权限入口 | 动态命令 permission | callback 内/业务层 |
| 分页 | 重开 UI，生成新命令 | 同一 View 内更新页码并 refresh |
| 文本输入 | 聊天 Inputter | 关闭 Inventory 后复用聊天 Inputter |
| 多点击类型 | 无 | 支持左键/右键等 ClickType |
| 关闭感知 | 无 | 没有 InventoryCloseEvent 管理 |
| 超时 | 无 | 无 |
| 玩家退出清理 | 服务器无人时清全部 dynamic command | 清该玩家 View |
| Spigot/Paper 差异 | Component 发送适配 | ItemMeta 文本适配 |

两套系统的共同点不是组件，而是 callback 最终调用的 UI 静态入口和业务 `*Command` 方法。

## 9. 引入第三套 UI 的主要风险

### 9.1 继续复制每个页面

若直接向 `AbstractUI` 增加：

```java
protected abstract void showDialog(Player player, String... args);
```

则 22 个 UI 类都必须立即新增实现，并形成：

```text
showTUI
showCUI
showDialog
showConsole
```

按钮可见性、权限判断、分页和刷新很容易在三套玩家 UI 中逐渐不一致。

### 9.2 复用 TUI 临时命令

Dialog callback 若转成 `/dominion tui_btn_future_<UUID>`：

- 会继续累积动态命令。
- 没有玩家/菜单 session timeout。
- callback 身份和闭包捕获玩家可能不一致。
- Dialog 输入还需要额外拼接到命令。
- 无法清晰处理一次性消费和过期行为。

因此不建议。

### 9.3 复用 CUI ChestButton

`ChestButton` 同时包含 ItemStack 外观、layout symbol 和 `ClickType`，与 Dialog `ActionButton` 模型无关。复用会迫使 Dialog 模块依赖 CUI 表现细节，也无法表达左右键。

因此不建议。

### 9.4 让 YAML 直接调用任意 Java 或命令

通用 `command:` 可以快速接入，但会把参数转义、安全校验、权限和 UI 跳转暴露给配置。Dominion 是固定业务插件，不需要 KaMenu 的通用动作能力。

Dialog YAML 应只引用白名单 Dominion action，由 action handler 决定允许哪些参数和业务方法。

## 10. Dialog 的建议边界

### 10.1 独立模块

建议 Dialog 模块包含：

```text
Dialog availability detector
Dialog YAML loader
Dialog renderer
Dialog session manager
PlayerCustomClickEvent listener
Dominion action registry
Dialog text adapter
```

core 只暴露不含 Spigot Dialog 类型的中立接口。旧服务端不能在类验证阶段加载 `net.md_5.bungee.api.dialog.*`。

### 10.2 UI 路由

建议未来形成单一入口：

```text
UiRouter.open(player, menuId, context)
  ├─ Bedrock -> CUI
  ├─ Dialog 已启用且该 menuId 已迁移 -> Dialog
  ├─ 玩家偏好 CUI -> CUI
  └─ TUI
```

第一版只迁移少量页面时，未找到 Dialog YAML、解析失败或运行时不支持，都回退到现有 `UI.show(...)`。

### 10.3 Dominion action

action 应是稳定白名单，而不是 Java 类名或任意命令：

```text
dominion:open
dominion:back
dominion:close
dominion:create
dominion:set_flag
dominion:delete
dominion:teleport
```

每个 handler 接收统一上下文：

```text
player
menuId
session token
trusted server context
submitted inputs
action arguments
```

其中 trusted context 来自服务端 session，不从客户端 payload 恢复玩家身份、权限或 DTO。

### 10.4 actions 组

按当前需求，`actions` 只需顺序执行，不需要 KaMenu 条件树：

```yaml
actions:
  - 'tell: &a操作完成'
  - 'actionbar: &e正在刷新领地信息'
  - 'dominion: open dominion_list'
```

建议语义：

- 默认严格按顺序执行。
- 一个动作失败后停止后续动作。
- 业务动作返回明确结果，不依赖解析聊天消息判断成功。
- `close/open/back` 属于生命周期终止动作，执行后停止剩余 UI 导航动作。
- 不支持嵌套条件、JavaScript、console command 或任意 reflection。

### 10.5 首批反馈动作与现有能力

| 计划动作 | Dominion 当前能力 | 研究结论 |
| --- | --- | --- |
| `tell` | `Notification.info/warn/error`、TUI Component sender | 可复用文本解析，但应明确是否添加 Dominion prefix |
| `actionbar` | `Notification.actionBar` | Paper/Spigot 已有适配，可直接形成 core 服务接口 |
| `title` | `Notification.title` | 支持 title + subtitle，当前使用 Adventure 默认时间 |
| `toast` | 当前无 Notification 方法 | 需要单独研究 Spigot 实现，不应假设存在稳定 Bukkit API |
| 可点击文本 | TUI Adventure Component + Bungee serializer | 发送能力存在；callback 应走 Dialog session/action registry，不走 TUI 临时命令 |

可点击文本需要再区分：

1. URL/copy：客户端静态 click event，不需要 session。
2. Dominion action：服务端 custom click，必须绑定玩家和 session。
3. 打开另一个 Dialog：可使用 show-dialog click event，但仍应经过受控路由。

## 11. 建议的渐进改造顺序

### 阶段一：只增加旁路，不改现有 UI

1. 建立独立 Dialog 模块和能力检测。
2. 建立 Dominion action registry 和 session manager。
3. 只迁移主菜单和一个输入/列表页面。
4. 所有业务操作调用现有 `*Command` 方法。
5. 不支持时回退 TUI/CUI。

### 阶段二：统一刷新路由

1. 将 `EnvFlags.show(...)` 等刷新动作逐步替换为 `UiRouter.open(...)`。
2. 业务 Command 不再硬编码具体 UI 类。
3. TUI/CUI/Dialog 都从同一 route context 获取页面和参数。

### 阶段三：提取共享页面模型

仅在迁移过程中出现明显重复时，再提取：

```text
MenuModel
MenuEntry
MenuAction
PageContext
InputDefinition
```

不要在第一版一次性重写全部 22 个 UI。现有 TUI/CUI 继续工作，是迁移期间最重要的兼容保障。

## 12. 第一版前必须确定的规则

1. Dialog 是独立偏好、全局 AUTO，还是仅管理员实验开关。
2. Bedrock 是否始终保持 CUI。
3. Dialog action 失败后是否关闭、保留还是重开页面。
4. `tell` 是否自动添加 Dominion prefix。
5. `title` YAML 如何表达 subtitle 和显示时间。
6. toast 在 Spigot 上的实现和版本范围。
7. 可点击文本支持 URL/copy、Dominion action、show-dialog 中的哪些子集。
8. session 默认时间、一次性消费和超时提示。
9. 第一批迁移的具体菜单 ID。
10. YAML 用户修改后的默认文件升级策略。

## 13. 关键源码索引

| 主题 | 源码 |
| --- | --- |
| 插件初始化 | `core/src/main/java/cn/lunadeer/dominion/Dominion.java` |
| UI 类型分流 | `core/src/main/java/cn/lunadeer/dominion/uis/AbstractUI.java` |
| 玩家 UI 偏好 | `api/src/main/java/cn/lunadeer/dominion/api/dtos/PlayerDTO.java` |
| TUI 消息发送 | `core/src/main/java/cn/lunadeer/dominion/utils/stui/TextUserInterfaceManager.java` |
| TUI View/ListView | `core/src/main/java/cn/lunadeer/dominion/utils/stui/View.java`、`ListView.java` |
| TUI 动态按钮 | `core/src/main/java/cn/lunadeer/dominion/utils/stui/components/buttons/FunctionalButton.java` |
| TUI 分页按钮 | `core/src/main/java/cn/lunadeer/dominion/utils/stui/components/buttons/ListViewButton.java`、`Pagination.java` |
| TUI 输入器 | `core/src/main/java/cn/lunadeer/dominion/utils/stui/inputter/Inputter.java`、`InputterRunner.java` |
| 命令回调注册 | `core/src/main/java/cn/lunadeer/dominion/utils/command/CommandManager.java` |
| CUI Manager | `core/src/main/java/cn/lunadeer/dominion/utils/scui/ChestUserInterfaceManager.java` |
| CUI View/ListView | `core/src/main/java/cn/lunadeer/dominion/utils/scui/ChestView.java`、`ChestListView.java` |
| CUI Button | `core/src/main/java/cn/lunadeer/dominion/utils/scui/ChestButton.java` |
| TUI 配置模型 | `core/src/main/java/cn/lunadeer/dominion/configuration/uis/TextUserInterface.java` |
| CUI 配置模型 | `core/src/main/java/cn/lunadeer/dominion/configuration/uis/ChestUserInterface.java` |
| 配置反射加载 | `core/src/main/java/cn/lunadeer/dominion/utils/configuration/ConfigurationManager.java` |
| 反馈消息工具 | `core/src/main/java/cn/lunadeer/dominion/utils/Notification.java` |
| 代表性主菜单 | `core/src/main/java/cn/lunadeer/dominion/uis/MainMenu.java` |
| 代表性列表菜单 | `core/src/main/java/cn/lunadeer/dominion/uis/dominion/DominionList.java` |
| 代表性修改页面 | `core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/EnvFlags.java` |

## 14. 最终建议

Dominion 当前已经存在可复用的业务入口，但尚不存在可复用的 UI 页面模型。Dialog 第一版应作为受控旁路接入：

```text
Dialog YAML
  -> Dialog renderer
  -> Dominion action registry
  -> existing Command / Provider
  -> unified UI route
```

不应形成：

```text
Dialog YAML
  -> TUI random command
  -> CUI ChestButton
  -> another UI callback
```

前一种结构让三种 UI 只共享业务和路由；后一种结构会把三套表现层串联，产生难以验证的身份、生命周期和刷新问题。
