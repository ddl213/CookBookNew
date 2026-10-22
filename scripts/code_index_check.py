#!/usr/bin/env python3
"""检查项目可复用能力索引结构，绝不取代真实源码审核。Python 3.9+。"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / 'docs' / 'code-index'
INDEX = DIR / '00-INDEX.md'
LIMIT = 16 * 1024
TYPES = {'ARCH', 'UTIL', 'VIEW', 'RES', 'DATA', 'BIZ', 'DEP', 'TEST'}
CATEGORY = {'01': 'ARCH', '02': 'UTIL', '03': 'VIEW', '04': 'RES', '05': 'DATA', '06': 'BIZ', '07': 'DEP', '08': 'TEST'}
LINK = re.compile(r'\]\((\d{2}-[a-z0-9-]+\.md)\)')
IDENT = re.compile(r'([A-Z]+)-\d{3,}$')
STATES = {'未盘点', '部分完成', '已盘点', '已核实无项'}


def split_row(line):
    return [s.strip() for s in re.split(r'(?<!\\)\|', line)[1:-1]]


def run(require_baseline=False):
    errors = []
    if not INDEX.is_file():
        return ['缺失 docs/code-index/00-INDEX.md'], None, 0
    src = INDEX.read_text(encoding='utf-8')
    status_m = re.search(r'盘点状态[：:]\s*([\u4e00-\u9fff]+)', src)
    status = status_m.group(1) if status_m else None
    if status not in {'未盘点', '部分完成', '已盘点'}:
        errors.append('总索引缺少有效的盘点状态（未盘点/部分完成/已盘点）')
    links = LINK.findall(src)
    if len(links) != 8 or len(set(links)) != 8 or [x[:2] for x in links] != [f'{n:02d}' for n in range(1, 9)]:
        errors.append('总索引必须依次链接 01～08 共八个分类首页，不重复、不跳号')
    pages = [DIR / name for name in links]
    for path in pages:
        if not path.is_file():
            errors.append(f'分类文件不存在：{path.relative_to(ROOT)}')
    if status == '已盘点':
        for term in ['扫描仓库：待核查', '已覆盖模块与 sourceSet：待核查', '不能访问/未覆盖模块：待核查', '盘点证据（实际源文件/调用位置）：待核查']:
            if term in src:
                errors.append(f'总体盘点仍有模板占位：{term}')
    docs = sorted(DIR.rglob('*.md'))
    used_ids = {}
    entries = 0
    for doc in docs:
        rel = doc.relative_to(ROOT)
        try:
            body = doc.read_text(encoding='utf-8')
        except UnicodeError:
            errors.append(f'文件非有效 UTF-8：{rel}')
            continue
        if doc.stat().st_size > LIMIT:
            errors.append(f'文件超过 16 KiB：{rel}')
        if doc in pages:
            category = re.search(r'本类盘点状态[：:]\s*([\u4e00-\u9fff]+)', body)
            st = category.group(1) if category else None
            if st not in STATES:
                errors.append(f'分类首页缺少有效盘点状态：{rel}')
            if status == '已盘点' and st not in {'已盘点', '已核实无项'}:
                errors.append(f'总索引已盘点，但分类首页仍未完成：{rel}')
        for line_num, line in enumerate(body.splitlines(), 1):
            if not line.startswith('|'):
                continue
            cells = split_row(line)
            if not cells or not IDENT.fullmatch(cells[0]):
                continue
            item_id = cells[0]
            entries += 1
            if len(cells) != 6:
                errors.append(f'{rel}:{line_num} 条目必须为六列表格')
                continue
            kind = item_id.rsplit('-', 1)[0]
            if kind not in TYPES:
                errors.append(f'{rel}:{line_num} 未知类别 ID：{item_id}')
            else:
                section = doc.relative_to(DIR).parts[0][:2]
                if section in CATEGORY and CATEGORY[section] != kind:
                    errors.append(f'{rel}:{line_num} 类别与 ID 前缀不一致：{item_id}')
            if item_id in used_ids:
                errors.append(f'{rel}:{line_num} ID 重复：{item_id}（另见 {used_ids[item_id]}）')
            else:
                used_ids[item_id] = f'{rel}:{line_num}'
            if any(not s for s in cells[1:]):
                errors.append(f'{rel}:{line_num} 条目存在空列：{item_id}')
            backticks = re.findall(r'`([^`]+)`', cells[4])
            if len(backticks) != 1:
                errors.append(f'{rel}:{line_num} 定义文件列必须且只能包含一个反引号路径：{item_id}')
                continue
            path = (ROOT / backticks[0]).resolve()
            try:
                path.relative_to(ROOT.resolve())
            except ValueError:
                errors.append(f'{rel}:{line_num} 源码路径越界：{item_id}')
                continue
            if not path.is_file():
                errors.append(f'{rel}:{line_num} 源码不存在：{backticks[0]}（{item_id}）')
    if status == '已盘点' and entries == 0:
        errors.append('总索引标记已盘点，但没有任何真实源码能力条目；不能把空模板冒充已完成盘点')
    if require_baseline and status != '已盘点':
        errors.append(f'首次项目盘点尚未通过：当前状态 {status or "未知"}')
    return errors, status, entries


def main():
    parser = argparse.ArgumentParser(description='Android 复用索引文档结构、源文件路径和预算校验')
    parser.add_argument('command', choices=['check'])
    parser.add_argument('--require-baseline', action='store_true', help='要求首次全项目盘点已经完成')
    args = parser.parse_args()
    try:
        errors, status, count = run(args.require_baseline)
    except (OSError, UnicodeError) as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        return 2
    if errors:
        print('FAIL:\n'+'\n'.join('- '+err for err in errors), file=sys.stderr)
        return 2
    print(f'PASS（文档结构校验）；索引状态：{status}；真实条目：{count}。'
          + ('首次盘点尚未执行，不能作为能力完整性证据。' if status != '已盘点' else
             '状态标记为已盘点；人工审核仍需核对语义/完整性。'))
    return 0


if __name__ == '__main__':
    sys.exit(main())
