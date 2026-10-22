#!/usr/bin/env python3
"""美食家园阶段索引校验/切换。Python 3.9+，仅标准库。"""
from __future__ import annotations

import argparse
import os
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
PHASES = DOCS / 'phases'
INDEX = DOCS / 'PROGRESS.md'
CURRENT = DOCS / 'CURRENT_TASK.md'
MAX_BYTES = 16 * 1024
DONE_SENTINEL = '# 无当前任务\n\n所有阶段均已获用户明确验收。阶段需求和证据已在对应阶段目录归档。\n'
TITLE_RE = re.compile(r'^# (P\d{2} · [^\n]+)$')
LINK_RE = re.compile(r'^- \[(P\d{2} · [^\]\n]+)\]\((phases/P\d{2}-[^)]+/01-requirements\.md)\)$')


class StateError(RuntimeError):
    pass


def stages():
    all_dirs = [p for p in PHASES.iterdir() if p.is_dir()]
    extra = [p.name for p in all_dirs if not re.fullmatch(r'P\d{2}-[a-z0-9-]+', p.name)]
    if extra:
        raise StateError('阶段目录必须按 P00-英文短名 命名：'+', '.join(extra))
    dirs = sorted(all_dirs)
    if not dirs:
        raise StateError('没有阶段目录')
    result = []
    for number, d in enumerate(dirs):
        if not d.name.startswith(f'P{number:02d}-'):
            raise StateError(f'阶段目录数字必须从 P00 连续无跳号：{d.name}')
        src = d / '01-requirements.md'
        prog = d / '02-progress.md'
        if not src.is_file() or not prog.is_file():
            raise StateError(f'缺失阶段需求或进度文件：{d}')
        content = src.read_text('utf-8')
        m = TITLE_RE.fullmatch(content.splitlines()[0]) if content else None
        if not m or not m.group(1).startswith(f'P{number:02d} · '):
            raise StateError(f'阶段需求第一行的名称与目录序号不匹配：{src}')
        result.append({'name': m.group(1), 'dir': d, 'source': src, 'progress': prog})
    return result


def parse_index(s):
    raw = INDEX.read_text('utf-8')
    lines = raw.splitlines()
    if len(lines) < 7 or lines[0] != '# 阶段索引':
        raise StateError('索引必须以 # 阶段索引 开始')
    group = None
    groups = {'已完成': [], '当前': [], '未完成': []}
    found_headers = []
    for line in lines[1:]:
        if not line.strip():
            continue
        if line.startswith('## '):
            key = line[3:]
            if key not in groups or key in found_headers:
                raise StateError(f'索引无效分组或重复分组：{line}')
            found_headers.append(key)
            group = key
            continue
        if group is None:
            raise StateError('索引阶段名必须放在三组内')
        match = LINK_RE.fullmatch(line)
        if not match:
            raise StateError(f'索引不能写小任务或注释，只能列阶段名称与导航链接：{line}')
        groups[group].append((match.group(1), match.group(2)))
    if found_headers != ['已完成', '当前', '未完成']:
        raise StateError('索引分组必须按 已完成 / 当前 / 未完成 排序')
    expected = [(x['name'], f"phases/{x['dir'].name}/01-requirements.md") for x in s]
    values = groups['已完成'] + groups['当前'] + groups['未完成']
    if values != expected:
        raise StateError('索引阶段标题、顺序或路径与真实需求不一致；不可跳阶段')
    if len(groups['当前']) > 1:
        raise StateError('索引只能有一个当前阶段')
    completed = len(groups['已完成'])
    if not groups['当前'] and completed != len(s):
        raise StateError('非终态必须恰有一个当前阶段')
    if groups['当前'] and completed == len(s):
        raise StateError('所有阶段已完成不能再标记当前')
    return completed, bool(groups['当前'])


def render_index(s, done, has_current):
    lines = ['# 阶段索引', '', '## 已完成', '']
    def entry(stage):
        return f"- [{stage['name']}](phases/{stage['dir'].name}/01-requirements.md)"
    lines.extend(entry(x) for x in s[:done])
    lines.extend(['', '## 当前', ''])
    if has_current:
        lines.append(entry(s[done]))
    lines.extend(['', '## 未完成', ''])
    if has_current:
        lines.extend(entry(x) for x in s[done+1:])
    return ('\n'.join(lines) + '\n').encode('utf-8')


def gate_accepted(progress_file):
    body = progress_file.read_text('utf-8')
    m = re.search(r'^- 用户阶段验收：(.+)$', body, re.M)
    n = re.search(r'^- 验收依据：(.+)$', body, re.M)
    return bool(m and m.group(1).strip() == '已接受'
                and n and n.group(1).strip() not in ('', '未提供', '待提供', '待确认'))


def check():
    s = stages()
    done, current = parse_index(s)
    expected_index = render_index(s, done, current)
    if INDEX.read_bytes() != expected_index:
        raise StateError('索引格式偏离规范；请核对单一状态，不自动修正')
    expected_task = s[done]['source'].read_bytes() if current else DONE_SENTINEL.encode('utf-8')
    if CURRENT.read_bytes() != expected_task:
        raise StateError('CURRENT_TASK.md 与当前阶段整个需求文件不一致（或终态标记错误）')
    for stage in s[:done]:
        if not gate_accepted(stage['progress']):
            raise StateError(f"已完成阶段没有真实用户 Gate 记录：{stage['name']}")
    oversized = []
    for p in sorted(ROOT.rglob('*.md')):
        size = p.stat().st_size
        if size > MAX_BYTES:
            oversized.append(f'{p.relative_to(ROOT)} ({size} bytes)')
    if oversized:
        raise StateError('下列 Markdown 超过 16 KiB，须归档/按章节分拆而非截断：\n'+'\n'.join(oversized))
    phase_name = s[done]['name'] if current else '无，全部已完成'
    return f'PASS: 阶段 {len(s)}，已完成 {done}，当前 {phase_name}；索引/副本/验收/文件上限一致。'


def atomic_write(path: Path, content: bytes):
    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.phase-', suffix='.tmp', delete=False) as f:
            tmp_path = Path(f.name)
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(str(tmp_path), str(path))
    finally:
        if tmp_path is not None and tmp_path.exists():
            tmp_path.unlink()


def change(final=False, user_accepted=False):
    if not user_accepted:
        raise StateError('拒绝切换：需要 --user-accepted，且必须真的已获用户明确阶段验收')
    check()
    s = stages()
    done, current = parse_index(s)
    if not current:
        raise StateError('全部阶段已完成，不再存在下个阶段')
    if not gate_accepted(s[done]['progress']):
        raise StateError(f"拒绝切换：{s[done]['name']} 的 02-progress.md 未记录用户明确验收与依据")
    is_last = done == len(s)-1
    if final != is_last:
        raise StateError('当前不是最后阶段应使用 advance；最后阶段应使用 finish')
    next_done = done+1
    has_current = not is_last
    new_index = render_index(s, next_done, has_current)
    new_current = s[next_done]['source'].read_bytes() if has_current else DONE_SENTINEL.encode('utf-8')
    if len(new_current) > MAX_BYTES or len(new_index) > MAX_BYTES:
        raise StateError('切换后的当前任务或索引超过文档上限')
    old_index, old_task = INDEX.read_bytes(), CURRENT.read_bytes()
    try:
        # 先生成当前完整副本，再切换索引的状态。任一步失败尽可能恢复旧状态。
        atomic_write(CURRENT, new_current)
        atomic_write(INDEX, new_index)
        result=check()
    except Exception:
        atomic_write(CURRENT, old_task)
        atomic_write(INDEX, old_index)
        raise
    return '已准备下阶段任务（未授权自动实施）。'+'\n'+result if has_current else '所有阶段已归档。\n'+result


def main():
    parser = argparse.ArgumentParser(description='美食家园阶段索引、当前任务完整复制和容量校验')
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('check', help='只读检查索引、任务副本、阶段用户 Gate 和文件大小')
    for name in ('advance', 'finish'):
        p=sub.add_parser(name, help='仅用户明确通过阶段 Gate 后使用')
        p.add_argument('--user-accepted', action='store_true', help='显式确认 Root 已核实真实用户的阶段验收')
    args=parser.parse_args()
    try:
        if args.command=='check':
            print(check())
        else:
            print(change(final=args.command=='finish', user_accepted=args.user_accepted))
    except (StateError, UnicodeError, OSError) as exc:
        print('FAIL:',str(exc),file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
