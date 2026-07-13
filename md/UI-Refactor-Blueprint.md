# Dominion TUI/CUI/Dialog 重构蓝图

> 编写日期：2026-07-11  
> 依据文档：`TUI-CUI-Architecture-Research.md`、`Spigot-Dialog-API-Research.md`  
> 参考项目：KaGuilds CUI 配置、KaMenu Dialog 配置与动作队列  
> 当前阶段：架构设计，不包含 Java 实现

## 1. 背景与决策

Dominion 当前每个页面分别实现 `showTUI`、`showCUI` 和 `showConsole`。TUI 与 CUI 的按钮最终通常调用相同的 `*Command` 静态业务方法，但两套 UI 各自维护按钮、分页、输入和刷新逻辑。若直接增加 `showDialog`，同一页面会出现第三份玩家 UI 业务分支。

本次重构的核心决策是：

1. 统一动作、路由、上下文、变量和 session，不强行统一三种 UI 的视觉组件。
2. TUI、CUI、Dialog 都只负责展示和收集 callback，不直接实现 Dominion 业务。
3. 所有 callback 转换为同一组 `ActionSpec`，通过同一个 `ActionExecutor` 严格顺序执行。
4. Dominion 业务只通过白名单 `dominion:` 动作调用，不开放任意反射、控制台命令或 JavaScript。
5. CUI 参考 KaGuilds 的字符槽位布局；Dialog 参考 KaMenu 的 `Title/Settings/Body/Inputs/Bottom`；TUI 使用类似字符布局的行组件配置。
6. Console 保留现有命令帮助输出，不尝试套用玩家菜单渲染器。
7. 先完成共享底座和少量页面迁移，再逐页替换旧 `showTUI/showCUI`，避免一次性改写 22 个页面。

## 2. 目标

### 2.1 功能目标

- 三种玩家 UI 共用一套动作语法和执行顺序。
- 菜单的外观和布局可通过 YAML 调整，无需修改 Java 页面类。
- 同一个业务动作在 TUI、CUI、Dialog 中使用相同 action id、参数校验和结果语义。
- 页面跳转、刷新、关闭和分页由统一 `UiRouter` 处理。
- CUI 动态列表、TUI 文本列表和 Dialog 多按钮列表使用同一数据源定义。
- Dialog 文本输入直接提交到动作上下文，不再中转聊天 Inputter。
- 旧版本、Spigot、Paper 和 Folia 在各自能力范围内稳定降级。

### 2.2 工程目标

- 新增 Java 类和方法均提供简短英文 Javadoc，说明用途和边界。
- 平台专属 API 类型不出现在共享接口的方法签名、字段或静态初始化中。
- 菜单 YAML 在 reload 时完成结构校验，运行时不反复猜测配置类型。
- 动作执行、变量解析、权限校验和 session 生命周期可独立测试。
- 配置错误能够指出菜单 id、YAML 路径和具体原因。

## 3. 非目标

第一阶段明确不实现：

- KaMenu 的条件树、JavaScript、目标选择器、任意命令、跨菜单动作包和周期任务。
- 在一份视觉模型中表达全部 TUI、CUI、Dialog 组件。
- 完全替代 Dominion Provider、DTO、Cache 或现有权限体系。
- 让 YAML 绕过服务端权限检查或直接调用任意 Java 方法。
- 用 Dialog 替代 Console 输出。
- 为旧客户端伪造原生 Dialog。
- 首次迁移时删除旧 TUI/CUI 实现；旧实现需要作为回退保留到页面迁移完成。

## 4. 总体架构

```text
resources/uis/
├── _shared/<menu-id>.yml      shared route, provider and action groups
└── <locale>/
    ├── text_menu/<menu-id>.yml
    ├── chest_menu/<menu-id>.yml
    └── dialog_menu/<menu-id>.yml
             │
             ▼
       MenuRepository
             │ validated immutable definitions
             ▼
       MenuDefinitionRegistry
             │
      MenuRoute + MenuContext
             │
             ▼
          UiRouter
      ┌──────┼───────────┐
      ▼      ▼           ▼
 TuiRenderer CuiRenderer DialogRenderer
      │      │           │
      └──── callback ─────┘
             │
             ▼
        UiSessionManager
             │ trusted context + submitted inputs
             ▼
 ActionParser -> ActionRegistry -> ActionExecutor
                                  │
                       ┌──────────┴──────────┐
                       ▼                     ▼
               Feedback handlers     Dominion handlers
                       │                     │
                       └──── ActionResult ───┘
                                  │
                                  ▼
                               UiRouter
```

共享的是“用户触发了哪个意图、在哪个可信上下文执行、执行后如何导航”，而不是 ItemStack、聊天 Component 或 Dialog widget。

## 5. 建议包结构

以下名称是目标结构，实施时可根据现有包风格小幅调整：

```text
cn.lunadeer.dominion.uis
├── menu
│   ├── action
│   │   ├── ActionSpec
│   │   ├── ActionParser
│   │   ├── ActionHandler
│   │   ├── ActionRegistry
│   │   ├── ActionExecutor
│   │   ├── ActionContext
│   │   └── ActionResult
│   ├── route
│   │   ├── MenuRoute
│   │   ├── MenuContext
│   │   ├── UiRouter
│   │   └── UiTypeResolver
│   ├── session
│   │   ├── UiSession
│   │   ├── UiSessionManager
│   │   └── UiCallbackToken
│   ├── config
│   │   ├── MenuRepository
│   │   ├── MenuDefinitionRegistry
│   │   ├── MenuConfigValidator
│   │   └── definition/...
│   ├── data
│   │   ├── MenuDataProvider
│   │   ├── MenuEntry
│   │   └── PageSlice
│   └── text
│       ├── MenuTextResolver
│       ├── ClickableTextParser
│       └── ResolvedText
├── tui
│   ├── TuiRenderer
│   └── TuiCallbackAdapter
├── cui
│   ├── CuiRenderer
│   └── CuiListener
└── dialog
    ├── DialogRenderer
    ├── DialogCapability
    └── DialogCallbackAdapter

version/platform-specific implementation
├── PaperDialogRenderer
└── SpigotDialogRenderer
```

职责限制：

- `ActionExecutor` 不依赖 TUI、Inventory 或 Dialog API。
- `CuiListener` 不识别 `dominion:create` 等具体业务动作，只解析 callback 后转交执行器。
- `DialogCallbackAdapter` 不直接读取或修改 Dominion DTO，只规范化输入并恢复 session。
- `MenuTextResolver` 只解析已知变量，不执行动作。
- `DominionActionHandler` 可以调用业务 service/Provider，但不负责构建 UI。

## 6. 核心领域模型

### 6.1 `MenuRoute`

`MenuRoute` 表示“要打开哪个页面及其可信参数”，替代散落的 `SomeUI.show(player, args...)`。

建议字段：

```java
public record MenuRoute(
        String menuId,
        Map<String, String> arguments,
        int page
) {}
```

约束：

- `menuId` 只能引用已加载的菜单定义。
- `arguments` 由服务端创建，不直接使用客户端提交的完整 map。
- 领地 id、成员 UUID、权限组 id 等目标标识属于 route/session 的可信参数。
- `page` 从 0 开始存储，展示变量 `{page}` 从 1 开始。
- route 应为不可变对象，每次翻页或跳转创建新 route。

### 6.2 `MenuContext`

`MenuContext` 是渲染上下文，建议包含：

```text
player
uiType
route
locale
resolved subject DTO snapshot
variables
capabilities
```

其中 DTO snapshot 只用于渲染。动作执行时必须按 route 中的 id 重新读取当前缓存/Provider 状态，不能把渲染时 DTO 视为持续有效。

### 6.3 `ActionContext`

`ActionContext` 是一次动作序列的服务端上下文：

```text
player                  event/command 的实际操作者
sourceUiType            TUI / CUI / DIALOG
route                   当前可信路由
sessionId               产生 callback 的 session
callbackId              被点击组件的稳定本地 id
trustedArguments        session 保存的业务参数
submittedInputs         已清洗、仍不可信的用户输入
variables               只读合并视图
```

变量优先级建议固定为：

```text
submitted inputs > list entry variables > route variables > player/global variables
```

优先级只影响文本取值，不改变信任等级。业务 handler 必须知道参数来源并重新校验。

### 6.4 `ActionSpec`

第一阶段只接受字符串动作。加载 YAML 时解析为不可变对象，不在每次点击时重复切分字符串。

```java
public record ActionSpec(
        String type,
        String argument,
        String configPath
) {}
```

建议 parser 规则：

- `close`、`refresh` 等无参动作允许无冒号。
- 其他动作使用第一个 `:` 分割 type 与 argument。
- type 转小写；argument 保留原始大小写并 trim 外层空格。
- 空 type、未知 type、缺失必填参数在 reload 时报告错误。
- 不使用正则拆分复杂 title/clickable text；这些动作交给各自结构化 parser。

### 6.5 `ActionResult`

动作结果不能只用 boolean。建议使用封闭结果类型：

```text
CONTINUE                      continue next action
STOP                          stop successfully
OPEN(MenuRoute)               open another menu and stop
REFRESH                       refresh current route and stop
CLOSE                         close current UI and stop
FAILURE(message, cause)       report/log and stop
```

规则：

- `CONTINUE` 是唯一继续执行后续动作的结果。
- `OPEN`、`REFRESH`、`CLOSE` 都终止旧菜单动作序列。
- `FAILURE` 默认终止，避免前一项失败后继续执行成功提示或跳转。
- handler 不直接调用下一个 handler；统一由 executor 编排。
- 业务动作成功后若需要刷新，应返回 `REFRESH`，不要在 handler 内调用 `SomeUI.show(...)`。

## 7. 统一动作协议

### 7.1 第一阶段动作清单

| 动作 | 用途 | 结果 |
| --- | --- | --- |
| `tell: <text>` | 发送聊天消息 | `CONTINUE` |
| `actionbar: <text>` | 发送 action bar | `CONTINUE` |
| `title: <arguments>` | 发送 title/subtitle | `CONTINUE` |
| `toast: <arguments>` | 发送 toast | `CONTINUE` |
| `hovertext: <markup>` | 发送可点击聊天文本 | `CONTINUE` |
| `open: <menu-id> [args]` | 打开另一个菜单 | `OPEN` |
| `refresh` | 刷新当前菜单 | `REFRESH` |
| `close` | 关闭当前 UI | `CLOSE` |
| `page: previous` | 上一页 | `OPEN(updated route)` |
| `page: next` | 下一页 | `OPEN(updated route)` |
| `page: <number>` | 指定页 | `OPEN(updated route)` |
| `dominion: <operation> [args]` | 调用白名单领地业务 | 由 handler 决定 |

`tell/actionbar/title/toast/hovertext` 是跨 UI 的反馈动作，并不意味着它们只能出现在 TUI。CUI 或 Dialog 按钮也可以执行同一列表。

### 7.2 顺序执行

接口建议从第一版就保留异步返回值：

```java
CompletionStage<ActionResult> execute(ActionContext context, List<ActionSpec> actions);
```

执行语义：

```text
execute action[0]
  -> await CompletionStage<ActionResult>
  -> CONTINUE ? execute action[1] : finish result
```

即使第一阶段没有 `wait`，Dominion 的缓存、数据库或后续 service 可能异步完成。使用 `CompletionStage` 可以保持动作严格有序，避免未来从同步循环改为异步链时改变所有 handler。

不建议使用 KaGuilds 当前“累计 delay 后批量调度”的方式，因为该方式不能保证前一个异步动作真正完成，也难以传播失败和导航结果。

### 7.3 `dominion:` 业务动作

业务动作必须显式注册，示例：

```yaml
actions:
  - 'dominion: create name={input.dominion_name}'
  - 'tell: &a领地创建成功。'
  - 'open: dominion_list'
```

建议首批 operation 按页面迁移逐步增加，不提前建立一个巨型 switch。可能包括：

```text
create
delete
rename
teleport
set-env-flag
set-guest-flag
add-member
remove-member
set-member-role
resize
set-message
```

每个 operation 对应独立 `ActionHandler` 或小型领域 handler。handler 的职责是：

1. 声明允许的参数和输入类型。
2. 从 route/session 获取可信目标 id。
3. 清洗并解析提交输入。
4. 重新执行权限、所有权、范围和当前状态校验。
5. 调用现有 Provider/service。
6. 返回 `ActionResult`，不直接选择具体 UI renderer。

业务修改成功且 YAML 仍需继续发送反馈或跳转时，handler 返回 `CONTINUE`；校验失败或业务异常返回 `FAILURE` 并终止后续动作。只有业务本身明确要求立即刷新、关闭或跳转时才返回对应导航结果。

过渡期间可以由 adapter 调用现有 `*Command`，但应避免其内部硬编码 `SomeUI.show(...)`。推荐逐步把命令拆为：

```text
Command adapter -> domain operation -> OperationResult
UI action       -> domain operation -> OperationResult
```

命令和 UI action 共享 domain operation，而不是 UI action 模拟执行命令字符串。

### 7.4 导航动作参数

第一版建议只允许受控 key/value 参数：

```yaml
- 'open: dominion_manage dominion={route.dominion}'
```

限制：

- 只允许目标菜单定义中声明的 route keys。
- `{route.*}` 继承可信 route 参数。
- `{entry.*}` 来自服务端创建的列表 entry。
- `{input.*}` 默认不能用于资源目标 id，只能用于业务值；例外必须由 handler 显式声明。
- 未声明参数、缺失必填参数或无法解析页码时返回 `FAILURE`。

### 7.5 反馈动作格式

`title` 建议保持单行参数格式，同时由专用 parser 处理：

```yaml
- 'title: title=&a操作成功;subtitle=&7领地已更新;in=10;stay=50;out=10'
```

`toast` 建议第一版只开放稳定字段：

```yaml
- 'toast: icon=GRASS_BLOCK;title=&a领地创建成功;frame=task'
```

所有文本都经过同一个 `MenuTextResolver`，支持 Dominion 颜色、语言变量和受控上下文变量。第一阶段不引入 PlaceholderAPI 任意条件表达式；是否保留纯文本 PAPI 替换可以在实现阶段单独决定并记录安全/性能边界。

## 8. 可点击文本统一设计

### 8.1 语法

参考 KaMenu，但缩小为 Dominion 所需能力：

```yaml
- 'hovertext: &7管理领地 <text="&a[点击打开]";hover="&7打开领地管理";actions="open_manage">'
```

可点击片段字段：

| 字段 | 用途 | 第一阶段 |
| --- | --- | --- |
| `text` | 显示文本 | 必需 |
| `hover` | 悬停文本 | 可选 |
| `actions` | 当前组件声明的命名动作组 | 可选 |
| `url` | 打开 URL | 可选 |
| `copy` | 复制文本 | 可选 |
| `newline` | 片段后换行 | 可选 |

不支持 `command`。点击服务端业务必须通过 `actions`，这样才能进入 session、权限校验和统一 executor。

优先级建议：

```text
actions > url > copy
```

同一片段配置多个点击目标时，reload 输出 warning，并按上述优先级选择；严格模式可直接拒绝加载。

### 8.2 命名动作组

组件内或菜单级动作组示例：

```yaml
action-groups:
  open_manage:
    - 'open: dominion_manage dominion={route.dominion}'
```

仅允许当前菜单内引用，不允许递归调用其他 action group。它只是可点击文本到动作列表的本地映射，不是可跨菜单复用的 KaMenu 动作包。第一阶段不支持动作组传入自由参数，避免形成第二套函数语法。

### 8.3 平台传输

| 平台/载体 | callback 传输 |
| --- | --- |
| TUI 新版 Adventure | custom click/callback + session token |
| TUI 旧版或不支持 custom click | 固定 `/dominion ui <token>` 命令 |
| CUI | holder/session id + slot callback id |
| Spigot Dialog button | `CustomClickAction` + session token/inputs |
| Spigot clickable text | `ClickEventCustom` + session token |
| Paper Dialog | Paper custom callback + session token/context |
| URL/copy | 平台静态 click event，不进入服务端 session |

legacy TUI 只能使用固定命令 transport，但不能继续为每个按钮动态注册 `SecondaryCommand`。固定命令只负责消费 token，业务 action 仍由统一 executor 执行。

## 9. Session 与安全模型

### 9.1 `UiSession`

每次渲染创建一个玩家绑定 session：

```text
sessionId              random, unguessable token
playerUuid
uiType
route
menuRevision
createdAt
expiresAt
callback map           callbackId -> parsed actions + trusted entry arguments
input metadata         key -> type/length/range/options
state                  ACTIVE / CONSUMED / EXPIRED / CLOSED
```

建议策略：

- 每个玩家每种 UI 最多一个活动菜单 session；打开新页面使旧 session 失效。
- callback token 至少包含足够熵，日志只输出缩短后的 token。
- 业务按钮默认一次性消费，分页和纯导航可由新页面生成新 session。
- callback 必须核对实际事件玩家 UUID。
- 玩家退出、插件 disable、reload、菜单 revision 改变时清理 session。
- 建议默认有效期 5 分钟，可配置但应设置上限。
- 过期点击只提示“菜单已过期，请重新打开”，不执行旧动作。

### 9.2 客户端数据不可信

以下数据均不得直接作为授权依据：

- Dialog `JsonElement` / `DialogResponseView`。
- custom click payload。
- 固定 TUI command 中的 token 以外参数。
- CUI ItemStack PDC 中可被客户端或其他插件影响的值。
- `{input.*}`、用户输入页码和客户端返回 option。

目标领地、成员、权限组和列表 entry 的内部 id 必须从 session callback map 恢复。即使 id 来自 session，业务执行前仍需验证目标存在且玩家当前有权操作。

### 9.3 Dialog 等待状态

`WAIT_FOR_RESPONSE` 仅用于服务端确定会在有限时间内关闭、重绘或打开下一 Dialog 的动作。执行器必须在所有结果路径处理：

| 结果 | Dialog 行为 |
| --- | --- |
| `OPEN` | 打开目标 Dialog |
| `REFRESH` | 重绘当前 Dialog |
| `CLOSE` / `STOP` | 明确关闭 |
| `FAILURE` | 发送错误并关闭或重绘可恢复页面 |
| timeout | 关闭等待界面、清理 session、发送超时提示 |

不要依赖客户端自动超时。workflow timeout 是 Dominion 自己的 session/动作超时策略，建议初值 30 秒；普通菜单 callback session 仍可保留 5 分钟。

## 10. 变量与文本解析

### 10.1 变量命名空间

建议使用显式命名空间，避免 `{name}` 在玩家、领地、成员和输入之间冲突：

```text
{player.name}
{route.dominion}
{dominion.name}
{dominion.owner}
{entry.id}
{entry.name}
{input.dominion_name}
{page.current}
{page.total}
```

兼容旧语言字段时可提供短名称 alias，但新菜单只写命名空间形式。

### 10.2 解析阶段

```text
YAML load
  -> parse static structure and action syntax
menu open
  -> resolve route and domain snapshot
  -> build immutable variable map
  -> render text/display
callback
  -> restore session
  -> normalize submitted inputs
  -> resolve action arguments immediately before each action
```

动作参数在动作真正执行前解析，以便前一个异步业务动作更新的状态可被后续反馈使用。用于资源授权的 id 不从普通字符串替换结果中获取，而由 handler 从 trusted arguments 读取。

### 10.3 输出转义

- 文本变量默认作为文本内容处理，不能注入新的 `<text=...>` 标记。
- clickable markup 先解析静态结构，再把变量填入字段。
- URL 只允许 `https` 和可选 `http` 白名单策略。
- material、sound、toast frame 和 option id 使用枚举/NamespacedKey 校验。
- 输入长度先按 Dialog/CUI/TUI 控件限制，再按业务限制二次校验。

## 11. 共享菜单元数据

每个菜单在 `uis/_shared/<menu-id>.yml` 中保存三种 renderer 和全部语言共用的语义，只描述 route、data provider 和 action groups：

```yaml
schema-version: 1
menu-id: dominion_list

route:
  optional:
    - owner

data:
  entries:
    provider: dominion_list
    page-size: auto
    sort: default

action-groups:
  close-menu:
    actions:
      - 'close'
```

`page-size: auto` 表示由 renderer 的列表容量决定。若三种 UI 容量不同，data provider 接收 renderer 算出的 page size，并返回同一 `PageSlice` 结构。

类型菜单采用混合动作模式。跨 TUI/CUI/Dialog 复用的业务、导航和分页动作通过 `action-group` 引用 `_shared`；仅属于当前 renderer 或当前语言展示的短反馈动作可以直接内联 `actions`。例如 `_shared/dominion_list.yml` 定义 `manage/teleport/delete`，而 text/chest/dialog 文件分别决定这些 action group 使用文本按钮、点击类型还是独立 Dialog 按钮。

`actions` 与 `action-group` 必须二选一。loader 会在加载期把两者统一解析为不可变的 `List<ActionSpec>`，renderer 和 callback session 不需要识别动作来源。内联动作仍经过全局 action whitelist，不提供额外权限或命令执行能力。`_shared` 没有共享动作组时允许省略 `action-groups`，但 route、provider 和可信参数仍应保留在共享层。

不把 title、layout、ItemStack display 或 Dialog body 放进 `_shared`，因为它们既与 renderer 能力有关，也需要按语言调整长度和排列。共享反馈文本应引用现有语言键，或由 action handler 返回可翻译的 message key。

## 12. CUI 配置规范

### 12.1 示例

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
        - '&7所有者: &f{entry.owner}'
        - '&e左键管理'
        - '&b右键传送'
    Clicks:
      left:
        action-group: manage-dominion
      right:
        action-group: teleport-dominion

  'B':
    Display:
      material: RED_STAINED_GLASS_PANE
      name: '&c返回'
    Clicks:
      left:
        action-group: back-main

  '<':
    Display:
      material: PAPER
      name: '&a上一页'
      lore:
        - '&7第 {page.current} / {page.total} 页'
    Clicks:
      left:
        action-group: previous-page

  '>':
    Display:
      material: PAPER
      name: '&a下一页'
    Clicks:
      left:
        action-group: next-page
```

每个 `Clicks.<type>` 节点必须在 `action-group` 和 `actions` 中二选一。动态目标不写入 YAML action 参数，entry 的 trusted arguments 由服务端 callback session 恢复。

### 12.2 布局规则

- `layout` 为 1 至 6 行，每行必须恰好 9 个 Unicode code unit 范围内的单字符 symbol；第一版建议限制 ASCII。
- 空格代表真正空槽，未配置 symbol 默认空槽。
- 同一普通 symbol 重复出现时复用 display/actions。
- 绑定 `source` 的 symbol 表示动态列表槽位，每个位置绑定独立 `MenuEntry` 和 callback trusted arguments。
- 一个菜单第一阶段只允许一个分页 source，避免 page 状态歧义。
- `#` 没有特殊内置语义，只是普通 symbol。

### 12.3 点击类型

第一阶段支持：

```text
left
right
shift-left
shift-right
middle
drop
```

可额外提供 `any` fallback。具体 click type 未配置时不执行动作。

Dialog 不具备左右键，因此迁移同一业务时，Dialog 配置必须把不同点击语义拆成独立按钮。共享层不能假定一个 entry 只有一个操作。

### 12.4 Holder 与监听器

新 CUI holder 只保存：

```text
owner UUID
sessionId
MenuRoute
menuRevision
slot -> callback token
```

holder 本身是服务端对象，slot 只保存 opaque callback token，不保存 DTO 或客户端可修改的业务参数。监听器必须：

- 取消顶层 Dominion Inventory 的 click。
- 取消 drag 到顶层 Inventory。
- 忽略玩家自身背包的普通操作，除非可能移动物品进入顶层。
- 核对 holder owner、revision、session 和 raw slot。
- 在 `InventoryCloseEvent` 标记 CUI session 关闭，但避免“刷新时关闭旧 Inventory”误清理新 session。

### 12.5 KaGuilds 源码复核后的取舍

详细研究见 `KaGuilds-CUI-Architecture-Research.md`。第二阶段确认：

- 采用 KaGuilds 的 9 字符槽位布局、重复 symbol、动态 source 占槽和多点击配置思路。
- 不采用 `GUILDS_LIST/MEMBERS_LIST` 一类业务 type；动态内容继续来自 `_shared` 注册的 `MenuDataProvider`。
- 不从 ItemStack PDC 恢复领地、成员或权限组目标；slot 只关联服务端 callback token。
- 不开放 command/console、条件表达式、wait 或定时数据库刷新。
- CUI YAML 在 reload 时严格校验并生成不可变 revision，不在每次打开时读取磁盘。
- callback 注册先返回 opaque token，再由 TUI command、CUI holder 和后续 Dialog adapter 分别传输。
- 第一批样板固定为 `MainMenu`、`DominionList`、`EnvFlags`，分别验证静态、动态分页和修改刷新。

## 13. TUI 配置规范

### 13.1 设计选择

TUI 不直接复制 CUI 的 9 列槽位，也不沿用 KaMenu 把整段可点击文本压在一行的 markup。根据 `TUI内容补充.yml` 的设想，TUI 采用“文本行模板 + 命名按钮占位符”：

- `Layout` 中每个字符串代表一条聊天消息模板，可直接看到最终文本的大致排列。
- `{button-id}` 引用 `Buttons.<button-id>`，渲染时替换为可点击 Component。
- 一行可以混合普通文本、颜色代码、变量和多个按钮。
- 同一按钮可以在多行重复引用；每个渲染位置创建独立 callback，但复用同一动作定义。
- `LIST` 占位符必须独占一行，由 renderer 按配置展开为多条 entry 行。
- `Buttons` 只描述可交互组件或动态展开组件，普通静态文本直接写在 `Layout`。

顶层主要内容键为 `Layout` 和 `Buttons`，另保留 `schema-version` 作为 loader 元数据。键名解析可以兼容大小写，但默认资源统一使用上述写法。

需要避免把 `CREATE`、`OPEN_DOMINION`、`PREVIOUS_PAGE` 等业务意图定义为按钮 `type`。它们已经由统一动作 `dominion:`、`open:`、`page:` 表达；若再建立一套业务 type，TUI 将与 CUI/Dialog 重新产生不同动作协议。

第一版 `type` 只描述组件形态：

| `type` | 用途 |
| --- | --- |
| `BUTTON` | 执行统一 `actions` 列表 |
| `LIST` | 从共享 data source 展开动态条目 |
| `URL` | 客户端打开固定 URL |
| `COPY` | 客户端复制固定文本 |

`BUTTON` 就是自定义动作按钮，因此不再额外定义 `CUSTOM`。上一页、下一页、创建领地和打开页面都使用 `BUTTON + actions`。

### 13.2 示例

```yaml
schema-version: 1

Layout:
  - '&8----------- &6&l我的领地 &8-----------'
  - '{dominion-list}'
  - '&8--------------------------------'
  - '{previous-page} &7第 {page.current}/{page.total} 页 {next-page}'
  - '{create} {close}'
  - '&7帮助文档: {documentation}'

Buttons:
  dominion-list:
    type: LIST
    source: entries
    rows: 5
    empty: '&7你还没有领地。'
    Layout:
      - '&8- {manage} &7所有者: &f{entry.owner} {teleport}'
    Buttons:
      manage:
        type: BUTTON
        text: '&a{entry.name}'
        hover: '&7点击管理该领地'
        actions:
          - 'open: dominion_manage dominion={entry.id}'
      teleport:
        type: BUTTON
        text: '&b[传送]'
        hover: '&7传送到该领地'
        actions:
          - 'dominion: teleport dominion={entry.id}'

  previous-page:
    type: BUTTON
    text: '&a[上一页]'
    hover: '&7前往上一页'
    disabled-text: '&8[上一页]'
    actions:
      - 'page: previous'

  next-page:
    type: BUTTON
    text: '&a[下一页]'
    hover: '&7前往下一页'
    disabled-text: '&8[下一页]'
    actions:
      - 'page: next'

  create:
    type: BUTTON
    text: '&a[创建领地]'
    hover: '&7开始创建领地'
    actions:
      - 'open: dominion_create'

  close:
    type: BUTTON
    text: '&c[关闭]'
    actions:
      - 'close'

  documentation:
    type: URL
    text: '&b[打开文档]'
    hover: '&7在浏览器中打开 Dominion 文档'
    url: 'https://dominion.lunadeer.cn/'
```

权限列表使用相同结构，只更换 source、entry 模板和动作：

```yaml
Layout:
  - '&8----------- &6权限列表 &8-----------'
  - '{flag-list}'
  - '{previous-page} &7{page.current}/{page.total} {next-page}'

Buttons:
  flag-list:
    type: LIST
    source: flags
    rows: 8
    empty: '&7没有可显示的权限。'
    Layout:
      - '{toggle} &7{entry.description}'
    Buttons:
      toggle:
        type: BUTTON
        text: '{entry.state-color}[{entry.name}]'
        hover: '&7点击切换该权限'
        actions:
          - 'dominion: set-env-flag flag={entry.id} value={entry.next-value}'
          - 'refresh'
```

### 13.3 占位符与变量规则

TUI 同时存在按钮占位符和文本变量，必须在 schema 中消歧：

```text
{create}                 button id，必须存在于当前 Buttons
{dominion-list}          LIST id，必须存在于当前 Buttons
{page.current}           namespaced text variable
{entry.name}             LIST entry variable
```

规则：

- button id 仅允许 `[a-z0-9][a-z0-9_-]*`，默认资源使用小写 kebab-case。
- 带 `.` 的占位符固定视为变量，不允许作为 button id。
- 不带 `.` 的占位符必须对应当前作用域的 `Buttons`；找不到时 reload 失败。
- LIST 内层 `Buttons` 只在该 LIST 的 `Layout` 中可见，不能从外层直接引用。
- LIST 内变量仅允许 `{entry.*}`、`{page.*}` 和外层只读变量。
- 若需要输出字面量花括号，使用 `{{` 和 `}}` 转义。
- 全角 `｛`/`｝` 不是合法占位符边界；validator 应给出针对性的拼写提示。
- parser 先识别静态占位结构，再解析文本变量，变量值不能注入新按钮或 clickable markup。

### 13.4 LIST 展开规则

`LIST` 用于领地列表、成员列表、权限列表等动态内容：

- LIST 占位符必须是该 `Layout` 行除空白外的唯一内容，避免多行展开时前后文本归属不清。
- `rows` 表示每页 entry 数量，不是最终聊天消息行数。
- 第一版每个 entry 的内层 `Layout` 只允许一行；后续若支持多行 entry，`rows` 仍按 entry 计数。
- `rows` 建议限制为 1 至 20，防止一次打开菜单刷出过多聊天消息。
- 一个菜单第一版只允许一个分页 LIST，保证 `{page.*}` 和 `page:` 动作只有一个目标。
- `empty` 在 source 无数据时替代整个 LIST，不能为空字符串。
- 每个 entry 的按钮 callback 保存该 entry 的 trusted arguments，不能从客户端文本恢复 id。
- 数据变化导致当前页越界时，router 回退到最后一个有效页并重新渲染。

若将来存在不分页的小型列表，可增加 `paginated: false`，但仍必须设置服务端最大条目数。

### 13.5 BUTTON、URL 与 COPY

`BUTTON` 的最小字段：

```yaml
example:
  type: BUTTON
  text: '&a[按钮]'
  hover: '&7悬停文本'
  actions:
    - 'tell: &a已点击。'
```

规则：

- `text` 必填，`hover` 可选，`actions` 至少一项。
- `actions` 使用全局统一动作协议，不允许在 TUI renderer 内按按钮名称硬编码业务。
- `BUTTON` 必须在内联 `actions` 与共享 `action-group` 引用之间二选一，二者最终解析为同一种动作列表。
- 内联 `actions` 适合语言相关反馈和当前菜单独有动作；可复用业务、导航及分页动作优先定义为 `_shared.action-groups`。
- `disabled-text` 用于分页边界或业务不可用状态；disabled 组件不注册 callback。
- 可选 `hidden-when-disabled` 用于完全隐藏按钮，但默认显示 disabled 文本，避免行布局突然难以理解。
- `URL` 只接受校验后的 URL，`COPY` 只接受固定文本或安全文本变量，两者不创建服务端 callback。
- 不允许一个组件同时配置 `actions`、`url` 和 `copy`。

分页按钮无需专用 type：

```yaml
previous-page:
  type: BUTTON
  text: '&a[上一页]'
  disabled-text: '&8[上一页]'
  actions:
    - 'page: previous'
```

renderer 根据 `PageSlice` 自动禁用首页的 previous 和末页的 next。validator 可通过 action type 识别分页按钮，不需要额外的 `PREVIOUS_PAGE` type。

### 13.6 Layout 渲染规则

- `Layout` 按配置顺序发送，每个普通模板行生成一条聊天消息。
- 空字符串生成空行；不使用 `...` 作为特殊语法。
- 普通文本、颜色代码和变量保持原位置，按钮 Component 原位拼接。
- 一个模板行可引用多个普通按钮，例如 `'{create} {close}'`。
- LIST 展开完成后再继续渲染下一条外层 Layout。
- 同一普通按钮重复出现时可以复用解析后的 action 定义，但必须为每个位置签发当前 session 的 callback。
- title、divider、spacer 和 row 不再作为额外 component type；它们直接通过可视化文本行表达。
- 不承诺按像素居中，Minecraft 字体不是等宽字体。
- 每行设置解析后 Component 数和文本长度上限，超限在 reload 或 render 时报告明确路径。

### 13.7 TUI callback

新 TUI 不再创建随机 `SecondaryCommand`。兼容 transport：

```text
modern custom click
  -> callback token
legacy RUN_COMMAND
  -> /dominion ui <callback-token>
```

固定命令 handler 只做：

1. sender 必须是 Player。
2. token 格式和长度校验。
3. 从 session 恢复 callback。
4. 核对玩家、过期时间和一次性状态。
5. 交给 `ActionExecutor`。

它不接受 menu id、领地 id 或动作字符串作为命令参数。

## 14. Dialog 配置规范

### 14.1 示例

```yaml
schema-version: 1
Title: '&6创建领地'

Settings:
  can_escape: true
  pause: false
  after_action: WAIT_FOR_RESPONSE

Body:
  intro:
    type: message
    text: '&7请输入新领地名称。'
    width: 260

Inputs:
  dominion_name:
    type: text
    label: '&f领地名称'
    max_length: 32
    initial: ''

Bottom:
  type: confirmation
  yes:
    text: '&a创建'
    tooltip: '&7创建领地'
    actions:
      - 'dominion: create name={input.dominion_name}'
      - 'open: dominion_list'
  no:
    text: '&c取消'
    actions:
      - 'open: main'
```

### 14.2 结构范围

参考 KaMenu 的结构，但第一阶段只支持：

```text
Title
Settings
Body.message
Inputs.text
Inputs.boolean
Inputs.number_range
Inputs.single_option
Bottom.notice
Bottom.confirmation
Bottom.multi
```

不支持 `Events`、`Tasks`、`JavaScript`、条件节点和 Item Body。若以后支持 Paper Item Body，应作为 renderer capability 增强，Spigot 默认不可用。

### 14.3 Settings

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `can_escape` | `true` | 是否允许 ESC 关闭 |
| `pause` | `false` | 明确关闭单人暂停语义 |
| `after_action` | `CLOSE` | `CLOSE/NONE/WAIT_FOR_RESPONSE` |

当 `after_action=WAIT_FOR_RESPONSE` 时，validator 必须确认每个服务端按钮 action 列表最终存在可确定的生命周期处理，或由 executor 的默认 completion policy 补全关闭/刷新。静态 URL/copy 按钮不应使用需要服务端响应的策略。

### 14.4 Inputs

所有 input 建立元数据表：

```text
key
type
required
max length / numeric range / option whitelist
normalizer
business variable name
```

Spigot 返回值通常按 JSON string 读取；Paper typed getter 也必须先规范化为同一个 `Map<String, String>`，然后进入共享 action context。

平台 adapter 不执行业务校验，只处理：

- 缺失/额外字段。
- JSON/typed value 到字符串的规范化。
- 文本 trim、换行和长度上限。
- option 是否属于定义中的 id。

### 14.5 Paper 与 Spigot 双 renderer

```text
DialogRenderer shared interface
├── PaperDialogRenderer
│   └── Paper Dialog API / Adventure / callback
└── SpigotDialogRenderer
    └── Bungee Dialog API / JsonElement / PlayerCustomClickEvent
```

共享接口不得出现以下类型：

```text
io.papermc.paper.dialog.*
DialogResponseView
net.md_5.bungee.api.dialog.*
com.google.gson.JsonElement
```

启动时按平台能力延迟加载实现。不要仅以 classpath 某个类存在判断全部能力，应组合服务器类型、版本和必要方法探测。

## 15. 数据源与分页

### 15.1 `MenuDataProvider`

动态菜单不应在 renderer 中直接查 CacheManager。建议接口：

```java
CompletionStage<PageSlice<MenuEntry>> load(
        MenuContext context,
        int page,
        int pageSize
);
```

`MenuEntry` 建议包含：

```text
stable id
display variables
trusted action arguments
available action groups
```

`display variables` 可用于文本；`trusted action arguments` 只保存服务端创建的标识。renderer 不需要知道 entry 是 DominionDTO、PlayerDTO 还是 GroupDTO。

### 15.2 容量差异

- CUI page size = layout 中动态 source symbol 的槽位数。
- TUI page size = `LIST.rows`，或全局 TUI 默认值。
- Dialog page size = multi action 可接受的按钮数/菜单配置值。
- provider 使用 renderer 提供的 page size 查询同一数据集。
- `{page.total}` 由 `totalItems/pageSize` 计算。
- 空 page 或删除导致页码越界时回退到最后一个有效页。

### 15.3 可用操作

列表 entry 可根据当前玩家权限暴露 `available action groups`。renderer 根据动作组是否可用决定隐藏或 disabled：

```text
manage
teleport
delete
edit-flags
```

隐藏按钮只是表现逻辑。点击后的 handler 仍必须重新校验。

## 16. UI 路由和偏好

### 16.1 UI 类型

长期建议把玩家可选类型扩展为：

```text
TUI
CUI
DIALOG
```

`BY_PLAYER` 继续只作为全局选择策略，不应持久化为实际玩家 renderer。迁移数据库前需核对现有 `PlayerDOO.getUiPreference()` 的特殊回退行为。

### 16.2 能力选择

```text
resolve requested/preferred UI
  -> player/server supports it?
  -> menu has corresponding definition?
  -> renderer loaded successfully?
  -> render
  -> otherwise deterministic fallback
```

建议 fallback：

```text
DIALOG -> CUI -> TUI
CUI    -> TUI
TUI    -> CUI
```

Bedrock 玩家继续默认 CUI，除非后续实测确认目标客户端完整支持 Dialog 并明确调整策略。

### 16.3 路由刷新

`UiRouter.refresh(context)` 使用原 route 和原 `sourceUiType`，不会重新读取玩家偏好。只有用户显式切换 UI 或重新从根命令进入时才重新解析偏好，避免动作后突然从 Dialog 跳回 CUI。

## 17. 线程与调度

### 17.1 原则

- YAML 文件读取、结构校验和不触碰 Bukkit 对象的解析可异步执行。
- Player、Inventory、Dialog、Adventure audience 和大多数 Bukkit API 调用必须通过项目 scheduler bridge 回到正确线程。
- Folia 上玩家相关 UI 打开/关闭应优先使用 entity scheduler，而不是 global scheduler。
- 数据 provider 的异步结果完成后，渲染前再次确认玩家在线且 session/route 请求仍是最新版本。
- executor 不假设 future completion thread；每个需要 Bukkit API 的 handler 自行通过 bridge 调度。

### 17.2 Scheduler 改进边界

现有 `Scheduler.runTask` 在 Paper/Folia 使用 GlobalRegionScheduler。UI 重构实施时应新增面向玩家的调度入口，例如 `runPlayerTask(Player, Runnable)`，其用途是确保玩家 UI 操作在玩家所属调度器执行。该改动属于 UI 底座所需，不应顺便重写项目所有调度逻辑。

## 18. 配置加载、缓存与 reload

### 18.1 目录

插件数据目录按“语言 -> 菜单类型 -> 菜单文件”组织：

```text
plugins/Dominion/uis/
├── _shared/
│   ├── main_menu.yml
│   ├── dominion_list.yml
│   ├── env_flags.yml
│   └── ...
├── zh_CN/
│   ├── text_menu/
│   │   ├── main_menu.yml
│   │   ├── dominion_list.yml
│   │   └── ...
│   ├── chest_menu/
│   │   ├── main_menu.yml
│   │   ├── dominion_list.yml
│   │   └── ...
│   └── dialog_menu/
│       ├── main_menu.yml
│       ├── dominion_list.yml
│       └── ...
└── en_US/
    ├── text_menu/
    ├── chest_menu/
    └── dialog_menu/
```

用户提出的主菜单路径即：

```text
plugins/Dominion/uis/zh_CN/chest_menu/main_menu.yml
```

类型目录固定为：

| 目录 | UI |
| --- | --- |
| `text_menu` | TUI 聊天文本菜单 |
| `chest_menu` | CUI Inventory 菜单 |
| `dialog_menu` | Mojang Dialog 菜单 |

规则：

- menu id 使用小写 snake_case，文件名就是 route 的 `menuId`。
- 同一 menu id 在 `_shared` 和各类型目录中保持一致。
- 每种语言可以独立调整布局、按钮文本、lore、hover、Dialog width 和条目模板。
- route、provider、可信参数和可复用业务 action groups 只存在于 `_shared`，不随语言复制。
- 类型菜单可以内联当前表现独有的反馈动作，也可以增加文档 URL/COPY，但不能覆盖 `_shared` 中 Dominion action 的实现。
- Console 文本继续使用现有 `languages/<code>.yml`，不建立 `console_menu`。

### 18.2 Locale 解析与回退

当前配置和 `LanguageCode` 使用 `en_us/zh_cn/jp_jp/zh_tw`。新 UI 目录采用用户给出的 locale 形式，例如 `zh_CN`。loader 需要显式兼容：

```text
zh_cn -> zh_CN
en_us -> en_US
jp_jp -> jp_JP
zh_tw -> zh_TW
```

locale 解析不得直接依赖 Linux 路径大小写。建议启动时建立规范化索引，并拒绝 `zh_CN` 与 `zh_cn` 两个目录同时映射到同一 locale，避免不同系统上行为不一致。

菜单解析顺序：

```text
requested locale + requested UI type
  -> fallback locale + requested UI type
  -> requested locale + fallback UI type
  -> fallback locale + fallback UI type
  -> configuration error
```

默认 fallback locale 为 `en_US`。当前 Dominion 只有全局 `Configuration.language`，第一版按全局语言选择目录；未来增加玩家 locale 时不需要改变目录结构。

### 18.3 默认资源与用户修改

内置资源和插件数据目录使用相同相对路径：

```text
core resources: uis/zh_CN/chest_menu/main_menu.yml
plugin data:    plugins/Dominion/uis/zh_CN/chest_menu/main_menu.yml
```

首次启动只释放缺失文件，不覆盖管理员修改。插件升级时：

- 新增菜单文件可以直接释放。
- 已有文件不自动覆盖。
- schema 变化通过 migration 或生成 `.new` 文件处理。
- `update_language` 不应无提示覆盖整个 `uis` 目录；UI 更新应使用独立确认命令。
- `_shared` 与类型菜单必须作为同一个 registry revision 原子加载。

### 18.4 schema version

每个文件必须有 `schema-version`。第一版为 `1`。loader 对未知更高版本拒绝加载并给出明确错误，避免静默误解配置。

### 18.5 原子 reload

```text
read all files
  -> parse temporary definitions
  -> validate references/actions/layouts
  -> all required menus valid?
      yes -> atomically replace registry + increment revision
      no  -> keep old registry + report errors
```

不能边读取边覆盖活动 registry。reload 成功后旧 revision session 全部失效；玩家再次点击时提示重新打开菜单。

### 18.6 校验项目

- menu id、目录名和引用一致。
- locale 和 menu type 目录合法且不存在规范化冲突。
- `_shared.action-groups` 与类型菜单的 `action-group` 引用一致。
- route 参数声明完整。
- layout 行数、列数和 symbol 合法。
- symbol 有定义，或明确允许为空。
- source provider 存在。
- action type 已注册且参数格式有效。
- action group 存在且无递归。
- Dialog input key 唯一，范围/option 合法。
- `open` 目标菜单存在。
- 平台不支持的组件输出 warning 或 error，策略可配置。

## 19. 错误处理和日志

用户侧只显示简洁、可翻译的信息：

```text
菜单已过期，请重新打开。
该操作当前不可用。
输入格式不正确。
菜单加载失败，请联系管理员。
```

日志必须包含：

```text
menuId
sourceUiType
config path
action type / callbackId
player UUID
session short id
exception
```

不要在日志中输出完整输入内容、完整 callback token 或可能敏感的聊天文本。

配置错误在 reload 时聚合报告，避免同一错误在每次玩家打开菜单时刷屏。运行期异常按 menu revision + config path 做限频。

## 20. 与旧架构的迁移策略

### 阶段 0：锁定行为

- 以 `TUI-CUI-Architecture-Research.md` 为现状基线。
- 列出 22 个页面的 route 参数、数据源、按钮和修改后刷新目标。
- 选择一个只读列表页和一个含输入/修改的页面作为样板。

交付标准：每个样板页面的 TUI/CUI 当前行为清单完整。

### 阶段 1：统一动作底座

- 实现 `ActionSpec/Parser/Registry/Executor/Result`。
- 实现反馈动作、导航动作和少量 `dominion:` handler。
- 实现变量命名空间和严格顺序 future 链。
- 暂不修改现有 UI 页面。

交付标准：动作解析、顺序、失败终止和导航终止有单元测试。

### 阶段 2：路由、session 和 callback transport

- 实现 `MenuRoute/UiRouter/UiSessionManager`。
- 实现固定 TUI callback command，停止新页面创建动态 SecondaryCommand。
- 实现新 CUI holder/listener 的 session callback。
- 保留旧 manager 供未迁移页面使用。

交付标准：伪造 token、跨玩家 token、过期 token和重复点击均不能执行。

### 阶段 3：配置仓库和首个 TUI/CUI 页面

- 实现 `MenuRepository`、validator 和原子 reload。
- 迁移一个简单页面，例如 MainMenu。
- 迁移一个分页列表，例如 DominionList。
- 同一 route 同时用 TUI/CUI renderer 展示。

交付标准：两种 UI 的动作调用同一 handler；不存在页面级双份业务 callback。

### 阶段 4：输入与修改型页面

- 建立 domain operation，拆离现有 `*Command` 中的 UI 跳转。
- CUI/TUI 暂时可继续使用聊天 Inputter，但提交后进入统一 action context。
- 迁移 rename、message、flag 等典型修改页面。

交付标准：命令与 UI action 共享同一 domain operation，修改结果由 router 刷新。

### 阶段 5：Dialog 双平台接入

- 加入 Dialog capability 和共享定义。
- 分别实现 Spigot/Paper renderer 与 callback adapter。
- Dialog input 规范化到共享 map。
- 实现 WAIT_FOR_RESPONSE 完成和 timeout 处理。

交付标准：同一菜单在 Spigot/Paper 上都能打开、提交和跳转；平台 API 不发生类加载错误。

### 阶段 6：逐页迁移和清理

- 按业务域逐页迁移剩余 UI。
- 页面迁移完成后删除对应旧 `showTUI/showCUI` 实现。
- 所有页面迁移后再移除动态 TUI command 和旧 CUI closure manager。
- 最后评估是否简化 `AbstractUI`。

交付标准：全仓库不再注册 `tui_btn_future_*`；三种玩家 UI 均从 router 进入。

## 21. 推荐首批样板页面

建议顺序：

1. `MainMenu`：验证静态布局、open/close 和 UI fallback。
2. `DominionList`：验证动态 source、分页、entry trusted arguments 和 CUI 左右键到 Dialog 双按钮映射。
3. `DominionCreate`：验证 TUI/CUI 聊天输入与 Dialog TextInput 汇入统一 action。
4. `EnvFlags`：验证重复 flag entry、权限重校验、修改后 refresh。

不要先迁移成员/权限组管理等复杂页面，否则无法快速判断问题来自底座还是业务组合。

## 22. 测试策略

### 22.1 单元测试

项目当前没有自动测试。此次共享底座纯 Java 比例较高，建议首次引入 JUnit，仅覆盖新模块，不强行补全旧代码测试。

必测：

- action parser 的合法/非法输入。
- action 串行顺序和异步完成顺序。
- `CONTINUE/OPEN/REFRESH/CLOSE/FAILURE` 终止语义。
- action group 未找到和递归拒绝。
- 变量优先级、缺失变量和文本转义。
- route 参数白名单。
- session 过期、跨玩家、重复消费和 revision 失效。
- CUI layout 与 TUI `Layout/Buttons` validator。
- Dialog input 类型、范围和 option 白名单。
- page size、边界页和空列表。

### 22.2 集成/手动测试矩阵

| 环境 | Java | 必测内容 |
| --- | --- | --- |
| Spigot 1.20.1 | 兼容 JDK | TUI/CUI、legacy callback、无 Dialog fallback |
| Paper 1.20.1 | 兼容 JDK | TUI/CUI、Adventure 文本 |
| Spigot 1.21.6+ | 对应最低 JDK | Spigot Dialog、JsonElement 输入、custom click |
| Paper 1.21.6+ | 对应最低 JDK | Paper Dialog、typed response、callback lifetime |
| Paper/Folia 26.2 | Java 25+ | entity scheduler、Dialog、分页和业务动作 |
| Bedrock bridge | 对应服务端 | CUI 默认选择和点击行为 |

每个平台验证：

- 主菜单打开和 fallback。
- 静态/动态列表、空列表、首页和末页。
- 左右键 CUI 与 Dialog 独立按钮语义一致。
- tell/actionbar/title/toast/hovertext 顺序。
- URL/copy 静态动作。
- 输入缺失、超长、非法字符、范围越界和伪造 option。
- 权限在菜单打开后被撤销。
- 目标领地在菜单打开后被删除或转移。
- 双击、重复数据包、旧 token、跨玩家 token。
- reload 时已有菜单 session。
- 玩家退出、切换世界、服务端关闭。
- `WAIT_FOR_RESPONSE` 成功、失败、异常和 timeout。

## 23. 验收标准

重构完成至少满足：

1. 同一业务 callback 在三种 UI 中引用相同动作 id。
2. renderer 和 listener 中不存在 Dominion 业务 switch。
3. 页面跳转只通过 `UiRouter`，业务 handler 不调用具体 renderer。
4. 不再为 TUI 按钮动态注册随机命令。
5. 客户端 payload 不直接决定资源目标或权限。
6. 每个 session 有玩家绑定、有效期、revision 和清理路径。
7. 菜单 reload 是原子的，错误配置不覆盖可用 registry。
8. Spigot/Paper Dialog 类型隔离，低版本加载插件时不解析高版本类。
9. CUI drag/click/close 生命周期有明确处理。
10. 所有新增类和方法有用途明确的英文 Javadoc。

## 24. 尚需在实施前确认的问题

以下问题不阻塞蓝图，但应在对应阶段做明确决策：

1. 玩家级 locale 第一版是否启用；目录结构已经支持，但当前配置只有全局语言。
2. 玩家 UI 偏好数据库是否直接增加 `DIALOG`，以及旧值迁移策略。
3. TUI custom click 在各目标 Adventure/Spigot 版本上的能力探测方式。
4. Dialog 最低服务端版本是严格 1.21.6，还是按实际 API 方法探测。
5. Paper renderer 是否提供 Item Body 增强，还是第一版与 Spigot 保持完全同构。
6. 普通 callback session 的默认有效期和最大活动 session 数。
7. `toast` 是否沿用临时 advancement，及其在 Folia/Spigot 的线程策略。
8. PlaceholderAPI 是否仅做文本替换，以及是否允许它参与 action 参数。

## 25. 最终建议

此次重构应从“统一动作和可信回调”开始，而不是先写 YAML renderer。若先完成 Dialog 或把旧闭包直接搬进配置，最终仍会形成三套业务逻辑。

建议实施主线固定为：

```text
Action contract
  -> Route/session
  -> Repository/validator
  -> TUI+CUI sample pages
  -> Domain operation extraction
  -> Spigot/Paper Dialog adapters
  -> Remaining page migration
```

配置可以高度自定义表现，但动作集合必须保持小、稳定、白名单化。这样 Dominion 的菜单可维护性来自明确的业务入口，而不是来自一个功能无限扩张的通用脚本引擎。
