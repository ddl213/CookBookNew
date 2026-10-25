# 美食家园 · Claude Code 工作入口

执行规则见 `.claude/rules/`，角色定义见 `.claude/agents/`。本文件只保留入口与核心边界，不套用 Desktop Pet 专属规则。

## 开始与接续

1. 读取 `docs/PROGRESS.md`，确定唯一当前阶段。
2. 读取 `docs/CURRENT_TASK.md`，获取该阶段完整需求。
3. 读取当前阶段的 `docs/phases/Pxx-*/02-progress.md`，结合 Git 状态核对已有成果、阻塞与下一动作，在既有授权范围内继续。
4. 开发前按 `.claude/rules/08-code-standards-and-reuse.md` 查 `docs/code-index/00-INDEX.md` 及相关分类；首次未盘点先派 `code-auditor`，已有可信基线后按任务局部核实、优先复用并增量维护。

产品需求只查 `docs/requirements/product/` 中与任务相关的章节。`CURRENT_TASK.md` 已包含当前阶段需求，除核对同步或修订需求外，不重复读取同一阶段的 `01-requirements.md`。历史阶段、其他阶段和无关索引分类按需查阅，`README.md` 不列为常规必读；已经载入上下文的规则无需为了阅读清单再读一次。

## 核心执行边界

- Root 只负责分析、分派、审核、验收判断与汇报；代码、文档、资产修改，以及测试、构建、Git 写操作，均委派给对应子 Agent，不因困难或 Agent 不可用改为亲自实施。
- 子 Agent 不再派生 Agent；配置与调用均不得指定 `model` 或 `effort`，也不通过命令强制切换模型或档位。职责与交接遵循 `03-subagent-routing.md`。
- 正式验证由 Root 按 `04-verification-gates.md` 顺序派发至少两轮不同的独立 verifier，满足最终干净轮次门禁才可通过；每轮及复测前清空专用测试数据、重新造数，需要安装应用时由当轮 verifier 亲自执行。
- 仅推进当前阶段内已授权的工作。小任务 `Verified` 不等于阶段完成；用户明确接受阶段 Gate 后，才由进度 Agent 按 `02-stage-lifecycle.md` 切换阶段、完整同步当前任务与阶段索引。
- 涉及 UI、交互或导航时，提供可操作的手动验收步骤与预期结果；无 UI 变更也要给出最小有效证据。阶段完成均等待用户明确接受，不提前开发下一阶段。
- 影响权限、订单状态、数据协议或验收的关键【待确认】项交由用户决定；未来规划不默认实施。
- 所有 Markdown 文件不超过 16 KiB（16,384 UTF-8 字节）；拆分与归档遵循 `07-document-budgets.md`，不得截断或静默删除历史。

以上主题规则均位于 `.claude/rules/`；具体操作、检查命令及例外以对应文件为准。
