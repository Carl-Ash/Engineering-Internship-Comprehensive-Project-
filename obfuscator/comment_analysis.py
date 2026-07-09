import ast
import tokenize
import io
from typing import List, Dict, Any, Optional

# Global comment ID counter
comment_counter = 0

# Comment type names exactly as defined in the documentation
COMMENT_TYPES = {
    "FILE": "File_Comment",
    "BLOCK": "Block_Comment",
    "NULL": "Null_Comment",
    "INLINE": "Inline_Comment",
    "DOCSTRING": "docString"
}

# ------------------------------------------------------------------------------
# Line information analysis
# ------------------------------------------------------------------------------
def get_code_line_info(source: str) -> Dict[int, Dict[str, Any]]:
    if not isinstance(source, str):
        raise TypeError("Source must be string")
    
    line_info = {}
    lines = source.splitlines()
    try:
        tokens = list(tokenize.generate_tokens(io.StringIO(source).readline))
    except Exception:
        tokens = []

    for idx, line in enumerate(lines, 1):
        stripped = line.strip()
        line_info[idx] = {
            "is_empty": len(stripped) == 0,
            "has_code": False,
            "has_comment": False,
            "comment_only": False,
        }

    for tok in tokens:
        try:
            t, val, (s_ln, _), (e_ln, _), _ = tok
            if t == tokenize.COMMENT:
                for ln in range(s_ln, e_ln + 1):
                    if ln in line_info:
                        line_info[ln]["has_comment"] = True
            elif t not in (tokenize.NL, tokenize.NEWLINE, tokenize.INDENT, tokenize.DEDENT):
                for ln in range(s_ln, e_ln + 1):
                    if ln in line_info:
                        line_info[ln]["has_code"] = True
        except Exception:
            continue

    for ln in line_info:
        li = line_info[ln]
        li["comment_only"] = li["has_comment"] and not li["has_code"]
        li["is_empty"] = not li["has_code"] and not li["has_comment"]
    
    return line_info

# ------------------------------------------------------------------------------
# AST parent node mapping
# ------------------------------------------------------------------------------
def get_ast_node_parent_map(tree: ast.AST) -> dict:
    parent_map = {}
    def traverse(node, parent=None):
        if parent and isinstance(parent, (
            ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef,
            ast.If, ast.For, ast.While, ast.With, ast.Try
        )):
            parent_map[id(node)] = parent
        for child in ast.iter_child_nodes(node):
            traverse(child, node)
    traverse(tree)
    return parent_map

# ------------------------------------------------------------------------------
# Inline_Comment scope (as defined in documentation)
# ------------------------------------------------------------------------------
def parse_inline_code_scope(lineno: int, line_info: dict, source_lines: list, parent_node):
    max_ln = len(source_lines)
    complex_types = (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef,
                     ast.If, ast.For, ast.While, ast.With)

    if parent_node and isinstance(parent_node, complex_types):
        if getattr(parent_node, "lineno", None) == lineno:
            s = getattr(parent_node, "lineno", lineno)
            e = getattr(parent_node, "end_lineno", lineno)
            return s, e

    syms = (",", "(", "[", "{", "=", "+", "-", "*", "/", "|", "&", ":")
    src = [""] + source_lines
    scope_start = lineno
    while scope_start > 1:
        line = src[scope_start].strip()
        if not line or (line and line[-1] in syms):
            scope_start -= 1
        else:
            break
    return scope_start, lineno

# ------------------------------------------------------------------------------
# Core: calculate scope for all 5 comment types (as defined in documentation)
# ------------------------------------------------------------------------------
def calculate_comment_scope(cmt, line_info, source_lines, ast_tree):
    ln = cmt["lineno"]
    max_ln = len(source_lines)
    typ = cmt["comment_type"]

    if typ == COMMENT_TYPES["FILE"]:
        first_code = max_ln
        for i in range(1, max_ln + 1):
            if line_info[i]["has_code"] and not line_info[i]["comment_only"]:
                first_code = i
                break
        cmt["scope_start"] = first_code
        cmt["scope_end"] = max_ln

    elif typ == COMMENT_TYPES["BLOCK"]:
        start = ln + 1
        while start <= max_ln and line_info[start]["is_empty"]:
            start += 1
        cmt["scope_start"] = start if start <= max_ln else ln
        end = cmt["scope_start"]
        while end <= max_ln:
            if line_info[end]["is_empty"] | line_info[end]["comment_only"]:
                end -= 1
                break
            end += 1
        cmt["scope_end"] = min(end, max_ln)

    elif typ == COMMENT_TYPES["NULL"]:
        cmt["scope_start"] = ln
        cmt["scope_end"] = ln

    elif typ == COMMENT_TYPES["INLINE"]:
        s, e = parse_inline_code_scope(ln, line_info, source_lines, cmt.get("parent_node"))
        cmt["scope_start"] = s
        cmt["scope_end"] = e

    elif typ == COMMENT_TYPES["DOCSTRING"]:
        p = cmt.get("parent_node")
        if p:
            cmt["scope_start"] = getattr(p, "lineno", ln)
            cmt["scope_end"] = getattr(p, "end_lineno", ln)
        else:
            cmt["scope_start"] = 1
            cmt["scope_end"] = max_ln

    else:
        cmt["scope_start"] = ln
        cmt["scope_end"] = ln

    return cmt

# ------------------------------------------------------------------------------
# Strict classification of the 5 comment types (as defined in documentation)
# ------------------------------------------------------------------------------
def classify_comment(s_ln, line_info, parent_node, max_ln):
    curr = line_info[s_ln]
    prev_ln = s_ln - 1
    next_ln = s_ln + 1
    prev = line_info.get(prev_ln, {})
    next_ = line_info.get(next_ln, {})
    comment_only = curr["comment_only"]

    if comment_only:
        if next_ln > max_ln:
            return COMMENT_TYPES["NULL"]
        if prev.get("has_code") and next_.get("is_empty"):
            return COMMENT_TYPES["NULL"]

    if comment_only:
        if next_.get("has_code"):
            return COMMENT_TYPES["BLOCK"]
        if (prev_ln < 1 or prev.get("is_empty")) and (next_ln > max_ln or next_.get("is_empty")):
            return COMMENT_TYPES["BLOCK"]

    if curr["has_code"] and curr["has_comment"]:
        return COMMENT_TYPES["INLINE"]

    return COMMENT_TYPES["BLOCK"]

# ------------------------------------------------------------------------------
# Extract docstrings
# ------------------------------------------------------------------------------
def extract_docstrings(node, ast_tree, line_info, source_lines, comments):
    global comment_counter
    ds = ast.get_docstring(node)
    if not ds:
        return
    comment_counter += 1
    doc_node = None
    for ch in ast.iter_child_nodes(node):
        if isinstance(ch, ast.Expr) and isinstance(ch.value, ast.Constant):
            doc_node = ch
            break
    if doc_node is None:
        return
    
    start_lineno = getattr(doc_node, 'lineno', None) or getattr(node, 'lineno', 1)
    end_lineno = getattr(doc_node, 'end_lineno', None)
    
    c = {
        "id": comment_counter,
        "comment_type": COMMENT_TYPES["DOCSTRING"],
        "value": ds.strip(),
        "lineno": start_lineno,
        "parent_node": node,
        "end_lineno": end_lineno,
    }
    comments.append(calculate_comment_scope(c, line_info, source_lines, ast_tree))

# ------------------------------------------------------------------------------
# Main function: extract all comments
# ------------------------------------------------------------------------------
def get_comments(source: str, tree: Optional[ast.AST] = None) -> List[Dict[str, Any]]:
    """
    Extract all comments from source code.
    If an external AST is provided (tree), it will be reused to avoid
    duplicate parsing and to ensure parent node IDs are consistent.
    """
    global comment_counter
    comment_counter = 0
    comments = []
    lines = source.splitlines()
    max_ln = len(lines)
    if max_ln == 0:
        return []

    line_info = get_code_line_info(source)

    # Use provided tree or parse a new one
    if tree is None:
        try:
            tree = ast.parse(source)
        except Exception:
            tree = ast.Module(body=[])

    # Ensure all nodes have end_lineno for consistency
    for n in ast.walk(tree):
        if hasattr(n, "lineno") and not hasattr(n, "end_lineno"):
            n.end_lineno = n.lineno

    ast_tree = tree  # internal alias

    # Extract docstrings
    extract_docstrings(ast_tree, ast_tree, line_info, lines, comments)
    for n in ast.walk(ast_tree):
        if isinstance(n, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
            extract_docstrings(n, ast_tree, line_info, lines, comments)

    # Extract # comments
    file_candidates = []
    try:
        tokens = list(tokenize.generate_tokens(io.StringIO(source).readline))
    except Exception:
        tokens = []

    for tok in tokens:
        if tok.type != tokenize.COMMENT:
            continue
        try:
            s_ln, _ = tok.start
            txt = tok.string[1:].strip()
            if not txt:
                continue
            comment_counter += 1

            parent_node = None
            for n in ast.walk(ast_tree):
                nl = getattr(n, "lineno", 0)
                nel = getattr(n, "end_lineno", nl)
                if nl <= s_ln <= nel:
                    parent_node = n
                    break

            ctype = classify_comment(s_ln, line_info, parent_node, max_ln)
            c = {
                "id": comment_counter,
                "comment_type": ctype,
                "value": txt,
                "lineno": s_ln,
                "parent_node": parent_node,
                "end_lineno": s_ln,
            }

            is_file_header = False
            if ctype == COMMENT_TYPES["BLOCK"]:
                has_code_before = False
                for i in range(1, s_ln):
                    if line_info[i]["has_code"] and not line_info[i]["comment_only"]:
                        has_code_before = True
                        break
                if not has_code_before:
                    file_candidates.append(c)
                    is_file_header = True

            if not is_file_header:
                comments.append(calculate_comment_scope(c, line_info, lines, ast_tree))

        except Exception:
            continue

    # Merge file header comments
    if file_candidates:
        comment_counter += 1
        fc = {
            "id": comment_counter,
            "comment_type": COMMENT_TYPES["FILE"],
            "value": "\n".join(x["value"] for x in file_candidates),
            "lineno": min(x["lineno"] for x in file_candidates),
            "parent_node": ast_tree,
            "end_lineno": max(x["end_lineno"] for x in file_candidates),
        }
        comments.append(calculate_comment_scope(fc, line_info, lines, ast_tree))
        # Preserve end_lineno
        comments[-1]["end_lineno"] = fc["end_lineno"]

    # Merge adjacent comments of the same type (excluding inline)
    comments.sort(key=lambda x: x["lineno"])
    def merge_adjacent_comments(cmt_list):
        if not cmt_list:
            return []
        merged = []
        current = cmt_list[0].copy()
        for nxt in cmt_list[1:]:
            if current["comment_type"] == COMMENT_TYPES["INLINE"] or nxt["comment_type"] == COMMENT_TYPES["INLINE"]:
                merged.append(current)
                current = nxt.copy()
                continue
            if current["comment_type"] == nxt["comment_type"] and current["end_lineno"] + 1 == nxt["lineno"]:
                current["value"] += "\n" + nxt["value"]
                current["end_lineno"] = nxt["lineno"]
            else:
                merged.append(current)
                current = nxt.copy()
        merged.append(current)
        return merged

    comments = merge_adjacent_comments(comments)

    # Recalculate scopes but preserve end_lineno
    for c in comments:
        end_lineno = c["end_lineno"]
        c = calculate_comment_scope(c, line_info, lines, ast_tree)
        c["end_lineno"] = end_lineno

    return sorted(comments, key=lambda x: x["lineno"])

# ------------------------------------------------------------------------------
# Testing
# ------------------------------------------------------------------------------
if __name__ == "__main__":
    import sys
    filename = "example.py"
    if len(sys.argv) > 1:
        filename = sys.argv[1]
    try:
        with open(filename, "r", encoding="utf-8") as f:
            test_code = f.read()
    except FileNotFoundError:
        print(f"Error: file {filename} not found")
        sys.exit(1)
    cs = get_comments(test_code)
    print(f"Total comments found: {len(cs)}")
    for c in cs:
        print(f"[{c['comment_type']}] lines:{c['lineno']:2d}-{c['end_lineno']:2d} scope:[{c['scope_start']:2d},{c['scope_end']:2d}]\n{c['value'][:60]}\n")