#!/usr/bin/env python3
"""
Standalone single-file Python obfuscator.
Usage: python standalone.py <source_file>
Prints obfuscated code to stdout, errors to stderr.

Does NOT require GraphCodeBERT/PyTorch — only uses stdlib, comment_analysis,
merge_anchors, and obfuscator modules.
"""

import ast
import json
import sys
import os
import re
from typing import Dict, Any, Optional, Set, List, Tuple

import comment_analysis as ca
from merge_anchors import merge_anchors_by_name
from obfuscator import (
    obfuscate_source,
    build_name_mapping_from_asts,
    STDLIB_MODULES,
)

# ----------------------------------------------------------------------
# Utility functions (copied from test.py to avoid semantic_similarity import)
# ----------------------------------------------------------------------

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


def extract_node_content(node: Dict) -> str:
    """Extract a descriptive string from an AST dict node for anchor recording."""
    tp = node.get("type", "")
    if tp == "Name":
        return node.get("id", "")
    elif tp == "Attribute":
        parts = []
        cur = node
        while cur.get("type") == "Attribute":
            parts.insert(0, cur.get("attr", ""))
            value_child = None
            for child in cur.get("children", []):
                if child.get("type") in ("Attribute", "Name"):
                    value_child = child
                    break
            cur = value_child if value_child else {}
        if cur and cur.get("type") == "Name":
            parts.insert(0, cur.get("id", ""))
        return ".".join(parts)
    elif tp in ("FunctionDef", "AsyncFunctionDef"):
        return node.get("name", "")
    elif tp == "ClassDef":
        return node.get("name", "")
    elif tp == "Constant":
        val = node.get("value")
        return str(val) if val is not None else ""
    return ""


def collect_protected_names(source: str, filepath: str) -> Set[str]:
    """Collect names that should not be obfuscated (builtins, imports, etc.)."""
    protected = set()
    import builtins
    protected.update(dir(builtins))

    base_name = os.path.splitext(os.path.basename(filepath))[0]
    if base_name and base_name.isidentifier():
        protected.add(base_name)

    try:
        tree = ast.parse(source)
    except SyntaxError:
        tree = ast.parse("")

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                top_level = alias.name.split('.')[0]
                if top_level.isidentifier():
                    protected.add(top_level)
                if alias.asname and alias.asname.isidentifier():
                    protected.add(alias.asname)
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                top_level = node.module.split('.')[0]
                if top_level.isidentifier():
                    protected.add(top_level)
            for alias in node.names:
                if alias.name and alias.name.isidentifier():
                    protected.add(alias.name)
                if alias.asname and alias.asname.isidentifier():
                    protected.add(alias.asname)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            protected.add(node.name)
            for arg in node.args.args:
                protected.add(arg.arg)
            if node.args.vararg:
                protected.add(node.args.vararg.arg)
            for arg in node.args.kwonlyargs:
                protected.add(arg.arg)
            if node.args.kwarg:
                protected.add(node.args.kwarg.arg)
        elif isinstance(node, ast.ClassDef):
            protected.add(node.name)

    return protected


def main():
    if len(sys.argv) < 2:
        print("Usage: python standalone.py <source_file>", file=sys.stderr)
        sys.exit(1)

    filepath = os.path.abspath(sys.argv[1])
    if not os.path.isfile(filepath):
        print(f"Error: file not found: {filepath}", file=sys.stderr)
        sys.exit(1)

    with open(filepath, "r", encoding="utf-8") as f:
        source = f.read()

    if not source.strip():
        print("Error: source file is empty", file=sys.stderr)
        sys.exit(1)

    # Step 1: Build AST with comments
    ast_dict = build_ast_with_comments(source)

    # Step 2: Build parent_map
    parent_map = {}
    def build_parent_map(node, parent=None):
        if parent:
            parent_map[id(node)] = parent
        for child in node.get("children", []):
            build_parent_map(child, node)
    build_parent_map(ast_dict)

    # Step 3: Collect protected names
    protected_names = collect_protected_names(source, filepath)

    # Step 4: Add anchors — use (0, 0) hole so no nodes are inside-hole
    add_anchor_to_ast(ast_dict, 0, 0, parent_map, protected_names, source)

    # Step 5: Merge anchors by name
    merge_anchors_by_name(ast_dict)

    # Step 6: Collect anchors list
    anchors = []
    def collect_anchors(node):
        anc = node.get("anchor")
        if anc and isinstance(anc, str) and anc.startswith("anchor_"):
            anchors.append({
                "anchor": anc,
                "type": node.get("type"),
                "lineno": node.get("lineno"),
                "end_lineno": node.get("end_lineno", node.get("lineno")),
                "start_offset": node.get("start_offset", 0),
                "end_offset": node.get("end_offset", 0),
                "content": extract_node_content(node),
                "comment_type": node.get("comment_type"),
                "scope_start": node.get("scope_start"),
                "scope_end": node.get("scope_end"),
                "block_key": filepath,
            })
        for child in node.get("children", []):
            collect_anchors(child)
    collect_anchors(ast_dict)

    # Step 7: Build name mapping
    ast_trees = {filepath: {"type": "main", "ast": ast_dict}}
    name_mapping = build_name_mapping_from_asts(ast_trees, anchors)

    # Step 8: Obfuscate
    source_lines = source.splitlines(keepends=True)
    obfuscated, _applied_mapping, _details = obfuscate_source(
        anchors, source_lines, name_mapping
    )

    sys.stdout.write(obfuscated)


if __name__ == "__main__":
    main()
