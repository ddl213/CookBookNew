# Android 项目可复用能力索引

> **盘点状态：部分完成**：仅本次刷新头间距相关源码核查，其余未盘点。当前仅登记 `VIEW-001`，其余分类仍为模板；禁止依据空表断言工具、方法或自定义 View 已存在。完成首次盘点后由 `code-auditor` 更新为 `已盘点`，如仍有覆盖缺口则使用 `部分完成`。

## 分类导航

| 分类 | 主要内容 | 入口 | 当前状态 |
| --- | --- | --- | --- |
| 01 项目结构与约定 | 模块、架构入口、导航/基类 | [01-project-architecture.md](01-project-architecture.md) | 未盘点 |
| 02 工具类与扩展方法 | Utils、通用方法、扩展与格式处理 | [02-utilities-extensions.md](02-utilities-extensions.md) | 未盘点 |
| 03 自定义 View 与 UI 组件 | 复合 View、Adapter、通用交互控件 | [03-custom-views-ui.md](03-custom-views-ui.md) | 部分完成：仅本次刷新头间距相关源码核查，其余未盘点 |
| 04 布局、样式及资源 | 可复用布局、主题、drawable、attrs | [04-layouts-styles-res.md](04-layouts-styles-res.md) | 未盘点 |
| 05 网络、存储与异步 | API、Repository、数据库、缓存、协程封装 | [05-network-storage.md](05-network-storage.md) | 未盘点 |
| 06 公共业务能力 | 用户、菜谱、消息、订单等已有可复用逻辑 | [06-business-components.md](06-business-components.md) | 未盘点 |
| 07 依赖与工程工具 | Gradle 配置、已有库、构建/开发脚本 | [07-dependencies-build.md](07-dependencies-build.md) | 未盘点 |
| 08 测试与辅助工具 | 单测辅助、测试 Fixture、校验工具 | [08-testing-tools.md](08-testing-tools.md) | 未盘点 |

## 首次盘点边界

- 扫描仓库：待核查。
- 已覆盖模块与 sourceSet：待核查。
- 不能访问/未覆盖模块：待核查。
- 盘点证据（实际源文件/调用位置）：待核查。
- 最近一次可信同步依据：2026-10-22 `RV-HEADER-GAP` 局部源码、页面调用及回归测试核查，见分类 03 的 `VIEW-001`；首次全仓盘点仍待执行。日常根据当前任务变更清单增量维护，不在每个任务后全仓再扫描。

## 日常使用约定

1. 先打开本文件，再读**与当前任务相关**的分类文件；只按实际命中条目的路径核对源码。
2. 类别里的表格是权威查找入口；任何不存在或已改签名的实现都要先局部核实并修正索引。
3. 新增/修改/删除可复用能力后，在当前任务中更新对应类别，检查分类链接和真实源码路径。
4. 一个文件接近 12 KiB 就按功能拆子表，每个 Markdown 严格不超过 16 KiB；分类首页保留子表导航。
5. 本索引**不是阶段进度**：不在此记录已完成/当前/未完成阶段，产品阶段只在 `docs/PROGRESS.md` 维护。
