# 美食家园：Claude Code 阶段化开发规则包

本文件供项目维护者安装和查阅；AI 的工作入口是 `CLAUDE.md`。本包包含规则、产品需求和阶段模板，不代表实际 Android 工程已实现或通过验收。

## 安装与启动

1. 将本包内容合并到 **Android 项目根目录**，保留 `.claude/` 隐藏目录，不额外套一层文件夹。
2. 已有 `CLAUDE.md`、`AGENTS.md` 或同名文档时先比较再合并，保留现有约束、真实阶段状态与进度记录。
3. 在项目根目录启动 Claude Code，首次使用或新会话接续可直接说：

```text
按 CLAUDE.md 接续，在既有授权范围内推进当前阶段；到阶段 Gate 时给出验收依据，等我明确接受。
```

代码规范已包含在 `.claude/rules/08-code-standards-and-reuse.md`；如手头另有 `美食家园-Android代码规范与复用索引.md`，无需重复安装或维护。

## 文件分工

| 文件或目录 | 用途 |
| --- | --- |
| `CLAUDE.md` | AI 的工作入口、读取顺序与核心边界 |
| `.claude/rules/` | 按主题维护执行规则；代码规范在 `08-code-standards-and-reuse.md`，布局规范在 `06-android-layout.md` |
| `.claude/agents/` | 实施、验证、代码盘点和进度维护等子 Agent 的职责 |
| `docs/PROGRESS.md` | 全项目唯一阶段状态索引 |
| `docs/CURRENT_TASK.md` | 当前阶段完整需求副本 |
| `docs/phases/` | 各阶段需求源 `01-requirements.md` 与工作记录 `02-progress.md` |
| `docs/code-index/00-INDEX.md` | 真实源码能力的分类导航，具体工具、方法、View 等记在对应分类中 |
| `docs/requirements/product/` | 产品需求原文，按相关章节查阅 |
| `scripts/` | 阶段一致性与代码索引校验工具 |

初始模板以 P00 为当前待核查阶段，P01～P10 未完成，能力索引为 `未盘点`。实际工程首次盘点、日常复用和索引更新由规则 `08` 统一规定，入口文档不再重复展开。产品原型图片未随包附带。

## 校验与阶段切换

以下命令由规则指定的子 Agent 在需要时执行；校验文档结构、大小及路径，不能替代业务验证或用户阶段验收。

```bash
python scripts/phase_flow.py check
python scripts/code_index_check.py check
# 首次源码盘点完成后，再检查可信基线要求
python scripts/code_index_check.py check --require-baseline
```

阶段接受、切换与最终收尾统一见 `.claude/rules/02-stage-lifecycle.md`；文档容量和历史归档见 `.claude/rules/07-document-budgets.md`。不要仅因小任务完成就执行阶段切换。

## 文档如何读取

- `README.md` 用于安装与维护，不要求 AI 每次任务读取。
- `CLAUDE.md` 规定工作入口；当前阶段以外的需求、历史记录和索引分类按任务查阅，不逐个通读。
- 启用项目规则时，`.claude/rules/` 中无 `paths` 限定的规则会在启动时加载；本包的 `06-android-layout.md` 使用路径限定。把规则拆成多个文件本身不会减少加载量，已载入的规则也无需再次手动读取。

加载机制可查 [Claude Code 官方文档](https://code.claude.com/docs/en/memory)，并用 `/context` 核对实际上下文占用。规则文件约束工作方式，工具权限仍由 Claude Code 设置控制。
