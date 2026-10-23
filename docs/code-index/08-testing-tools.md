# 08 · 测试与辅助工具

> 本类盘点状态：已盘点。已核对 `lib_android_common` 的 Robolectric 测试配置与框架测试，以及各模块模板测试；模板测试不计为业务覆盖。2026-10-22 `P01-T02` 增量登记 `lib_auth`、`lib_network` 的纯 JVM 测试；`P02-T01` 增量登记 `lib_relation` 关系模型与 `lib_network` 分页/Response 校验测试；`P03-T01` 增量登记菜谱表单校验与上传媒体规则测试；`P03-T05` 增量登记真实关系页 View 重建测试与其 Robolectric 配置；`P04-T02` 增量登记评论/收藏/分享参数校验与新增错误码映射测试，并因文件超过 12 KiB 按功能拆为两个子表。

**收录范围：**现有单测工具、Fixture、测试基类、mock/脚本/局部验证入口。

**索引原则：**不因为登记而执行全量测试；注明可安全执行的范围及输入前提。测试通过只证明该测试范围，不代表阶段验收。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 测试基础设施与框架测试](08-testing-tools/01-infrastructure.md) | Robolectric/Android 资源配置、Adapter/列表/刷新套件、模块模板测试 | TEST-001～TEST-004 |
| [02 业务能力回归测试](08-testing-tools/02-business-tests.md) | 认证、网络配置、关系、菜谱表单/上传、社交参数与错误码、真实 Fragment 返回 | TEST-005～TEST-015 |

## 覆盖与缺口

- 已覆盖 `lib_android_common` 框架测试、`lib_auth`/`lib_network`/`lib_relation`/`lib_cookbook` 的纯 JVM 业务测试，以及 `lib_relation` 的真实 Fragment 返回测试。
- 菜谱详情页、评论/收藏/分享目前只有参数校验与错误映射单测，没有 Detail Fragment 的 Robolectric/UI 测试；`ShareFriendDialog` 的分页、选择与失败分支也未覆盖。
- 各模块仍保留模板测试（TEST-004），不作为功能已验证的证据；未登记任何全量回归或性能/压力测试入口。
