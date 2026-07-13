# Dominion TUI/CUI 列表清单与差异报告

> 研究日期：2026-07-11  
> 研究范围：`core/src/main/java/cn/lunadeer/dominion/uis` 下全部 TUI/CUI 页面  
> 目的：为配置化 TUI、CUI 和后续 Dialog 共用列表数据源提供迁移基线  
> 本报告只分析现有行为，不修改 Java 实现

## 1. 总体结论

源码中不能把所有 `ListView` 都理解为同一种动态列表。

检索结果：

| 项目 | 数量 |
| --- | ---: |
| 使用 TUI `ListView.create(...)` 的类 | 22 |
| 其中真正由集合生成条目的 TUI | 17 |
| 仅借用 TUI ListView 作为固定行/分页容器 | 5 |
| 使用 CUI `ChestListView` 的类 | 19 |
| 其中真正由集合生成条目的 CUI | 18 |
| 仅借用 CUI ChestListView 放固定动作 | 1 |

现有列表应分为四类：

1. **资源与选择列表**：领地、玩家、成员、权限组、模板、称号、复制来源。
2. **层级或组合列表**：领地树、Residence 树、权限组及其成员。
3. **Flag 设置列表**：环境、访客、成员、权限组、模板，共 5 类。
4. **固定动作菜单**：主菜单、领地管理、复制类别、尺寸信息、尺寸调整。

因此新 TUI `LIST` 不能内置为“权限列表”。`LIST` 只是渲染某个 data source 的通用容器；flag 设置只是其中一组 source。

## 2. 当前列表基础设施

### 2.1 TUI `ListView`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/utils/stui/ListView.java
core/src/main/java/cn/lunadeer/dominion/utils/stui/components/Pagination.java
```

当前行为：

- 所有调用点都使用 `ListView.create(10, button)`，固定每页 10 条 `Line`。
- 标题和 subtitle/navigator 不计入分页。
- 通过 `view.add(...)` 添加的操作行、空行、section 标题和数据行全部计入 10 条容量。
- 空列表自动加入文本 `...`。
- 上一页/下一页通过 `ListViewButton.function(page)` 重开整个页面。
- 首页/末页箭头显示为 disabled 普通文本。
- 页码小于 1 或 offset 大于总行数时回到第一页。

边界问题：

- 判断使用 `offset > lines.size()`，而不是 `>=`。当总行数刚好是 10 的倍数且请求下一页时，可能显示一个页码超出总页数的空页。
- 分页对象只知道扁平 `Line` 数量，不知道某行是工具栏、section、树父节点还是数据 entry。
- 层级列表可能把父节点与子节点切到不同页。
- section 标题可能出现在页面末尾，而其内容出现在下一页。

### 2.2 CUI `ChestListView`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/utils/scui/ChestListView.java
core/src/main/java/cn/lunadeer/dominion/utils/scui/configuration/ListViewConfiguration.java
```

当前行为：

- page size 等于 layout 中 `itemSymbol` 出现次数。
- 绝大多数列表 layout 有 21 个 `i` 槽位。
- `DominionManage` 只有 8 个 `i` 槽位。
- 固定 symbol 按钮不计入列表容量。
- 通过 `addItem(...)` 加入的“创建/添加”按钮会和数据 entry 一起分页并占用容量。
- 上一页/下一页在同一 `ChestListView` 中调整页码并重新 `open()`。
- 当前页小于 1 会直接抛出异常。
- 当前页大于总页数不会自动回退，只会打开空页。

当前绝大多数列表 item 都使用 `itemSymbol=i`。新架构不应继续依赖 ItemStack symbol 或 list index 恢复业务目标，应由 session 保存 entry 的可信参数。

## 3. 完整动态列表清单

### 3.1 领地相关列表

| UI | TUI 数据和表现 | CUI 数据和表现 | entry 操作 |
| --- | --- | --- | --- |
| `DominionList` | 玩家当前服领地树；当前服管理员领地 section；其他服务器领地 section | 当前服自有、管理员、其他服领地平铺 | 自有：删除/管理/传送；管理员：管理或传送；其他服：传送 |
| `AllDominion` | 全部领地树 | 全部领地平铺 | 管理、传送；TUI 还直接提供删除 |
| `AllDominionOfPlayer` | 指定玩家的领地树 | API 返回的指定玩家全部领地平铺 | 管理、传送；TUI 还直接提供删除 |
| `DominionCopy` | 当前玩家拥有的领地，排除目标领地 | 相同 | 按 copy type 复制环境、访客、成员或权限组设置 |

#### `DominionList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/DominionList.java
```

TUI 数据结构：

```text
current server owned dominion tree
  -> recursive children, depth rendered as " | " prefix
current server admin dominions
  -> blank line + section title + flat entries
each other server
  -> blank line + server section title + accessible entries
```

TUI owned tree entry 包含：

```text
DELETE -> DominionOperateCommand.delete
MANAGE -> DominionManage.show
TP     -> teleportToDominion
```

CUI 将三类 entry 全部放入同一个平铺列表，但使用不同 ItemStack 外观：

- 自有领地：左键管理，右键传送。
- 当前服管理员领地：左键管理，右键传送。
- 其他服领地：仅右键传送。
- 其他服数据按 dominion id 去重。

迁移要求：该页面不是一个简单 source，至少包含 `owned-tree`、`admin-current-server`、`accessible-other-server` 三个逻辑 section。若第一版每页只允许一个 `LIST`，应由一个组合 provider 产出带 `entry.kind`、`entry.depth`、`entry.server` 的统一序列，而不是在 YAML 中配置三个独立分页器。

#### `AllDominion` 与 `AllDominionOfPlayer`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/AllDominion.java
core/src/main/java/cn/lunadeer/dominion/uis/AllDominionOfPlayer.java
```

两者的 TUI 复用 `DominionList.BuildTreeLines(...)`，因此连同普通玩家页面的删除、管理、传送动作一起复用。CUI 使用平铺 DTO 列表，左键管理、右键传送。

建议 provider：

```text
dominions.all
dominions.by-player
```

provider 应返回父子关系或 `depth`，renderer 决定显示为缩进树还是平铺；业务 action 不应由 `BuildTreeLines` 共享产生。

#### `DominionCopy`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/copy/DominionCopy.java
```

route 参数：

```text
target dominion
copy type: ENVIRONMENT / GUEST / MEMBER / GROUP
page
```

source 是当前玩家拥有的领地，过滤目标领地自身。entry action 根据可信 route 中的 copy type 调用对应 CopyCommand，不能允许客户端提交任意 copy type。

建议 provider：

```text
dominions.copy-sources
```

## 4. Flag 设置列表

这里的“权限列表”应明确称为 **flag 设置列表**。当前共有 5 种页面：

| UI | flag source | 额外规则 | action |
| --- | --- | --- | --- |
| `EnvFlags` | `Flags.getAllEnvFlagsEnable()` | 仅启用的环境 flag | `DominionFlagCommand.setEnv` |
| `GuestFlags` | `Flags.getAllPriFlagsEnable()` | 跳过 `ADMIN` | `DominionFlagCommand.setGuest` |
| `MemberFlags` | `Flags.getAllPriFlagsEnable()` | TUI 在成员为 ADMIN 时只显示 ADMIN；有模板入口 | `MemberCommand.setMemberPrivilege` |
| `GroupFlags` | `Flags.getAllPriFlagsEnable()` | TUI 在组为 ADMIN 时只显示 ADMIN；有重命名入口 | `GroupCommand.setGroupFlag` |
| `TemplateFlags` | TUI: `getAllPriFlagsEnable()`；CUI: `getAllPriFlags()` | 有重命名和删除入口 | `TemplateCommand.setTemplateFlag` |

### 4.1 共同表现

- 每个 flag entry 展示当前 boolean 状态、显示名和说明。
- 点击后反转当前值并重开/刷新原页。
- TUI 使用 `☑/☐` 和 hover description。
- CUI 使用 flag 对应 material、状态 lore 和折行 description。
- TUI 每页固定 10 行；CUI 默认每页 21 个 flag。

### 4.2 当前不一致

#### 成员 ADMIN 状态

`MemberFlags.showTUI(...)`：

```text
member ADMIN=true
  -> only ADMIN entry
otherwise
  -> all enabled privilege flags
```

`MemberFlags.showCUI(...)` 无此分支，始终遍历全部启用 privilege flag。

#### 权限组 ADMIN 状态

`GroupFlags.showTUI(...)` 与成员 TUI 相同：ADMIN=true 时只显示 ADMIN。CUI 始终显示全部启用 privilege flag。

#### 模板 flag 范围

- TUI 使用 `Flags.getAllPriFlagsEnable()`。
- CUI 使用 `Flags.getAllPriFlags()`。

因此禁用的 flag 可能仍出现在模板 CUI，但不出现在模板 TUI。

#### 访客 ADMIN 过滤与页码

GuestFlags TUI/CUI 都跳过 ADMIN；但 CUI 计算 entry 所属页时仍使用原始 flag index。若 ADMIN 位于中间或开头，页码计算可能与过滤后的实际槽位偏移不一致。

### 4.3 新架构建议

不要为每个 flag 页面建立独立 `LIST` type。使用不同 provider id：

```text
flags.environment
flags.guest
flags.member
flags.group
flags.template
```

统一 `MenuEntry` 字段可包含：

```text
entry.id
entry.name
entry.description
entry.material
entry.value
entry.next-value
entry.state-text
entry.state-color
```

但 provider 负责各页面的过滤规则和 ADMIN 特殊状态。实施前必须决定以 TUI 还是 CUI 的现有行为为准，不能在 renderer 内继续分叉。

## 5. 成员与玩家列表

### 5.1 `MemberList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/member/MemberList.java
```

数据差异：

- TUI 使用 `dominion.getMembers()`。
- CUI 直接调用 `MemberDOO.selectByDominionId(...)`，注释说明用于避免缓存更新延迟。

TUI 第一条分页行是“添加成员”，随后每个成员显示：

```text
[A] admin
[N] normal
[B] no MOVE privilege
[G] belongs to group
```

entry 操作：

- 打开成员 flag 设置。
- 从领地移除成员。
- 非 owner 管理员不能修改/移除其他 ADMIN 成员，TUI 会 disabled。
- 属于权限组的成员不能单独编辑 flag；TUI 禁用设置按钮。

CUI 第一项同样是“添加成员”，会占用第一页一个列表槽位。成员 entry：

- 左键：仅 `groupId == -1` 时打开 MemberFlags。
- 右键：移除成员。
- CUI 没有复制 TUI 的 owner/admin disabled 表现，最终是否拒绝依赖业务命令校验。

建议 provider：

```text
members.by-dominion
```

“添加成员”应放在外层 `Buttons` 工具栏，不应作为第一条 `MenuEntry`，否则配置的 `rows` 与实际成员容量不一致。

### 5.2 `SelectPlayer`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/member/SelectPlayer.java
```

source 为 `PlayerDOO.all()`，点击调用 `MemberCommand.addMember`。

关键差异：

- TUI 展示全部已知玩家，没有排除已是该领地成员的玩家。
- CUI 会构造成员 UUID 列表并过滤已有成员。
- TUI/CUI 都提供搜索输入入口，但位置不同：TUI subtitle，CUI 固定 compass 槽位。

建议 provider：

```text
players.member-candidates
```

provider 应统一排除已有成员，并支持后续 search query，而不是由 CUI renderer 自行过滤。

### 5.3 `SelectMember`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/group/SelectMember.java
```

source 为领地成员数据库查询结果，过滤 `groupId == -1`，即只显示尚未加入任何权限组的成员。点击后加入目标组。

route 需要保存：

```text
dominion id
target group id
back page
current selection page
```

建议 provider：

```text
members.ungrouped
```

### 5.4 `GroupManage` 的成员列表

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/group/GroupManage.java
```

该页面仅支持 CUI；TUI 方法直接抛出 `UnsupportedOperationException`。

CUI 固定按钮：返回、权限设置、重命名、删除。动态 items：

1. 第一项为添加成员。
2. 后续为 `group.getMembers()`。
3. 点击成员将其移出组，然后重开计算出的原页。

建议 provider：

```text
members.by-group
```

新 TUI 不必继续把该能力塞进 GroupList；可以和 CUI/Dialog 一样提供独立 GroupManage route，从而减少组合列表复杂度。

## 6. 权限组列表

### 6.1 `GroupList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/group/GroupList.java
```

TUI 是组合/层级列表：

```text
create group row
group row
  -> delete
  -> flag settings
  -> add member
  nested member row(s)
    -> remove from group
blank row
next group...
```

所有 group 行、member 行和 blank 行都进入同一个每页 10 行的 ListView，因此一个组可能被拆跨页。

CUI 只列出权限组：

- 第一 item 是创建权限组。
- 每个 group item 显示成员数量。
- 点击进入独立 `GroupManage`。

建议拆成：

```text
groups.by-dominion
members.by-group
```

三种 UI 统一使用 GroupList -> GroupManage 两级 route。TUI 若仍希望紧凑展示成员，可让 group entry 额外显示成员数量或名称摘要，但不建议在第一版 LIST 内嵌第二个可分页 source。

## 7. 模板列表

### 7.1 `TemplateList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/template/TemplateList.java
```

source 为 `TemplateDOO.selectAll(player UUID)`。

TUI：

- 创建模板是第一条分页行。
- 每个 entry 有删除和设置两个动作。

CUI：

- 创建模板是第一 item，占用列表容量。
- 模板 entry 只打开 TemplateFlags。
- 删除操作位于 TemplateFlags 页固定按钮。

建议 provider：

```text
templates.owned
```

创建按钮移到外层工具栏。删除可保留为 TUI entry 快捷动作，但三种 UI 的核心 route 应一致。

### 7.2 `SelectTemplate`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/dominion/manage/member/SelectTemplate.java
```

source 同样是玩家模板列表，点击后将模板应用到指定成员。CUI 应用后显式返回 MemberFlags；TUI 只调用业务命令，后续页面取决于命令内部行为。

建议 provider：

```text
templates.member-apply-candidates
```

route 必须可信保存目标 dominion 和 member，不能由模板按钮 payload 自由提交。

## 8. 称号列表

### 8.1 `TitleList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/TitleList.java
```

source 由两部分合并：

1. `PlayerCache.getPlayerGroupTitleList(player UUID)`。
2. 玩家拥有领地中的全部 group。

当前使用中的 group id 来自 `PlayerDTO.getUsingGroupTitleID()`。entry 状态：

- 未使用：点击 `GroupTitleCommand.useTitle(group id)`。
- 正在使用：点击 `GroupTitleCommand.useTitle(-1)` 取消。
- 展示来源领地。

建议 provider：

```text
titles.available-group-titles
```

provider 应负责合并、去重、过滤已删除 group，并标记 `entry.active`。

## 9. Residence 迁移列表

### 9.1 `MigrateList`

源码：

```text
core/src/main/java/cn/lunadeer/dominion/uis/MigrateList.java
```

source 取决于权限：

- 管理员：全部 Residence 数据，并显示 migrate all。
- 普通玩家：自己的 Residence 数据。

TUI 递归展开完整 ResidenceNode 树：

- 根 Residence 可点击迁移。
- 子 Residence 显示但按钮 disabled，说明会随父 Residence 一起迁移。
- 管理员 migrate all 是第一条分页行。

CUI 只遍历 `res_data` 顶层节点，不展示子 Residence。管理员 migrate all 使用固定 symbol，不占列表容量。

建议 provider：

```text
residence.migration-tree
```

entry 至少包含：

```text
entry.name
entry.depth
entry.root
entry.selectable
entry.disabled-reason
```

三种 renderer 应使用同一棵树或同一扁平化结果，避免 CUI 看不到 TUI 可见的数据结构。

## 10. 固定动作菜单

以下页面使用 ListView，但不是动态业务列表：

| UI | TUI | CUI | 结论 |
| --- | --- | --- | --- |
| `MainMenu` | 固定入口、条件入口和管理员 section，共用 10 行分页 | 普通 `ChestView` 固定布局 | 应使用普通 `Layout/Buttons`，不是 LIST |
| `DominionManage` | 11 个固定管理动作，10 行分页 | `ChestListView`，8 个 item 槽位，固定动作也被分页 | 应使用普通菜单布局或明确的 action collection，不需要 data provider |
| `CopyMenu` | 4 个 copy type 固定入口 | 普通 `ChestView` | 普通菜单，不是 LIST |
| `Info` | 5 条尺寸信息/操作；不是 `AbstractUI` | 无独立 CUI，信息整合进 DominionManage | 普通文本详情页 |
| `SetSize` | 6 个方向，每行 expand/contract | 普通 `ChestView` 固定方向按钮 | 固定重复结构，可由 Layout 明确配置 |

### 10.1 `DominionManage` 特殊情况

这是唯一只用固定动作却采用 `ChestListView` 的 CUI。其 layout 有 8 个 `i`，但代码加入 11 个 action item，因此会产生两页。

新配置可以有两种选择：

1. 使用普通 CUI 字符布局，把全部固定按钮放在明确槽位中。
2. 若确实需要分页，使用 `LIST` 的静态 entries provider。

建议选择普通布局。固定 action 不应伪装成动态数据 source。

## 11. TUI/CUI 行为差异汇总

| 页面 | 差异 |
| --- | --- |
| DominionList | TUI 保留领地树和 section；CUI 全部平铺，点击类型不同 |
| AllDominion / AllDominionOfPlayer | TUI 树形且带直接删除；CUI 平铺且仅左/右键操作 |
| MigrateList | TUI 展开子 Residence 并 disabled；CUI 只显示根节点 |
| GroupList | TUI 内嵌组成员；CUI 通过独立 GroupManage 展示成员 |
| GroupManage | 仅 CUI 存在；TUI 明确不支持 |
| SelectPlayer | TUI 不排除已有成员；CUI 排除 |
| MemberList | TUI 使用 DTO members，CUI 直接查数据库；disabled 规则表现不同 |
| MemberFlags | ADMIN 成员时 TUI 只显示 ADMIN，CUI 显示全部启用 flag |
| GroupFlags | ADMIN 组时 TUI 只显示 ADMIN，CUI 显示全部启用 flag |
| TemplateFlags | TUI 只显示启用 flag，CUI 显示全部 flag |
| TemplateList | TUI entry 直接提供删除；CUI 在详情页删除 |
| SelectTemplate | CUI 应用后显式返回；TUI 依赖命令内部后续行为 |
| 固定创建/添加入口 | 多数 TUI/CUI 将其作为第一条 list item，压缩第一页真实 entry 容量 |

这些差异必须在数据 provider/route 层统一决定，不应继续由 renderer 各自保留一份过滤和导航逻辑。

## 12. 建议的 LIST 能力模型

### 12.1 第一版 provider 清单

建议至少预留以下 provider id：

```text
dominions.player-accessible
dominions.all
dominions.by-player
dominions.copy-sources
members.by-dominion
members.by-group
members.ungrouped
players.member-candidates
groups.by-dominion
templates.owned
templates.member-apply-candidates
titles.available-group-titles
residence.migration-tree
flags.environment
flags.guest
flags.member
flags.group
flags.template
```

provider id 是内部白名单，不允许 YAML 指定任意 Java 类名。

### 12.2 `MenuEntry` 所需能力

通用 entry 不能只包含 `id/name`，至少需要：

```text
stable id
kind                    data / section / spacer
depth                   tree indentation
display variables
trusted action arguments
available operations
disabled reason
state                   active/enabled/admin/etc.
```

`section` 和 `spacer` 是否进入 page size 必须由统一 paginator 明确规定。建议它们属于渲染装饰，不消耗业务 entry 的 `rows`，并由 paginator 避免 section 与首个 entry 分页分离。

### 12.3 TUI `LIST` 配置扩展

普通平铺列表继续使用：

```yaml
flags:
  type: LIST
  source: flags.environment
  rows: 10
```

对于层级/分组数据，建议增加有限的 entry template 选择，而不是允许任意条件语言：

```yaml
dominion-list:
  type: LIST
  source: dominions.player-accessible
  rows: 10
  templates:
    data:
      - '{manage} {teleport} {entry.indent}{entry.name}'
    section:
      - '&6{entry.title}'
    empty:
      - '&7没有可显示的领地。'
```

这里的 `entry.kind` 由可信 provider 决定，YAML 只选择对应模板，不需要 KaMenu 条件表达式。

### 12.4 工具栏与列表分离

创建、搜索、返回、重命名、删除、选择模板等固定操作应位于外层 `Layout/Buttons`。`LIST.rows` 只计算业务 entry：

```yaml
Layout:
  - '{create} {search}'
  - '{entries}'
  - '{previous} {page.current}/{page.total} {next}'
```

这样能消除当前“第一页少一个数据槽位”和回调页码手工补偿问题。

## 13. 迁移优先级

建议按复杂度迁移：

1. `TemplateList`：简单 flat list + 固定 create。
2. `SelectTemplate`：简单 selection list + 可信目标 route。
3. `EnvFlags`：验证 flag provider、状态切换和 refresh。
4. `MemberList`：验证 entry operations、disabled reason 和固定 toolbar。
5. `GroupList/GroupManage`：统一 TUI/CUI 的两级结构。
6. `DominionList`：最后处理 section、树、跨服 entry 和多种操作。
7. `MigrateList`：最后验证 tree entry 与 disabled child。

在迁移前三个样板页后，应先固定 `MenuEntry/PageSlice` 契约，再继续扩展 provider，避免每遇到一种列表就修改基础模型。

## 14. 结论

Dominion 当前真正需要支持的不是“领地列表”和“权限列表”两个特例，而是：

```text
flat resource list
selection list
state-toggle flag list
sectioned list
tree list
```

其中 flag list 有 5 个具体业务 source；领地、成员、权限组、模板、称号、迁移数据各有自己的过滤和操作。新 TUI 的 `LIST` 应保持为通用布局组件，具体内容由白名单 `MenuDataProvider` 提供，固定业务动作继续进入统一 actions executor。
