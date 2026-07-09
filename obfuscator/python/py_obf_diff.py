#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
obf.py - 基于 AST 的轻量 Python 混淆器（字符串 & 数字混淆、标识符重命名、死代码注入、不透明谓词）

用法:
  python tools/obf.py -i input.py -o output_obf.py [--no-rename] [--rename-ratio 1.0] [--verbose]
"""
from __future__ import annotations
import ast
import random
import string
import argparse
import os
import textwrap
import sys
from typing import Set, Dict, Tuple, List

# -----------------------
# 工具函数
# -----------------------
def gen_name(prefix: str = "z") -> str:
    return prefix + "_" + ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))

def xor_encrypt_bytes(bs: bytes, key: int | None = None) -> Tuple[bytes, int]:
    if key is None:
        key = random.randint(1, 255)
    enc = bytes((b ^ key) & 0xff for b in bs)
    return enc, key

def xor_encrypt_to_int_list(s: str) -> Tuple[List[int], int]:
    bs = s.encode('utf-8', errors='surrogatepass')
    enc, key = xor_encrypt_bytes(bs)
    return list(enc), key

def gen_decryptor_src(fn_name: str) -> str:
    src = f"""
def {fn_name}(data: bytes, key: int) -> str:
    # 自动生成的解密函数：优先 utf-8，失败回退 latin1（避免 UnicodeDecodeError）
    b = bytes(data)
    out = bytes((x ^ key) & 0xff for x in b)
    try:
        return out.decode('utf-8')
    except Exception:
        return out.decode('latin1')
"""
    return textwrap.dedent(src)

# -----------------------
# AST Transformers
# -----------------------
class StringAndFstringTransformer(ast.NodeTransformer):
    """
    将字符串常量替换为 decrypt(bytes([...]), key)
    也把 f-string 静态部分替换为 decrypt(...)，表达式部分包装为 str(...)
    记录 entries: list of (original_string, key)
    """
    def __init__(self, decrypt_fn_name: str):
        self.decrypt_fn_name = decrypt_fn_name
        self.entries: List[Tuple[str, int]] = []

    def _make_decrypt_call_node(self, s: str) -> ast.Expr:
        ints, key = xor_encrypt_to_int_list(s)
        bytes_list = ast.List(elts=[ast.Constant(v) for v in ints], ctx=ast.Load())
        bytes_call = ast.Call(func=ast.Name(id='bytes', ctx=ast.Load()), args=[bytes_list], keywords=[])
        call = ast.Call(
            func=ast.Name(id=self.decrypt_fn_name, ctx=ast.Load()),
            args=[bytes_call, ast.Constant(key)],
            keywords=[]
        )
        self.entries.append((s, key))
        return call

    def visit_Constant(self, node: ast.Constant):
        if isinstance(node.value, str):
            return ast.copy_location(self._make_decrypt_call_node(node.value), node)
        return node

    def visit_JoinedStr(self, node: ast.JoinedStr):
        parts: List[ast.expr] = []
        for val in node.values:
            if isinstance(val, ast.FormattedValue):
                parts.append(ast.Call(func=ast.Name(id='str', ctx=ast.Load()), args=[val.value], keywords=[]))
            elif isinstance(val, ast.Constant) and isinstance(val.value, str):
                parts.append(self._make_decrypt_call_node(val.value))
            else:
                # 保险降级：wrap with str()
                try:
                    parts.append(ast.Call(func=ast.Name(id='str', ctx=ast.Load()), args=[val], keywords=[]))
                except Exception:
                    parts.append(ast.Constant(''))
        if not parts:
            return ast.copy_location(ast.Constant(''), node)
        expr = parts[0]
        for p in parts[1:]:
            expr = ast.BinOp(left=expr, op=ast.Add(), right=p)
        return ast.copy_location(expr, node)

class NumberObfuscator(ast.NodeTransformer):
    """
    将整数常量替换为等价表达式；保护 0..255 的小常量（常用于 bytes 列表）
    """
    def __init__(self, prob: float = 0.9):
        self.prob = prob

    def visit_Constant(self, node: ast.Constant):
        if isinstance(node.value, int):
            num = node.value
            if 0 <= num <= 255:
                return node
            if random.random() > self.prob:
                return node
            if num == 0:
                return ast.copy_location(ast.Constant(0), node)
            r = random.random()
            if r < 0.35:
                k = random.randint(1, 255)
                inner = ast.BinOp(left=ast.Constant(num), op=ast.BitXor(), right=ast.Constant(k))
                expr = ast.BinOp(left=inner, op=ast.BitXor(), right=ast.Constant(k))
                return ast.copy_location(expr, node)
            elif r < 0.7:
                max_shift = max(1, (num.bit_length() if num > 0 else 1) - 1)
                a = random.randint(1, min(6, max_shift))
                hi = num >> a
                lo = num & ((1 << a) - 1)
                left = ast.BinOp(left=ast.Constant(hi), op=ast.LShift(), right=ast.Constant(a))
                expr = ast.BinOp(left=left, op=ast.Add(), right=ast.Constant(lo))
                return ast.copy_location(expr, node)
            else:
                if num >= 0:
                    a = random.randint(1, max(2, num // 2))
                else:
                    a = -random.randint(1, max(2, abs(num) // 2))
                b = num - a
                expr = ast.BinOp(left=ast.Constant(a), op=ast.Add(), right=ast.Constant(b))
                return ast.copy_location(expr, node)
        return node

# -----------------------
# Identifier collector & renamer (robust)
# -----------------------
class IdentifierCollector(ast.NodeVisitor):
    """
    收集可重命名标识符（定义处）：
    - FunctionDef.name, AsyncFunctionDef.name
    - ClassDef.name
    - Assign/AnnAssign targets (simple Name, tuple/list of Names)
    - For.target, With.optional_vars, ExceptHandler.name
    - Function parameters (args/kwonlyargs/vararg/kwarg)
    不收集：属性 attr、导入模块/包名（import/importfrom）、内置名或 dunder 名（__name__）
    """
    def __init__(self):
        self.names: Set[str] = set()
        self.imported: Set[str] = set()

    def visit_Import(self, node: ast.Import):
        for alias in node.names:
            if alias.asname:
                self.imported.add(alias.asname)
            else:
                self.imported.add(alias.name.split('.')[0])

    def visit_ImportFrom(self, node: ast.ImportFrom):
        for alias in node.names:
            if alias.asname:
                self.imported.add(alias.asname)
            else:
                self.imported.add(alias.name)

    def visit_FunctionDef(self, node: ast.FunctionDef):
        self.names.add(node.name)
        for arg in node.args.args + node.args.kwonlyargs:
            self.names.add(arg.arg)
        if node.args.vararg:
            self.names.add(node.args.vararg.arg)
        if node.args.kwarg:
            self.names.add(node.args.kwarg.arg)
        self.generic_visit(node)

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef):
        self.visit_FunctionDef(node)

    def visit_ClassDef(self, node: ast.ClassDef):
        self.names.add(node.name)
        self.generic_visit(node)

    def visit_Assign(self, node: ast.Assign):
        for t in node.targets:
            self._collect_target(t)
        self.generic_visit(node)

    def visit_AnnAssign(self, node: ast.AnnAssign):
        self._collect_target(node.target)
        self.generic_visit(node)

    def visit_AugAssign(self, node: ast.AugAssign):
        self._collect_target(node.target)
        self.generic_visit(node)

    def visit_For(self, node: ast.For):
        self._collect_target(node.target)
        self.generic_visit(node)

    def visit_With(self, node: ast.With):
        for item in node.items:
            if item.optional_vars:
                self._collect_target(item.optional_vars)
        self.generic_visit(node)

    def visit_ExceptHandler(self, node: ast.ExceptHandler):
        if node.name:
            if isinstance(node.name, str):
                self.names.add(node.name)
            elif isinstance(node.name, ast.Name):
                self.names.add(node.name.id)
        self.generic_visit(node)

    def _collect_target(self, target: ast.AST):
        if isinstance(target, ast.Name):
            self.names.add(target.id)
        elif isinstance(target, (ast.Tuple, ast.List)):
            for e in target.elts:
                self._collect_target(e)

class IdentifierRenamer(ast.NodeTransformer):
    """
    在 AST 上进行一致的重命名：替换定义处与引用处的 Name 节点
    - 不改 Attribute.attr
    - 替换 FunctionDef.name, ClassDef.name, arg.arg
    """
    def __init__(self, rename_map: Dict[str, str], verbose: bool = False):
        self.rename_map = rename_map
        self.verbose = verbose

    def visit_Name(self, node: ast.Name):
        if node.id in self.rename_map:
            old = node.id
            node.id = self.rename_map[old]
            if self.verbose:
                print(f"Renamed Name usage: {old} -> {node.id}")
        return node

    def visit_FunctionDef(self, node: ast.FunctionDef):
        old_name = node.name
        if old_name in self.rename_map:
            node.name = self.rename_map[old_name]
            if self.verbose:
                print(f"Renamed FunctionDef: {old_name} -> {node.name}")
        for arg in node.args.args + node.args.kwonlyargs:
            if arg.arg in self.rename_map:
                old = arg.arg
                arg.arg = self.rename_map[old]
                if self.verbose:
                    print(f"Renamed param: {old} -> {arg.arg}")
        if node.args.vararg and node.args.vararg.arg in self.rename_map:
            old = node.args.vararg.arg
            node.args.vararg.arg = self.rename_map[old]
            if self.verbose:
                print(f"Renamed vararg: {old} -> {node.args.vararg.arg}")
        if node.args.kwarg and node.args.kwarg.arg in self.rename_map:
            old = node.args.kwarg.arg
            node.args.kwarg.arg = self.rename_map[old]
            if self.verbose:
                print(f"Renamed kwarg: {old} -> {node.args.kwarg.arg}")
        self.generic_visit(node)
        return node

    def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef):
        return self.visit_FunctionDef(node)

    def visit_ClassDef(self, node: ast.ClassDef):
        old_name = node.name
        if old_name in self.rename_map:
            node.name = self.rename_map[old_name]
            if self.verbose:
                print(f"Renamed ClassDef: {old_name} -> {node.name}")
        self.generic_visit(node)
        return node

    def visit_arg(self, node: ast.arg):
        if node.arg in self.rename_map:
            old = node.arg
            node.arg = self.rename_map[old]
            if self.verbose:
                print(f"Renamed arg node: {old} -> {node.arg}")
        return node

    def visit_Assign(self, node: ast.Assign):
        for t in node.targets:
            self._rename_target(t)
        self.generic_visit(node)
        return node

    def visit_AnnAssign(self, node: ast.AnnAssign):
        self._rename_target(node.target)
        self.generic_visit(node)
        return node

    def visit_For(self, node: ast.For):
        self._rename_target(node.target)
        self.generic_visit(node)
        return node

    def visit_With(self, node: ast.With):
        for item in node.items:
            if item.optional_vars:
                self._rename_target(item.optional_vars)
        self.generic_visit(node)
        return node

    def visit_ExceptHandler(self, node: ast.ExceptHandler):
        if isinstance(node.name, str):
            if node.name in self.rename_map:
                old = node.name
                node.name = self.rename_map[old]
                if self.verbose:
                    print(f"Renamed except name (str): {old} -> {node.name}")
        elif isinstance(node.name, ast.Name):
            if node.name.id in self.rename_map:
                old = node.name.id
                node.name.id = self.rename_map[old]
                if self.verbose:
                    print(f"Renamed except name (Name): {old} -> {node.name.id}")
        self.generic_visit(node)
        return node

    def _rename_target(self, target: ast.AST):
        if isinstance(target, ast.Name):
            if target.id in self.rename_map:
                old = target.id
                target.id = self.rename_map[old]
                if self.verbose:
                    print(f"Renamed target: {old} -> {target.id}")
        elif isinstance(target, (ast.Tuple, ast.List)):
            for e in target.elts:
                self._rename_target(e)

# -----------------------
# Dead code injector (optional simple)
# -----------------------
class DeadCodeInjector(ast.NodeTransformer):
    def gen_dead_function(self, name: str | None = None) -> ast.FunctionDef:
        if name is None:
            name = gen_name("dead_func")
        params = [ast.arg(arg="x", annotation=None)]
        func = ast.FunctionDef(
            name=name,
            args=ast.arguments(args=params, posonlyargs=[], vararg=None, kwonlyargs=[], kw_defaults=[], kwarg=None, defaults=[]),
            body=[ast.Expr(value=ast.Constant(value="Dead code placeholder")), ast.Return(value=ast.Constant(value=None))],
            decorator_list=[]
        )
        return func

    def visit_Module(self, node: ast.Module):
        node.body.insert(0, self.gen_dead_function())
        return node

# -----------------------
# 主混淆流程
# -----------------------
def collect_identifiers_after_string_transform(tree: ast.AST, excluded_names: Set[str]) -> Set[str]:
    coll = IdentifierCollector()
    coll.visit(tree)
    candidates = set()
    for n in coll.names:
        if n in coll.imported:
            continue
        if n in excluded_names:
            continue
        if n.startswith("__") and n.endswith("__"):
            continue
        if n in dir(__builtins__):
            continue
        candidates.add(n)
    return candidates

def obfuscate_with_ast(src_text: str, decrypt_fn_name: str | None = None, rename_ratio: float = 1.0, do_rename: bool = True, verbose: bool = False) -> Tuple[str, dict]:
    _PLACEHOLDER = '<<$ target $>>'
    _SENTINEL = '__GPT_PLACEHOLDER_TARGET__'
    src_text = src_text.replace(_PLACEHOLDER, _SENTINEL)

    try:
        tree = ast.parse(src_text)
    except SyntaxError as e:
        raise RuntimeError(f"Parse error: {e}")

    if decrypt_fn_name is None:
        decrypt_fn_name = gen_name("dec")
    decrypt_node = ast.parse(gen_decryptor_src(decrypt_fn_name)).body[0]

    # 1) Number obfuscation first (but we protect 0..255 in NumberObfuscator)
    num_t = NumberObfuscator(prob=0.95)
    tree = num_t.visit(tree)
    ast.fix_missing_locations(tree)

    # 2) String/f-string transform
    str_t = StringAndFstringTransformer(decrypt_fn_name)
    tree = str_t.visit(tree)
    ast.fix_missing_locations(tree)

    # Build exclude set: decrypt_fn, builtin names, and any decrypted strings that look like identifiers
    exclude = {decrypt_fn_name, "__name__", "__main__", "print", "eval", "open", "bytes", "str", "int", "float", "math"}
    for s, _k in str_t.entries:
        if isinstance(s, str) and s.isidentifier():
            exclude.add(s)

    meta = {
        "decrypt_fn": decrypt_fn_name,
        "strings_replaced": len(str_t.entries),
    }

    # 3) Identifier collection & renaming
    rename_map: Dict[str, str] = {}
    renamed_count = 0
    rename_sample: List[str] = []

    if do_rename:
        candidates = sorted(collect_identifiers_after_string_transform(tree, exclude))
        if rename_ratio >= 1.0:
            to_rename = candidates
        elif rename_ratio <= 0:
            to_rename = []
        else:
            import math
            k = max(0, int(math.floor(len(candidates) * rename_ratio)))
            to_rename = set(random.sample(candidates, k)) if k > 0 else set()
        used = set(exclude)
        for name in to_rename:
            new = gen_name("z")
            while new in used:
                new = gen_name("z")
            used.add(new)
            rename_map[name] = new
        rename_sample = list(rename_map.items())
        if verbose:
            print("Rename map (sample):")
            for a,b in rename_sample[:50]:
                print(f"  {a} -> {b}")

        if rename_map:
            renamer = IdentifierRenamer(rename_map, verbose=verbose)
            tree = renamer.visit(tree)
            ast.fix_missing_locations(tree)
            renamed_count = len(rename_map)

    # 4) Insert dead code (optional)
    tree = DeadCodeInjector().visit(tree)
    ast.fix_missing_locations(tree)

    # 5) Put decrypt function at top
    new_mod = ast.Module(body=[decrypt_node] + tree.body, type_ignores=[])
    ast.fix_missing_locations(new_mod)

    # 6) Unparse
    try:
        src_out = ast.unparse(new_mod)
    except Exception:
        try:
            import astor
            src_out = astor.to_source(new_mod)
        except Exception as e:
            raise RuntimeError("无法将 AST 转换为源码：需要 Python 3.9+ 或安装 astor.") from e

    src_out = src_out.replace(_SENTINEL, _PLACEHOLDER)
    meta.update({"renamed_count": renamed_count, "rename_map_sample": rename_sample})
    return src_out, meta

# -----------------------
# CLI
# -----------------------
def main():
    p = argparse.ArgumentParser(description="基于 AST 的 Python 混淆器（字符串/数字/标识符重命名/死代码）")
    p.add_argument("-i","--input", required=True, help="输入 Python 文件")
    p.add_argument("-o","--output", required=True, help="输出混淆文件")
    p.add_argument("--no-rename", action="store_true", help="不进行标识符重命名（仅字符串/数字混淆）")
    p.add_argument("--rename-ratio", type=float, default=1.0, help="重命名覆盖比例，0.0-1.0（默认 1.0）")
    p.add_argument("--verbose", action="store_true", help="打印重命名映射（调试用）")
    args = p.parse_args()

    if not os.path.isfile(args.input):
        print("Error: 输入文件不存在:", args.input); sys.exit(1)

    src = open(args.input, "r", encoding="utf-8", errors="ignore").read()
    out_src, meta = obfuscate_with_ast(src, decrypt_fn_name=None, rename_ratio=args.rename_ratio, do_rename=not args.no_rename, verbose=args.verbose)

    with open(args.output, "w", encoding="utf-8") as f:
        f.write(out_src)

    print("✅ 混淆完成:", args.output)
    print("decrypt 函数名:", meta["decrypt_fn"], "strings_replaced:", meta["strings_replaced"], "renamed_count:", meta["renamed_count"])
    if args.verbose:
        print("rename_map sample:", meta["rename_map_sample"][:30])

if __name__ == "__main__":
    main()
