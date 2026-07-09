import ast
import json
import sys
import os
import re
from typing import Dict, Any, Optional, Set, List, Tuple

import comment_analysis as ca
from merge_anchors import merge_anchors_by_name
from semantic_similarity import similarity, extract_node_content

# ----------------------------------------------------------------------
# Utility functions
# ----------------------------------------------------------------------
def offset_to_linecol(source: str, offset: int) -> Tuple[int, int]:
    lines = source.splitlines(keepends=True)
    pos = 0
    for i, line in enumerate(lines, 1):
        line_len = len(line)
        if offset < pos + line_len:
            return i, offset - pos + 1
        pos += line_len
    last_line = len(lines)
    last_col = len(lines[-1]) + 1 if lines else 1
    return last_line, last_col


def get_hole_range_from_parts(source: str, prefix: str, middle: str, suffix: str) -> Tuple[int, int, int, int, int, int]:
    if prefix is None or middle is None or suffix is None:
        raise ValueError("Missing prefix/middle/suffix to locate hole content")
    start_offset = len(prefix)
    end_offset = start_offset + len(middle)
    start_line, start_col = offset_to_linecol(source, start_offset)
    end_line, end_col = offset_to_linecol(source, end_offset)
    return start_offset, end_offset, start_line, start_col, end_line, end_col


def is_elif_node(node: dict, source: str) -> bool:
    start = node.get("start_offset")
    end = node.get("end_offset")
    if start is None or end is None or start >= end:
        return False
    node_text = source[start:end]
    stripped = node_text.lstrip()
    return bool(re.match(r'elif\b', stripped))


def build_ast_with_comments(source: str) -> Dict[str, Any]:
    try:
        tree = ast.parse(source)
    except SyntaxError as e:
        print(f"Syntax error: {e}", file=sys.stderr)
        raise

    line_offsets = [0]
    for line in source.splitlines(keepends=True):
        line_offsets.append(line_offsets[-1] + len(line))

    all_comments = ca.get_comments(source, tree)

    node_map = {}
    parent_map = {}

    def convert_node(node: ast.AST, parent_dict: Optional[Dict] = None) -> Dict:
        lineno = getattr(node, "lineno", 0)
        end_lineno = getattr(node, "end_lineno", lineno)
        col_offset = getattr(node, "col_offset", 0)
        end_col_offset = getattr(node, "end_col_offset", 0)

        if lineno > 0:
            start_offset = line_offsets[lineno - 1] + col_offset
        else:
            start_offset = 0
        if end_lineno > 0:
            end_offset = line_offsets[end_lineno - 1] + end_col_offset
        else:
            end_offset = start_offset

        node_dict = {
            "type": node.__class__.__name__,
            "lineno": lineno,
            "end_lineno": end_lineno,
            "col_offset": col_offset,
            "end_col_offset": end_col_offset,
            "start_offset": start_offset,
            "end_offset": end_offset,
            "children": [],
        }

        node_map[id(node)] = node_dict
        if parent_dict:
            parent_map[id(node_dict)] = parent_dict

        for field, value in ast.iter_fields(node):
            if field in ("lineno", "end_lineno", "ctx", "col_offset", "end_col_offset"):
                continue
            if isinstance(value, list):
                for item in value:
                    if isinstance(item, ast.AST):
                        child_dict = convert_node(item, node_dict)
                        node_dict["children"].append(child_dict)
                    else:
                        node_dict[field] = value
            elif isinstance(value, ast.AST):
                child_dict = convert_node(value, node_dict)
                node_dict["children"].append(child_dict)
            else:
                node_dict[field] = value
        return node_dict

    root_dict = convert_node(tree)

    if root_dict["end_lineno"] == 0:
        root_dict["end_lineno"] = len(source.splitlines())
        root_dict["end_offset"] = len(source)

    def find_innermost_node(node_dict: Dict, lineno: int) -> Optional[Dict]:
        if not (node_dict["lineno"] <= lineno <= node_dict["end_lineno"]):
            return None
        for child in node_dict["children"]:
            if child["lineno"] <= lineno <= child["end_lineno"]:
                inner = find_innermost_node(child, lineno)
                if inner:
                    return inner
        return node_dict

    docstring_linenos: Set[int] = set()

    for cmt in all_comments:
        cmt_dict = {
            "type": "Comment",
            "lineno": cmt["lineno"],
            "end_lineno": cmt['end_lineno'],
            "comment_type": cmt["comment_type"],
            "value": cmt["value"],
            "scope_start": cmt["scope_start"],
            "scope_end": cmt["scope_end"],
            "children": [],
            "start_offset": 0,
            "end_offset": 0,
        }

        if cmt["lineno"] > 0 and len(line_offsets) > cmt["lineno"]:
            cmt_dict["start_offset"] = line_offsets[cmt["lineno"] - 1]
            cmt_dict["end_offset"] = line_offsets[cmt["lineno"]] if cmt["lineno"] < len(line_offsets) else len(source)

        if cmt["comment_type"] == "docString":
            docstring_linenos.add(cmt["lineno"])
            parent_ast = cmt.get("parent_node")
            target_parent = None
            if parent_ast and id(parent_ast) in node_map:
                target_parent = node_map[id(parent_ast)]
            else:
                inner = find_innermost_node(root_dict, cmt["lineno"])
                while inner is not None:
                    if inner.get("type") in ("FunctionDef", "AsyncFunctionDef", "ClassDef", "Module"):
                        target_parent = inner
                        break
                    inner = parent_map.get(id(inner))
                if target_parent is None:
                    target_parent = root_dict

            if target_parent is not None:
                target_parent["children"].append(cmt_dict)
            else:
                root_dict["children"].append(cmt_dict)
        else:
            parent = find_innermost_node(root_dict, cmt["lineno"])
            if parent:
                parent["children"].append(cmt_dict)
            else:
                root_dict["children"].append(cmt_dict)

    def remove_expr_nodes(node_dict: Dict):
        new_children = []
        for child in node_dict.get("children", []):
            if child.get("type") == "Expr" and child["lineno"] in docstring_linenos:
                continue
            remove_expr_nodes(child)
            new_children.append(child)
        node_dict["children"] = new_children

    remove_expr_nodes(root_dict)

    def sort_children(node_dict: Dict):
        node_dict["children"].sort(key=lambda x: x["lineno"])
        for child in node_dict["children"]:
            sort_children(child)

    sort_children(root_dict)
    return root_dict


def parse_block_key(block_key: str) -> Tuple[str, int, int]:
    if "#" not in block_key:
        raise ValueError(f"Invalid block_key: {block_key}")
    file_path, line_part = block_key.rsplit("#", 1)
    line_part = line_part.lstrip('L')
    if "-" in line_part:
        start_str, end_str = line_part.split("-")
        start_str = start_str.lstrip('L')
        end_str = end_str.lstrip('L')
        start_line = int(start_str)
        end_line = int(end_str)
    else:
        line_part = line_part.lstrip('L')
        start_line = int(line_part)
        end_line = start_line
    return file_path, start_line, end_line


def add_anchor_to_ast(ast_dict: Dict, hole_start_offset: int, hole_end_offset: int,
                      parent_map: Dict[int, Dict], protected_names: Set[str],
                      source: str) -> None:
    ALLOWED_ANCHOR_TYPES = {
        "FunctionDef", "AsyncFunctionDef", "ClassDef",
        "If", "For", "AsyncFor", "While", "Comment",
        "Name", "Attribute", "Constant", "Continue"
    }
    counter = 1

    def dfs(node: Dict):
        nonlocal counter
        node_type = node.get("type", "")
        node_start = node.get("start_offset", 0)
        node_end = node.get("end_offset", 0)

        inside_hole = (node_start >= hole_start_offset and node_end <= hole_end_offset)

        if node_type not in ALLOWED_ANCHOR_TYPES or inside_hole:
            node["anchor"] = None
        elif node_type == "Name":
            name = node.get("id", "")
            if name in protected_names:
                node["anchor"] = None
            else:
                node["anchor"] = f"anchor_{counter}"
                counter += 1
        elif node_type == "Attribute":
            attr_name = node.get("attr", "")
            if attr_name in protected_names:
                node["anchor"] = None
            else:
                node["anchor"] = f"anchor_{counter}"
                counter += 1
        elif node_type == "If" and is_elif_node(node, source):
            node["anchor"] = "elif"
        else:
            node["anchor"] = f"anchor_{counter}"
            counter += 1

        for child in node.get("children", []):
            dfs(child)

    dfs(ast_dict)


def make_json_serializable(obj):
    if isinstance(obj, dict):
        return {k: make_json_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [make_json_serializable(item) for item in obj]
    elif obj is Ellipsis:
        return "..."
    elif isinstance(obj, (bytes, complex)):
        return str(obj)
    else:
        return obj


def compute_node_height(node: Dict) -> int:
    if not node.get("children"):
        node["_height"] = 0
        return 0
    max_child_height = 0
    for child in node.get("children", []):
        child_h = compute_node_height(child)
        max_child_height = max(max_child_height, child_h)
    node["_height"] = max_child_height + 1
    return node["_height"]


def get_node_effective_range(node: Dict) -> Tuple[int, int]:
    if node.get("type") == "Comment":
        start = node.get("scope_start")
        end = node.get("scope_end")
        if start is not None and end is not None:
            return start, end
    return node.get("lineno", 0), node.get("end_lineno", 0)


def calculate_raw_factors(node: Dict, height: int, max_height: int,
                          hole_start_line: int, hole_end_line: int,
                          hole_code: str) -> Dict[str, float]:
    node_start, node_end = node.get("lineno", 0), node.get("end_lineno", 0)
    scope_start, scope_end = get_node_effective_range(node)
    if scope_start == 0:
        return {}

    factors = {}
    norm_height = height / max_height if max_height > 0 else 0
    factors["height"] = norm_height

    if node_end < hole_start_line:
        distance = hole_start_line - node_end
    elif hole_end_line < node_start:
        distance = node_start - hole_end_line
    else:
        distance = 0
    factors["distance"] = 1.0 / ((distance + 1) ** 2)

    factors["inside_bonus"] = 0.5 if (scope_start <= hole_start_line and scope_end >= hole_end_line) else 0.0

    node_content = extract_node_content(node)
    if node_content and hole_code:
        semantic_score = similarity(node_content, hole_code)
    else:
        semantic_score = 0.0
    factors["semantic"] = semantic_score

    return factors


def get_top_anchors(ast_dict: Dict, hole_start_line: int, hole_end_line: int,
                    hole_code: str, top_k: int = 10) -> List[Dict]:
    compute_node_height(ast_dict)

    max_height = 0
    def find_max_height(node):
        nonlocal max_height
        h = node.get("_height", 0)
        if h > max_height:
            max_height = h
        for child in node.get("children", []):
            find_max_height(child)
    find_max_height(ast_dict)

    candidates_raw = []
    def collect_raw(node):
        anchor = node.get("anchor")
        if anchor and isinstance(anchor, str) and anchor.startswith("anchor_"):
            height = node.get("_height", 0)
            factors = calculate_raw_factors(node, height, max_height,
                                            hole_start_line, hole_end_line, hole_code)
            if factors:
                candidates_raw.append((node, factors))
        for child in node.get("children", []):
            collect_raw(child)
    collect_raw(ast_dict)

    if not candidates_raw:
        return []

    factor_names = ["height", "distance", "inside_bonus", "semantic"]
    factor_weights = [0.25] * 4
    n = len(candidates_raw)

    raw_factor_lists = {name: [f[name] for _, f in candidates_raw] for name in factor_names}
    percentile_maps = {}
    for name in factor_names:
        values = raw_factor_lists[name]
        sorted_vals = sorted(values)
        rank_map = {}
        for i, v in enumerate(sorted_vals):
            if v not in rank_map:
                rank_map[v] = []
            rank_map[v].append(i + 1)
        for v, ranks in rank_map.items():
            rank_map[v] = sum(ranks) / len(ranks) / (n + 1)
        percentile_maps[name] = rank_map

    candidates = []
    for node, factors in candidates_raw:
        normalized = {name: percentile_maps[name][factors[name]] for name in factor_names}
        score = sum(w * normalized[name] for w, name in zip(factor_weights, factor_names))

        item = {
            "anchor": node.get("anchor"),
            "type": node.get("type"),
            "lineno": node.get("lineno"),
            "end_lineno": node.get("end_lineno"),
            "start_offset": node.get("start_offset"),
            "end_offset": node.get("end_offset"),
            "score": score,
            "content": extract_node_content(node),
            "comment_type": node.get("comment_type"),
            "factors": {
                "raw": factors,
                "normalized": normalized,
                "percentile_method": "rank/(n+1)"
            }
        }
        if node.get("type") == "Comment":
            item["scope_start"] = node.get("scope_start")
            item["scope_end"] = node.get("scope_end")
        candidates.append(item)

    candidates.sort(key=lambda x: x["score"], reverse=True)

    seen_anchors = set()
    top_candidates = []
    for cand in candidates:
        anchor = cand["anchor"]
        if anchor not in seen_anchors:
            seen_anchors.add(anchor)
            top_candidates.append(cand)
            if len(top_candidates) == top_k:
                break

    return top_candidates


def get_hole_info(record: dict, source_code: str):
    middle = record.get("middle")
    prefix = record.get("prefix")
    suffix = record.get("suffix")
    block_key = record.get("block_key")

    if middle is not None and prefix is not None and suffix is not None:
        try:
            start_off, end_off, start_line, _, end_line, _ = get_hole_range_from_parts(
                source_code, prefix, middle, suffix
            )
            return start_line, end_line, middle, start_off, end_off
        except Exception:
            pass

    _, hole_start_line, hole_end_line = parse_block_key(block_key)
    lines = source_code.splitlines()
    hole_code = "\n".join(lines[hole_start_line-1:hole_end_line])
    line_offsets = [0]
    for ln in source_code.splitlines(keepends=True):
        line_offsets.append(line_offsets[-1] + len(ln))
    start_off = line_offsets[hole_start_line-1]
    end_off = line_offsets[hole_end_line] if hole_end_line < len(line_offsets) else len(source_code)
    return hole_start_line, hole_end_line, hole_code, start_off, end_off


def process_source(record, source_code):
    """
    Generate AST with anchors for a main file.
    Populates record['anchors'] with anchor information,
    each containing the block_key to identify the hole.
    """
    hole_start_line, hole_end_line, hole_code, start_off, end_off = get_hole_info(record, source_code)
    ast_dict = build_ast_with_comments(source_code)

    parent_map = {}
    def build_parent_map(node, parent=None):
        if parent:
            parent_map[id(node)] = parent
        for child in node.get("children", []):
            build_parent_map(child, node)
    build_parent_map(ast_dict)

    protected_names = set()
    import builtins
    protected_names.update(dir(builtins))

    block_key = record.get("block_key")
    if block_key:
        try:
            file_path, _, _ = parse_block_key(block_key)
            base_name = os.path.splitext(os.path.basename(file_path))[0]
            if base_name and base_name.isidentifier():
                protected_names.add(base_name)
        except:
            pass

    try:
        tree = ast.parse(source_code)
    except SyntaxError:
        tree = ast.parse("")

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                top_level = alias.name.split('.')[0]
                if top_level.isidentifier():
                    protected_names.add(top_level)
                if alias.asname and alias.asname.isidentifier():
                    protected_names.add(alias.asname)
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                top_level = node.module.split('.')[0]
                if top_level.isidentifier():
                    protected_names.add(top_level)
            for alias in node.names:
                if alias.name and alias.name.isidentifier():
                    protected_names.add(alias.name)
                if alias.asname and alias.asname.isidentifier():
                    protected_names.add(alias.asname)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            protected_names.add(node.name)
            for arg in node.args.args:
                protected_names.add(arg.arg)
            if node.args.vararg:
                protected_names.add(node.args.vararg.arg)
            for arg in node.args.kwonlyargs:
                protected_names.add(arg.arg)
            if node.args.kwarg:
                protected_names.add(node.args.kwarg.arg)
        elif isinstance(node, ast.ClassDef):
            protected_names.add(node.name)

    add_anchor_to_ast(ast_dict, start_off, end_off, parent_map, protected_names, source_code)

    # Collect anchor information and attach block_key
    anchors = []
    def collect_anchors(node):
        anc = node.get("anchor")
        if anc and isinstance(anc, str) and anc.startswith("anchor_"):
            anchors.append({
                "anchor": anc,
                "type": node.get("type"),
                "lineno": node.get("lineno"),
                "end_lineno": node.get("end_lineno"),
                "start_offset": node.get("start_offset"),
                "end_offset": node.get("end_offset"),
                "content": extract_node_content(node),
                "comment_type": node.get("comment_type"),
                "scope_start": node.get("scope_start"),
                "scope_end": node.get("scope_end"),
                "block_key": block_key   # link to the hole
            })
        for child in node.get("children", []):
            collect_anchors(child)
    collect_anchors(ast_dict)
    record['anchors'] = anchors

    return ast_dict


def get_imported_modules(source_code):
    try:
        tree = ast.parse(source_code)
    except SyntaxError:
        return set()
    imported_files = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                module_path = alias.name.replace('.', os.sep) + '.py'
                imported_files.add(module_path)
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                base = node.module.replace('.', os.sep)
                module_path = base + '.py'
                imported_files.add(module_path)
    return imported_files


# ----------------------------------------------------------------------
# Propagate anchors to dependency files
# ----------------------------------------------------------------------
def propagate_anchors_to_dependency(ast_dict, name_to_anchor):
    """
    Traverse the AST and assign anchors according to name_to_anchor map.
    If a name is present, give it the corresponding anchor; otherwise set to None.
    name_to_anchor: name -> anchor_label
    """
    def dfs(node):
        if node.get("type") == "Name":
            name = node.get("id")
            if name and name in name_to_anchor:
                node["anchor"] = name_to_anchor[name]
            else:
                node["anchor"] = None
        elif node.get("type") == "Attribute":
            attr = node.get("attr")
            if attr and attr in name_to_anchor:
                node["anchor"] = name_to_anchor[attr]
            else:
                node["anchor"] = None
        elif node.get("type") == "arg":
            arg = node.get("arg")
            if arg and arg in name_to_anchor:
                node["anchor"] = name_to_anchor[arg]
            else:
                node["anchor"] = None
        for child in node.get("children", []):
            dfs(child)
    dfs(ast_dict)


# ----------------------------------------------------------------------
# Rollback invalid Attribute anchors (cross-file check)
# ----------------------------------------------------------------------
def build_import_map(source_code: str, base_dir: str) -> Dict[str, str]:
    """
    Parse import statements and return a mapping from alias/name to
    the absolute module file path.
    """
    try:
        tree = ast.parse(source_code)
    except SyntaxError:
        return {}

    import_map = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                mod_path = alias.name.replace('.', os.sep) + '.py'
                actual_name = alias.asname if alias.asname else alias.name.split('.')[0]
                import_map[actual_name] = os.path.normpath(os.path.join(base_dir, mod_path))
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                mod_base = node.module.replace('.', os.sep) + '.py'
                mod_path = os.path.normpath(os.path.join(base_dir, mod_base))
                for alias in node.names:
                    imported_name = alias.asname if alias.asname else alias.name
                    import_map[imported_name] = mod_path
    return import_map


def get_module_definitions(module_ast: Dict) -> Set[str]:
    """
    Extract top-level defined names (functions, classes, variables) from a module AST.
    """
    defs = set()
    if not module_ast:
        return defs
    for child in module_ast.get("children", []):
        tp = child.get("type")
        if tp in ("FunctionDef", "AsyncFunctionDef", "ClassDef"):
            name = child.get("name")
            if name:
                defs.add(name)
        elif tp == "Assign":
            for target in child.get("targets", []):
                if target.get("type") == "Name":
                    defs.add(target.get("id"))
    return defs


def rollback_invalid_attribute_anchors(all_asts: Dict[str, dict], records: List[dict]):
    """
    For each main file, scan Attribute nodes.
    If the leaf attribute is not defined in the imported module, set its anchor to None.
    """
    main_import_maps = {}
    for rec in records:
        file_path = rec["file_path"]
        if file_path not in main_import_maps:
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    source = f.read()
                base_dir = os.path.dirname(os.path.normpath(file_path))
                main_import_maps[file_path] = build_import_map(source, base_dir)
            except:
                main_import_maps[file_path] = {}

    def dfs(node, file_path, import_map):
        if node.get("type") == "Attribute":
            attr_chain = []
            current = node
            while current.get("type") == "Attribute":
                attr_chain.insert(0, current.get("attr"))
                value_child = None
                for child in current.get("children", []):
                    if child.get("type") in ("Attribute", "Name"):
                        value_child = child
                        break
                if value_child:
                    current = value_child
                else:
                    current = None
            if current and current.get("type") == "Name":
                base_name = current.get("id")
                if base_name in import_map:
                    module_path = import_map[base_name]
                    module_ast = all_asts.get(module_path)
                    if module_ast:
                        module_defs = get_module_definitions(module_ast)
                        leaf_attr = node.get("attr")
                        if leaf_attr not in module_defs:
                            node["anchor"] = None
        for child in node.get("children", []):
            dfs(child, file_path, import_map)

    for rec in records:
        file_path = rec["file_path"]
        ast_root = all_asts.get(file_path)
        if not ast_root:
            continue
        import_map = main_import_maps.get(file_path, {})
        dfs(ast_root, file_path, import_map)


# ----------------------------------------------------------------------
# Main processing (kept for compatibility with test.py's original usage)
# ----------------------------------------------------------------------
def main():
    jsonl_path = "block_key.jsonl"
    output_root = "output"
    os.makedirs(output_root, exist_ok=True)

    records = []
    try:
        with open(jsonl_path, "r", encoding="utf-8-sig") as f:
            for line_num, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    continue
                block_key = record.get("block_key")
                if not block_key:
                    continue
                try:
                    file_path, hole_start_line, hole_end_line = parse_block_key(block_key)
                except:
                    continue
                if not os.path.isfile(file_path):
                    continue
                records.append({
                    "record": record,
                    "file_path": file_path,
                    "hole_start_line": hole_start_line,
                    "hole_end_line": hole_end_line,
                    "line_num": line_num
                })
    except FileNotFoundError:
        print(f"Error: {jsonl_path} not found!", file=sys.stderr)
        sys.exit(1)

    all_asts = {}
    task_to_main_file = {}

    for idx, rec_info in enumerate(records, 1):
        file_path = rec_info["file_path"]
        record = rec_info["record"]

        if file_path in all_asts:
            task_to_main_file[idx] = file_path
            continue

        try:
            with open(file_path, "r", encoding="utf-8") as f:
                source_code = f.read()
        except Exception as e:
            print(f"Task {idx}: error reading {file_path}: {e}", file=sys.stderr)
            continue

        try:
            ast_dict = process_source(record, source_code)
        except Exception as e:
            print(f"Task {idx}: error processing {file_path}: {e}", file=sys.stderr)
            continue

        all_asts[file_path] = ast_dict
        task_to_main_file[idx] = file_path

        base_dir = os.path.dirname(os.path.normpath(file_path))
        imported_modules = get_imported_modules(source_code)
        for imp in imported_modules:
            abs_imp = os.path.normpath(os.path.join(base_dir, imp))
            if abs_imp in all_asts:
                continue
            if not os.path.isfile(abs_imp):
                continue
            try:
                with open(abs_imp, "r", encoding="utf-8") as df:
                    dep_code = df.read()
                dep_ast = build_ast_with_comments(dep_code)
                dep_ast = make_json_serializable(dep_ast)
                all_asts[abs_imp] = dep_ast
            except Exception as e:
                print(f"Error building AST for {abs_imp}: {e}", file=sys.stderr)

    virtual_root = {
        "type": "VirtualRoot",
        "lineno": 0, "end_lineno": 0,
        "start_offset": 0, "end_offset": 0,
        "children": list(all_asts.values())
    }
    merge_anchors_by_name(virtual_root)

    name_to_anchor = {}
    def collect_mapping(node):
        if node.get("anchor") and isinstance(node.get("anchor"), str) and node["anchor"].startswith("anchor_"):
            if node.get("type") == "Name":
                name = node.get("id")
            elif node.get("type") == "Attribute":
                name = node.get("attr")
            elif node.get("type") == "arg":
                name = node.get("arg")
            else:
                name = None
            if name and name not in name_to_anchor:
                name_to_anchor[name] = node["anchor"]
        for child in node.get("children", []):
            collect_mapping(child)

    for file_path, ast_root in all_asts.items():
        is_main = any(rec["file_path"] == file_path for rec in records)
        if is_main:
            collect_mapping(ast_root)

    for file_path, ast_root in all_asts.items():
        is_main = any(rec["file_path"] == file_path for rec in records)
        if not is_main:
            propagate_anchors_to_dependency(ast_root, name_to_anchor)

    rollback_invalid_attribute_anchors(all_asts, records)

    for task_idx, main_file in task_to_main_file.items():
        task_dir = os.path.join(output_root, f"task_{task_idx}")
        os.makedirs(task_dir, exist_ok=True)

        main_ast = all_asts[main_file]

        rec_info = records[task_idx - 1]
        record = rec_info["record"]
        base_dir = os.path.dirname(os.path.normpath(main_file))
        imported = set()
        try:
            with open(main_file, "r", encoding="utf-8") as f:
                imported = get_imported_modules(f.read())
        except:
            pass

        resolved_deps = {}
        for imp in imported:
            abs_imp = os.path.normpath(os.path.join(base_dir, imp))
            if abs_imp in all_asts:
                resolved_deps[abs_imp] = all_asts[abs_imp]

        ast_trees_out = {main_file: {"type": "main", "ast": main_ast}}
        for dep_path, dep_ast in resolved_deps.items():
            ast_trees_out[dep_path] = {"type": "dependency", "ast": dep_ast}
        with open(os.path.join(task_dir, "ast_trees.json"), "w", encoding="utf-8") as f:
            json.dump(ast_trees_out, f, indent=2, ensure_ascii=False)

        hole_start_line, hole_end_line, hole_code, _, _ = get_hole_info(record, open(main_file, encoding="utf-8").read())
        top_anchors = get_top_anchors(main_ast, hole_start_line, hole_end_line, hole_code, top_k=100)
        with open(os.path.join(task_dir, "anchors.json"), "w", encoding="utf-8") as f:
            json.dump(top_anchors, f, indent=2, ensure_ascii=False)

        print(f"Task {task_idx}: {main_file} -> {task_dir}")

    print("All tasks processed.")

if __name__ == "__main__":
    main()