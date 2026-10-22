# RecyclerView 列表接口

`AdapterBuilder` 配置布局、差量比较及事件；数据变化通过控件提交，无需保存 Adapter 变量。
普通 `RecyclerView` 和 `RefreshRecyclerView` 使用相同的数据方法。

```kotlin
import com.android.common.ext.buildAdapter
import com.android.common.ext.liner
import com.android.common.ext.submitList
import com.android.common.ext.addItems

binding.rv.liner().buildAdapter<Recipe> {
    setLayout(RecipeItemBinding::class.java)
    setDiff(
        areItemsSame = { old, new -> old.id == new.id },
        areContentsSame = { old, new -> old == new }
    )
    bind<RecipeItemBinding> { holder, _, item, _ ->
        holder.binding.title.text = item.name
    }
}

binding.rv.submitList(firstPage) // 替换全部内容：首次加载、刷新、筛选。
binding.rv.addItems(nextPage)   // 追加内容：分页或新增条目。
binding.rv.submitList(null)    // 清空，也可以传 emptyList()。
```

- Builder 的 `setList(initialList)` 是可选的初始数据配置；不必再向控件重复提交同一批初始数据。
- `submitList` 替换全部内容，不会把同一列表再追加一次。`addItems` 按调用顺序追加，不去重。
- `BaseAdapter` 对提交列表做结构快照；即使修改原来的 `MutableList` 再提交，也能正确比较。列表元素不做深拷贝，请使用不可变数据对象，更新元素时创建新对象。
- 连续追加以最近一次请求提交的列表为基准，包含正在等待差量计算的数据，避免快速分页丢失条目。
- 两个方法均支持尾随提交完成回调，例如 `rv.submitList(data) { rv.refreshComplete() }`。沿用 `ListAdapter` 语义：被后续提交取代的回调不保证执行。
- 在主线程调用。列表元素类型必须与 `buildAdapter<T>` / `buildRefreshAdapter<T>` 一致；Diff 的身份比较需要使用真正唯一的条目 ID。
- 控件会递归查找 `ConcatAdapter` 中唯一的 `BaseAdapter`；未设置、找不到或有多个数据 Adapter 时给出明确错误。

## 刷新和多布局

使用 `rv.buildRefreshAdapter<T>(header = true) { ... }` 配置下拉刷新头，再用 `setOnRefreshListener` 请求数据。
刷新头默认高度为 0，下拉时显示；它不参与 `RvDecoration` 的间距和分割线，首个内容项仍保留配置的顶部间距。
网格布局的刷新头自动占满一行。当前不提供加载更多脚部，`footer = true` 会明确报错；分页数据可通过 `addItems` 追加。

多布局绑定请通过 `holder.localViewType` 判断 Builder 中注册的布局类型；`holder.itemViewType` 可能已被 `ConcatAdapter` 重映射。
点击回调的 `position` 始终对应数据 Adapter 的本地下标，不包含刷新头偏移。

`initViewHolderBlock` 中可以直接调用 `holder.withBinding<BindingType> { ... }`。Binding 泛型必须与注册的布局相匹配。

自定义 `IRefreshHeader` 需要提供只读 `state: RefreshHeaderEnum`。控件读取该状态决定是否允许新刷新，避免控件与 Header 各自维护不同的刷新状态。

## 验证

在项目根目录执行 `./gradlew :lib_android_common:testDebugUnitTest`。测试覆盖列表快照、连续追加、真实列表绑定、点击下标、Builder 校验及刷新手势和动画时序。
