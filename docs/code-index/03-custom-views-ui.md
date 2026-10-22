# 03 · 自定义 View 与 UI 组件

> 本类盘点状态：部分完成：仅本次刷新头间距相关源码核查，其余未盘点。当前仅登记 `VIEW-001`，保留分类模板与未覆盖范围；首次全仓盘点尚未执行。

**收录范围：**自定义 View、复合 View、通用 Adapter/Delegate、Dialog、状态组件、UI 交互封装。

**索引原则：**注明初始化方式、attrs、生命周期、样式依赖；只有代码中存在的能力才登记。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| VIEW-001 | `RvDecoration`（`RecyclerView.ItemDecoration`）及 `Builder` | 为普通列表和带刷新头的列表设置内容间距、分割线；刷新头不额外占装饰间距。 | `rv.addItemDecoration(RvDecoration.Builder().setVertical(30).setHorizontal(10).build())`；间距参数为 dp，可分别设置四边。跳过 `RefreshHeaderAdapter` 的间距与分割线，递归定位 `ConcatAdapter` 首个内容项。竖向 Grid 按实际 `SpanSizeLookup` 计算列间距和首内容行顶部间距；刷新头须占满一行（`RefreshRecyclerView` 自动处理），内容 span 必须在 `1..spanCount` 内。本次只核查刷新头与间距集成，其他布局方向和既有分割线规则未全面核查。 | `subModules/lib_android_common/src/main/java/com/android/common/utils/RvDecoration.kt` | 页面：`subModules/lib_cookbook/src/main/java/com/rhys/cookbook/CookBookChildFragment.kt` 使用 `setVertical(30)` 配合刷新列表。测试：`subModules/lib_android_common/src/test/java/com/android/common/utils/RvDecorationRefreshTest.kt`，真实 RecyclerView 验证默认头高 0、paddingTop 12 + 顶间距 30 时首内容 top 42、下拉复位、普通列表、Grid 整行头及多 span、跳过头部分割线；2026-10-22 定向测试 6 项通过。 |

> 填表时使用本类编号 `VIEW-001`、`VIEW-002`……；一个条目对应一个可复用能力，源码路径使用反引号，例如 `app/src/.../ActualClass.kt`（这里只是路径格式示意，不是存在性声明）。无法确认可用性时明确标记“未证实”，不得当作已验证。
>
> 增长到约 12 KiB 后在 `docs/code-index/03-custom-views-ui/` 创建 `01-*.md`、`02-*.md` 等子分类页；本页转为子页导航，保留能定位到每项能力的链接，单个 Markdown 不能超过 16 KiB。
