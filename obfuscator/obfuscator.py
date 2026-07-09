#!/usr/bin/env python3
"""
Obfuscate Python code using analysis results.
Reads anchors.json and ast_trees.json from each task_* folder.
Builds global name mapping from anchors, then performs code transformations.
"""

import json
import random
import string
import sys
import os
import re
import ast
import keyword
import builtins
from typing import Dict, List, Tuple, Optional, Any

def get_stdlib_module_names():
    try:
        return set(sys.stdlib_module_names)
    except AttributeError:
        return {
            'abc','argparse','array','ast','asyncio','base64','binascii',
            'bisect','builtins','bz2','calendar','cmath','codecs','collections',
            'concurrent','configparser','contextlib','copy','csv','ctypes',
            'datetime','decimal','difflib','dis','doctest','email','enum',
            'errno','faulthandler','fcntl','filecmp','fileinput','fnmatch',
            'fractions','ftplib','functools','gc','getopt','getpass','gettext',
            'glob','gzip','hashlib','heapq','hmac','html','http','importlib',
            'inspect','io','itertools','json','keyword','lib2to3','linecache',
            'locale','logging','lzma','mailbox','math','mimetypes','mmap',
            'modulefinder','multiprocessing','netrc','nis','numbers','operator',
            'optparse','os','pathlib','pdb','pickle','pipes','pkgutil',
            'platform','plistlib','poplib','posix','pprint','profile','pstats',
            'pty','pwd','py_compile','pyclbr','pydoc','queue','quopri',
            'random','re','readline','reprlib','resource','rlcompleter',
            'runpy','sched','secrets','select','selectors','shelve','shlex',
            'shutil','signal','site','smtplib','sndhdr','socket','socketserver',
            'spwd','sqlite3','ssl','stat','statistics','string','stringprep',
            'struct','subprocess','sunau','symtable','sys','sysconfig',
            'tabnanny','tarfile','telnetlib','tempfile','textwrap','threading',
            'time','timeit','tkinter','token','tokenize','trace','traceback',
            'tracemalloc','tty','turtle','types','typing','unicodedata',
            'unittest','urllib','uuid','venv','warnings','wave','weakref',
            'webbrowser','wsgiref','xdrlib','xml','xmlrpc','zipfile','zipimport',
            'zlib'
        }

STDLIB_MODULES = get_stdlib_module_names()


def random_name(exclude: set = None) -> str:
    exclude = exclude or set()
    exclude.update({'None', 'True', 'False', '_cont', '_done', '_iter_tmp'})
    exclude.update(STDLIB_MODULES)
    exclude.update(keyword.kwlist)
    while True:
        length = random.choice([1, 2, 3])
        first = random.choice(string.ascii_lowercase + '_')
        rest = ''.join(random.choices(string.ascii_lowercase + string.digits, k=length - 1))
        name = first + rest
        if (name not in exclude and
            not name.startswith('_') and
            not keyword.iskeyword(name)):
            return name

def shuffle_comment_text(text: str) -> str:
    if not text:
        return text
    chars = list(text)
    random.shuffle(chars)
    return ''.join(chars)

def generate_constant_expr(val) -> str:
    if isinstance(val, int) and abs(val) < 1000:
        factors = [(a, val // a) for a in range(2, abs(val) + 1) if val % a == 0]
        if factors:
            a, b = random.choice(factors)
            return f"({a} * {b})"
    if isinstance(val, str) and len(val) <= 10:
        return "''.join([" + ", ".join(repr(c) for c in val) + "])"
    if isinstance(val, bool):
        return "1 == 1" if val else "1 != 1"
    return repr(val)

def get_line_indent(line: str) -> str:
    return line[:len(line) - len(line.lstrip())]

def remove_comment_from_line(line: str) -> str:
    return line.split('#')[0].rstrip()

def parse_for_head(line: str):
    code = remove_comment_from_line(line)
    m = re.match(r'\s*for\s+(.+?)\s+in\s+(.+):\s*$', code)
    return (m.group(1).strip(), m.group(2).strip()) if m else None

def parse_while_head(line: str):
    code = remove_comment_from_line(line)
    m = re.match(r'\s*while\s+(.+):\s*$', code)
    return m.group(1).strip() if m else None

def extract_block_by_indent(lines: List[str], lineno: int) -> Tuple[int, int]:
    if lineno < 1 or lineno > len(lines):
        return (lineno, lineno)
    idx = lineno - 1
    base_indent = len(get_line_indent(lines[idx]))
    end = len(lines)
    for j in range(idx + 1, len(lines)):
        stripped = lines[j].lstrip()
        if not stripped or stripped.startswith('#'):
            continue
        if len(get_line_indent(lines[j])) <= base_indent:
            end = j
            break
    return (lineno, end)

def generate_opaque_true(base_indent: str) -> str:
    templates = [
        f"({random.randint(0,100)} ^ 0x{random.randint(10,99):02X}) == ({random.randint(0,100)} ^ 0x{random.randint(10,99):02X})",
        f"len('{''.join(random.choices(string.ascii_letters, k=random.randint(2,6)))}') == {random.randint(2,6)}",
        f"({random.randint(2,9)} * {random.randint(2,9)} == {random.randint(2,9) * random.randint(2,9)})",
    ]
    expr = random.choice(templates)
    return f"({expr})"

def get_if_branch_keyword(line: str) -> Optional[str]:
    code = remove_comment_from_line(line)
    if code.lstrip().startswith('if '):
        return 'if'
    if code.lstrip().startswith('elif '):
        return 'elif'
    if code.lstrip().startswith('else:'):
        return 'else'
    return None

def get_if_condition(source_line: str) -> Optional[str]:
    code = remove_comment_from_line(source_line)
    m = re.match(r'\s*(?:if|elif)\s+(.+?)\s*:\s*$', code)
    return m.group(1).strip() if m else None

def build_if_chain_from_lines(source_lines: List[str], start_lineno: int) -> List[Dict]:
    if start_lineno < 1 or start_lineno > len(source_lines):
        return []
    first_line = source_lines[start_lineno - 1]
    first_kw = get_if_branch_keyword(first_line)
    if first_kw != 'if':
        return []
    base_indent = get_line_indent(first_line)
    chain = []
    i = start_lineno - 1
    while i < len(source_lines):
        line = source_lines[i]
        stripped = line.lstrip()
        if not stripped or stripped.startswith('#'):
            i += 1
            continue
        cur_indent = get_line_indent(line)
        if len(cur_indent) != len(base_indent):
            break
        kw = get_if_branch_keyword(line)
        if kw is None:
            break
        if chain:
            last_kw = chain[-1]['keyword']
            if last_kw == 'else':
                break
            if kw == 'if' and last_kw in ('if', 'elif'):
                break
            if kw == 'elif' and last_kw == 'else':
                break
        chain.append({
            'keyword': kw,
            'lineno': i + 1,
            'indent': base_indent
        })
        if kw == 'else':
            i += 1
            break
        i += 1
    return chain

def apply_if_obfuscation(chain: List[Dict], source_lines: List[str]) -> str:
    if not chain:
        return ""
    branches = []
    for item in chain:
        lineno = item['lineno']
        start, end = extract_block_by_indent(source_lines, lineno)
        body_lines = source_lines[lineno:end]
        header = source_lines[lineno - 1]
        condition = get_if_condition(header) if item['keyword'] in ('if','elif') else None
        branches.append({
            'keyword': item['keyword'],
            'lineno': lineno,
            'indent': item['indent'],
            'condition': condition,
            'body_str': ''.join(body_lines),
            'block_end': end,
        })

    base_indent = branches[0]['indent']
    has_elif = any(b['keyword'] == 'elif' for b in branches)
    has_else = any(b['keyword'] == 'else' for b in branches)

    def wrap_condition(cond: str, kw: str) -> str:
        if kw == 'else':
            return ''
        opaque = generate_opaque_true(base_indent)
        return f"({cond}) and {opaque}"

    if not has_elif and not has_else:
        cond = wrap_condition(branches[0]['condition'] or 'True', 'if')
        body = branches[0]['body_str']
        return f"{base_indent}if {cond}:\n{body}"
    elif not has_else:
        parts = []
        for b in branches:
            if b['keyword'] in ('if', 'elif'):
                kw = 'if' if b is branches[0] else 'elif'
                cond = wrap_condition(b['condition'] or 'True', b['keyword'])
                parts.append(f"{base_indent}{kw} {cond}:\n")
                parts.append(b['body_str'])
        return ''.join(parts)
    elif not has_elif and has_else:
        if_branch = branches[0]
        else_branch = branches[1]
        cond = wrap_condition(if_branch['condition'] or 'True', 'if')
        return (f"{base_indent}if not ({if_branch['condition']}):\n{else_branch['body_str']}"
                f"{base_indent}else:\n{if_branch['body_str']}")
    else:
        parts = []
        for b in branches:
            if b['keyword'] in ('if', 'elif'):
                kw = 'if' if b is branches[0] else 'elif'
                cond = wrap_condition(b['condition'] or 'True', b['keyword'])
                parts.append(f"{base_indent}{kw} {cond}:\n")
                parts.append(b['body_str'])
            else:
                parts.append(f"{base_indent}else:\n")
                parts.append(b['body_str'])
        return ''.join(parts)

def obfuscate_continue_in_loop(loop_block: str) -> str:
    lines = loop_block.splitlines(keepends=True)
    if not lines:
        return loop_block
    base_indent = get_line_indent(lines[0])
    header = lines[0]

    body_end = len(lines)
    for i in range(1, len(lines)):
        stripped = lines[i].lstrip()
        if not stripped or stripped.startswith('#'):
            continue
        if len(get_line_indent(lines[i])) <= len(base_indent):
            body_end = i
            break

    body_lines = lines[1:body_end]
    loop_body_indent = base_indent + '    '
    if not body_lines or body_lines[0] != f"{loop_body_indent}_cont = False\n":
        body_lines.insert(0, f"{loop_body_indent}_cont = False\n")

    new_body = process_loop_body(body_lines, loop_body_indent)
    return header + ''.join(new_body) + ''.join(lines[body_end:])

def process_loop_body(lines: List[str], indent: str) -> List[str]:
    FLAG = '_cont'
    out = []
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        stripped = line.lstrip()
        if not stripped or stripped.startswith('#'):
            out.append(line)
            i += 1
            continue

        cur_indent = get_line_indent(line)
        if len(cur_indent) != len(indent):
            out.append(line)
            i += 1
            continue

        if stripped == 'continue\n':
            out.append(f"{indent}{FLAG} = True\n")
            i += 1
            siblings = []
            while i < n:
                nx = lines[i]
                ns = nx.lstrip()
                if (not ns or ns.startswith('#')) and len(get_line_indent(nx)) == len(indent):
                    siblings.append(nx)
                    i += 1
                    continue
                if len(get_line_indent(nx)) == len(indent):
                    siblings.append(nx)
                    i += 1
                else:
                    break
            if siblings:
                deeper = indent + '    '
                out.append(f"{indent}if not {FLAG}:\n")
                for s in siblings:
                    if s.strip():
                        out.append(deeper + s[len(indent):])
                    else:
                        out.append(deeper + '\n' if s.endswith('\n') else deeper)
            continue

        if stripped.startswith(('if ', 'elif ', 'else:')):
            chain_lines, i = extract_if_chain(lines, i, indent)
            transformed = process_if_elif_else_block(chain_lines, indent, FLAG)
            out.extend(transformed)
            continue

        out.append(line)
        i += 1
    return out

def extract_if_chain(lines: List[str], start: int, indent: str):
    chain = []
    i = start
    base_len = len(indent)
    while i < len(lines):
        line = lines[i]
        stripped = line.lstrip()
        if not stripped or stripped.startswith('#'):
            chain.append(line)
            i += 1
            continue
        if len(line) - len(stripped) < base_len and i != start:
            break
        if len(line) - len(stripped) == base_len and i != start:
            if not stripped.startswith(('elif ', 'else:')):
                break
        chain.append(line)
        i += 1
    return chain, i

def process_if_elif_else_block(lines: List[str], indent: str, flag: str) -> List[str]:
    out = []
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        stripped = line.lstrip()
        if not stripped or stripped.startswith('#'):
            out.append(line)
            i += 1
            continue
        if len(get_line_indent(line)) != len(indent):
            out.append(line)
            i += 1
            continue

        if stripped.startswith('if ') or stripped.startswith('elif '):
            colon = stripped.rfind(':')
            if colon != -1:
                cond_part = stripped[:colon]
                keyword, rest = cond_part.split(None, 1) if ' ' in cond_part else (cond_part, '')
                if f'not {flag}' not in rest:
                    if rest:
                        new_cond = f"{keyword} {rest} and not {flag}{stripped[colon:]}"
                    else:
                        new_cond = f"{keyword} not {flag}{stripped[colon:]}"
                else:
                    new_cond = stripped
                out.append(indent + new_cond)
            else:
                out.append(line)
            branch_body, i = extract_branch_body(lines, i + 1, indent + '    ')
            if branch_body:
                processed = process_loop_body(branch_body, indent + '    ')
                out.extend(processed)
            continue

        if stripped.startswith('else:'):
            out.append(f"{indent}elif not {flag}:\n")
            branch_body, i = extract_branch_body(lines, i + 1, indent + '    ')
            if branch_body:
                processed = process_loop_body(branch_body, indent + '    ')
                out.extend(processed)
            continue

        out.append(line)
        i += 1
    return out

def extract_branch_body(lines: List[str], start: int, body_indent: str):
    body = []
    i = start
    while i < len(lines):
        line = lines[i]
        stripped = line.lstrip()
        if not stripped or stripped.startswith('#'):
            body.append(line)
            i += 1
            continue
        if len(get_line_indent(line)) < len(body_indent):
            break
        body.append(line)
        i += 1
    return body, i

def find_enclosing_loop(lines: List[str], lineno: int) -> Tuple[int, int]:
    idx = lineno - 1
    target_indent = None
    for i in range(idx, -1, -1):
        stripped = lines[i].lstrip()
        if not stripped or stripped.startswith('#'):
            continue
        cur_indent = len(get_line_indent(lines[i]))
        if stripped.startswith(('for ', 'while ')):
            if target_indent is None or cur_indent < target_indent:
                start = i + 1
                base_indent_len = cur_indent
                break
        if target_indent is None:
            target_indent = cur_indent
    else:
        return (0, 0)

    for j in range(start, len(lines)):
        stripped = lines[j].lstrip()
        if not stripped or stripped.startswith('#'):
            continue
        if len(get_line_indent(lines[j])) <= base_indent_len:
            end = j
            return (start, end)
    return (start, len(lines))

def convert_for_to_while_block(block: str) -> str:
    lines = block.splitlines(keepends=True)
    base_indent = get_line_indent(lines[0])
    parsed = parse_for_head(lines[0])
    if not parsed:
        return block
    loop_var, iterable = parsed

    else_idx = None
    for i in range(1, len(lines)):
        if lines[i].lstrip().startswith('else:') and get_line_indent(lines[i]) == base_indent:
            else_idx = i
            break
    body = lines[1:else_idx] if else_idx is not None else lines[1:]
    else_part = lines[else_idx:] if else_idx is not None else []

    tmp = '_iter_tmp'
    done = '_done'
    inner = base_indent + '    '
    out = [
        f"{base_indent}{tmp} = iter({iterable})\n",
        f"{base_indent}{done} = False\n",
        f"{base_indent}while not {done}:\n",
        f"{inner}try:\n",
        f"{inner}    {loop_var} = next({tmp})\n",
        f"{inner}except StopIteration:\n",
        f"{inner}    {done} = True\n",
        f"{inner}    break\n",
    ]
    out.extend(body)
    if else_part:
        out.append(f"{base_indent}else:\n")
        out.extend(else_part[1:])
    return ''.join(out)

def convert_while_to_for_block(block: str) -> str:
    lines = block.splitlines(keepends=True)
    base_indent = get_line_indent(lines[0])
    cond = parse_while_head(lines[0])
    if cond is None:
        return block

    else_idx = None
    for i in range(1, len(lines)):
        if lines[i].lstrip().startswith('else:') and get_line_indent(lines[i]) == base_indent:
            else_idx = i
            break
    body = lines[1:else_idx] if else_idx is not None else lines[1:]
    else_part = lines[else_idx:] if else_idx is not None else []

    new_head = f"{base_indent}for _ in iter(lambda: {cond}, False):\n"
    out = [new_head] + body
    if else_part:
        out.extend(else_part)
    return ''.join(out)


def extract_name_from_anchor_node(node: Dict) -> Optional[str]:
    """Extract the original identifier name from an anchor node."""
    tp = node.get('type')
    if tp == 'Name':
        return node.get('id')
    elif tp == 'Attribute':
        return node.get('attr')
    elif tp in ('FunctionDef', 'AsyncFunctionDef'):
        return node.get('name')
    elif tp == 'ClassDef':
        return node.get('name')
    elif tp == 'arg':
        return node.get('arg')
    elif tp == 'alias':
        return node.get('name')
    return None

def build_name_mapping_from_asts(ast_trees: Dict[str, Any], all_anchors: List[Dict]) -> Dict[str, str]:
    """
    Build a mapping from original names to obfuscated names by traversing all ASTs.
    Returns {original_name -> new_name}.
    """
    anchor_to_names = {}  # anchor_id -> set of names
    name_to_anchor = {}   # name -> anchor_id

    def collect_from_ast(ast_root):
        def dfs(node):
            anchor = node.get('anchor')
            if anchor and isinstance(anchor, str) and anchor.startswith('anchor_'):
                name = extract_name_from_anchor_node(node)
                if name:
                    if anchor not in anchor_to_names:
                        anchor_to_names[anchor] = set()
                    anchor_to_names[anchor].add(name)
                    if name not in name_to_anchor:
                        name_to_anchor[name] = anchor
            for child in node.get('children', []):
                dfs(child)
        dfs(ast_root)

    for filepath, info in ast_trees.items():
        collect_from_ast(info['ast'])

    used_names = set(STDLIB_MODULES)
    used_names.update(keyword.kwlist)
    used_names.update({'self', 'cls', '__name__', '__file__', 'None', 'True', 'False'})

    anchor_new_name = {}
    for anchor_id in sorted(anchor_to_names.keys()):
        new_name = random_name(used_names)
        anchor_new_name[anchor_id] = new_name
        used_names.add(new_name)

    name_mapping = {}
    for name, anchor_id in name_to_anchor.items():
        name_mapping[name] = anchor_new_name[anchor_id]

    return name_mapping


def obfuscate_source(anchors: List[Dict], source_lines: List[str],
                     name_mapping: Dict[str, str]) -> Tuple[str, Dict[str, str], List[Dict]]:
    """
    Obfuscate a main file using the provided name mapping.
    Returns:
        obfuscated_code: str
        applied_mapping: dict original_name -> new_name (for de‑obfuscation)
        anchor_details: list of dict with transformation metadata
    """
    applied_mapping = {}
    idx_by_id = {id(a): i for i, a in enumerate(anchors)}
    anchor_details = [None] * len(anchors)

    # Constant obfuscation
    constant_anchors = [a for a in anchors if a.get('type') == 'Constant']
    if constant_anchors:
        constant_anchors.sort(key=lambda a: a.get('start_offset', 0), reverse=True)
        source_str = ''.join(source_lines)
        for anchor in constant_anchors:
            start = anchor.get('start_offset')
            end = anchor.get('end_offset')
            if start is None or end is None:
                continue
            content = (anchor.get('content') or '').strip()
            if start <= end and source_str[start:end] == content:
                try:
                    val = ast.literal_eval(content)
                except Exception:
                    val = content
                expr = generate_constant_expr(val)
                source_str = source_str[:start] + expr + source_str[end:]
        source_lines = source_str.splitlines(keepends=True)

    # Name replacement (skip import lines)
    import_lines = {i for i, l in enumerate(source_lines) if l.lstrip().startswith(('import ', 'from '))}
    for old_name, new_name in name_mapping.items():
        if old_name == new_name:
            continue
        pat = r'\b' + re.escape(old_name) + r'\b'
        for i in range(len(source_lines)):
            if i not in import_lines:
                source_lines[i] = re.sub(pat, new_name, source_lines[i])
        applied_mapping[old_name] = new_name

    # Record name anchor details
    for anchor in anchors:
        tp = anchor.get('type')
        if tp in ('Name', 'Attribute'):
            old_name = anchor.get('content', '').strip()
            if tp == 'Attribute':
                old_name = old_name.rsplit('.', 1)[-1] if '.' in old_name else old_name
            if old_name in name_mapping:
                idx = idx_by_id[id(anchor)]
                anchor_details[idx] = {
                    "type": tp,
                    "lineno": anchor.get('lineno'),
                    "end_lineno": anchor.get('end_lineno', anchor.get('lineno')),
                    "transformation": "renamed",
                    "original_name": old_name,
                    "new_name": name_mapping[old_name],
                    "block_key": anchor.get('block_key')
                }

    # Control‑flow obfuscations
    anchors_sorted = sorted(anchors, key=lambda a: a.get('lineno', 0), reverse=True)
    processed_loops = set()
    processed_if_chains = set()

    for anchor in anchors_sorted:
        tp = anchor.get('type')
        lineno = anchor.get('lineno', 0)
        if lineno < 1:
            continue

        if tp == 'If':
            chain = build_if_chain_from_lines(source_lines, lineno)
            if chain:
                chain_key = chain[0]['lineno']
                if chain_key not in processed_if_chains:
                    processed_if_chains.add(chain_key)
                    first_lineno = chain[0]['lineno']
                    last_lineno = chain[-1]['lineno']
                    _, last_end = extract_block_by_indent(source_lines, last_lineno)
                    new_block = apply_if_obfuscation(chain, source_lines)
                    new_lines = new_block.splitlines(keepends=True)
                    source_lines[first_lineno - 1:last_end] = new_lines

                    branches_desc = [{'keyword': b['keyword'], 'lineno': b['lineno']} for b in chain]
                    idx = idx_by_id[id(anchor)]
                    anchor_details[idx] = {
                        "type": "If",
                        "lineno": lineno,
                        "end_lineno": chain[-1]['lineno'],
                        "transformation": "if_chain_with_opaque_predicates",
                        "branches": branches_desc,
                        "description": "Added opaque predicates to conditions",
                        "block_key": anchor.get('block_key')
                    }
            continue

        if tp == 'Continue':
            loop_start, loop_end = find_enclosing_loop(source_lines, lineno)
            if loop_start and loop_end and (loop_start, loop_end) not in processed_loops:
                processed_loops.add((loop_start, loop_end))
                loop_block = ''.join(source_lines[loop_start - 1:loop_end])
                new_loop = obfuscate_continue_in_loop(loop_block)
                new_loop_lines = new_loop.splitlines(keepends=True)
                source_lines[loop_start - 1:loop_end] = new_loop_lines

                idx = idx_by_id[id(anchor)]
                anchor_details[idx] = {
                    "type": "Continue",
                    "lineno": lineno,
                    "end_lineno": anchor.get('end_lineno', lineno),
                    "transformation": "continue_flag_replacement",
                    "enclosing_loop_range": [loop_start, loop_end],
                    "description": "Inserted _cont flag and guarded statements",
                    "block_key": anchor.get('block_key')
                }
            continue

        if tp == 'Comment':
            comment_type = anchor.get('comment_type')
            start_line = lineno - 1
            end_line = anchor.get('end_lineno', lineno)
            old_texts = []
            new_texts = []

            for line_idx in range(start_line, min(end_line, len(source_lines))):
                line = source_lines[line_idx]
                if comment_type != "docString" and '#' in line:
                    code, sep, comment = line.partition('#')
                    stripped = comment.rstrip('\n')
                    lead_spaces = ' ' if stripped.startswith(' ') else ''
                    new_comment = lead_spaces + shuffle_comment_text(stripped.lstrip())
                    source_lines[line_idx] = code + sep + new_comment + '\n'
                    old_texts.append(comment)
                    new_texts.append(new_comment)
                elif comment_type == "docString":
                    old_texts.append(line)
                    new_texts.append(line)
                else:
                    old_texts.append(line)
                    new_texts.append(line)

            idx = idx_by_id[id(anchor)]
            anchor_details[idx] = {
                "type": "Comment",
                "comment_type": comment_type,
                "lineno": lineno,
                "end_lineno": end_line,
                "transformation": "shuffled" if comment_type != "docString" else "unchanged",
                "before": "\n".join(old_texts),
                "after": "\n".join(new_texts),
                "block_key": anchor.get('block_key')
            }
            continue

        if tp in ('For', 'While'):
            start_line, end_line = extract_block_by_indent(source_lines, lineno)
            if end_line > start_line:
                block = ''.join(source_lines[start_line - 1:end_line])
                if tp == 'For':
                    new_block = convert_for_to_while_block(block)
                    trans = "for_to_while"
                else:
                    new_block = convert_while_to_for_block(block)
                    trans = "while_to_for"
                new_lines = new_block.splitlines(keepends=True)
                source_lines[start_line - 1:end_line] = new_lines

                idx = idx_by_id[id(anchor)]
                anchor_details[idx] = {
                    "type": tp,
                    "lineno": lineno,
                    "end_lineno": anchor.get('end_lineno', lineno),
                    "transformation": trans,
                    "original_block_range": [start_line, end_line],
                    "description": f"Transformed {tp} loop",
                    "block_key": anchor.get('block_key')
                }
            continue

    anchor_details = [d for d in anchor_details if d is not None]
    return ''.join(source_lines), applied_mapping, anchor_details


def obfuscate_dependency(source_lines: List[str], name_mapping: Dict[str, str]) -> str:
    """Obfuscate a dependency file by renaming identifiers (skip imports)."""
    import_lines = {i for i, l in enumerate(source_lines) if l.lstrip().startswith(('import ', 'from '))}
    for old_name, new_name in name_mapping.items():
        if old_name == new_name:
            continue
        pat = r'\b' + re.escape(old_name) + r'\b'
        for i in range(len(source_lines)):
            if i not in import_lines:
                source_lines[i] = re.sub(pat, new_name, source_lines[i])
    return ''.join(source_lines)


# The following functions are kept for the standalone obfuscation pipeline.
# They are not used by the evaluation script but may be called directly.
def collect_all_tasks_and_build_mapping(output_dir: str) -> Tuple[List[Dict], Dict[str, str]]:
    if not os.path.isdir(output_dir):
        print(f"Error: {output_dir} not found", file=sys.stderr)
        return [], {}

    task_dirs = sorted(
        [d for d in os.listdir(output_dir) if d.startswith('task_') and os.path.isdir(os.path.join(output_dir, d))],
        key=lambda x: int(x.split('_')[1])
    )

    task_records = []
    all_anchors_global = []

    for task_name in task_dirs:
        task_dir = os.path.join(output_dir, task_name)
        anchors_file = os.path.join(task_dir, 'anchors.json')
        ast_file = os.path.join(task_dir, 'ast_trees.json')

        if not os.path.isfile(anchors_file) or not os.path.isfile(ast_file):
            print(f"Warning: {task_name} missing required files, skip", file=sys.stderr)
            continue

        with open(anchors_file, 'r', encoding='utf-8') as f:
            anchors = json.load(f)
        with open(ast_file, 'r', encoding='utf-8') as f:
            ast_trees = json.load(f)

        task_records.append({
            'task_name': task_name,
            'task_dir': task_dir,
            'anchors': anchors,
            'ast_trees': ast_trees
        })
        all_anchors_global.append(anchors)

    merged_ast_trees = {}
    for rec in task_records:
        merged_ast_trees.update(rec['ast_trees'])

    flat_anchors = []
    for anchors in all_anchors_global:
        flat_anchors.extend(anchors)

    global_name_mapping = build_name_mapping_from_asts(merged_ast_trees, flat_anchors)
    print(f"Built global name mapping with {len(global_name_mapping)} entries.")

    return task_records, global_name_mapping


def obfuscate_all_tasks(output_dir: str, obfuscated_dir: str):
    task_records, global_name_mapping = collect_all_tasks_and_build_mapping(output_dir)
    if not task_records:
        print("No valid tasks found.", file=sys.stderr)
        return

    os.makedirs(obfuscated_dir, exist_ok=True)

    for rec in task_records:
        task_name = rec['task_name']
        anchors = rec['anchors']
        ast_trees = rec['ast_trees']

        task_out_dir = os.path.join(obfuscated_dir, task_name)
        os.makedirs(task_out_dir, exist_ok=True)

        for filepath, info in ast_trees.items():
            if not os.path.isfile(filepath):
                print(f"{task_name}: file not found {filepath}, skip", file=sys.stderr)
                continue

            with open(filepath, 'r', encoding='utf-8') as f:
                original = f.read()

            lines = original.splitlines(keepends=True)

            if info.get('type') == 'main':
                obfuscated, applied_mapping, details = obfuscate_source(anchors, lines.copy(), global_name_mapping)
            else:
                obfuscated = obfuscate_dependency(lines.copy(), global_name_mapping)
                details = []

            base_name = os.path.basename(filepath)
            out_path = os.path.join(task_out_dir, base_name)
            with open(out_path, 'w', encoding='utf-8') as f:
                f.write(obfuscated)

            if info.get('type') == 'main':
                map_path = os.path.join(task_out_dir, 'mapping.json')
                mapping_out = {
                    "original_file": filepath,
                    "name_mapping": {k: v for k, v in global_name_mapping.items() if k != v},
                    "anchors": details
                }
                with open(map_path, 'w', encoding='utf-8') as f:
                    json.dump(mapping_out, f, indent=2, ensure_ascii=False)

        print(f"{task_name}: processed -> {task_out_dir}")

    print("All tasks obfuscated.")


if __name__ == '__main__':
    output_dir = "output"
    obfuscated_dir = "obfuscated"
    if len(sys.argv) >= 3:
        output_dir = sys.argv[1]
        obfuscated_dir = sys.argv[2]
    obfuscate_all_tasks(output_dir, obfuscated_dir)