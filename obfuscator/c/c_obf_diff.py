#!/usr/bin/env python3
"""
c_obfuscator.py - text-preserving C obfuscator with libclang AST if available,
and a regex-based fallback renamer when AST is unavailable.

Usage:
  python tools/c_obfuscator.py input.c -o output.c --libclang "C:\Program Files\LLVM\bin\libclang.dll"
"""

'''
基于AST
标识符重命名
- 优先使用libclang AST（抽象语法树）分析来识别可重命名的标识符（函数名、变量名等）
- 当libclang不可用时，使用正则表达式分析作为后备方案
- 生成随机的短标识符（如 z_abc123 格式）替换原标识符
- 跳过关键字、标准库函数名等特殊标识符

基于正则
字符串加密
- 对代码中的字符串字面量进行XOR加密
- 生成解密函数（如 dec_xxx 格式）用于运行时解密
- 将原字符串替换为对解密函数的调用，传入加密后的字节数组和密钥

数值常量拆分
- 通过位运算、数学运算等方式将常量值拆分为等效表达式
- 例如，将数字 42 转换为 ((45) - (3)) 或 ((42 ^ 13) ^ 13)
- 提高静态分析难度，同时保持程序行为不变

死代码注入
- 注入不可达或无意义的死代码函数（如 zdead_xxx 格式）
- 这些函数包含随机计算但不会影响程序的实际执行流程
- 增加代码复杂度和分析难度

不透明谓词插入
- 插入计算结果总是为真或假的复杂条件表达式
- 例如： if (((a*b) ^ ((a<<5)|(b>>3))) % mod == val)
- 条件分支中一个路径是真实执行路径，另一个路径调用死代码函数

注释混淆
- 保留特殊提示注释（包含 <<$ 和 $>> 的注释）
- 其他注释被替换为随机生成的垃圾注释
- 删除代码的可读性信息
'''
import os
import sys
import re
import argparse
import random
import string
import base64
import time

# Try to import clang.cindex
try:
    from clang import cindex
except Exception:
    cindex = None

DEFAULT_LIBCLANG = r"C:\Program Files\LLVM\bin\libclang.dll"

# Token regex
_identifier_or_string_re = re.compile(
    r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|\b[a-zA-Z_][a-zA-Z0-9_]*\b',
    flags=re.S
)
_string_literal_re = re.compile(r'"(?:\\.|[^"\\])*"', flags=re.S)

# ---------------- utilities ----------------
def gen_name(prefix="z"):
    s = ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"{prefix}_{s}"

def random_garbage_comment():
    return "/* " + ''.join(random.choices('abcdefghijklmnopqrstuvwxyz0123456789', k=12)) + " */"

def is_gpt_hint_comment(text: str) -> bool:
    try:
        return ('<<$' in text) and ('$>>' in text)
    except Exception:
        return False

# ---------------- libclang helper ----------------
def set_libclang_path(maybe_path=None):
    if cindex is None:
        return False
    if maybe_path:
        if os.path.exists(maybe_path):
            try:
                cindex.Config.set_library_file(maybe_path)
                return True
            except Exception:
                return False
        else:
            return False
    env = os.environ.get("CINDEX_LIBRARY_FILE") or os.environ.get("LLVM_PATH") or os.environ.get("LLVM_HOME")
    if env:
        if os.path.isdir(env):
            p = os.path.join(env, 'bin', 'libclang.dll')
        else:
            p = env
        if os.path.exists(p):
            try:
                cindex.Config.set_library_file(p)
                return True
            except Exception:
                return False
    if os.path.exists(DEFAULT_LIBCLANG):
        try:
            cindex.Config.set_library_file(DEFAULT_LIBCLANG)
            return True
        except Exception:
            return False
    return False

# ---------------- AST collection (if available) ----------------
DECL_KINDS = None
if cindex is not None:
    DECL_KINDS = {
        cindex.CursorKind.FUNCTION_DECL,
        cindex.CursorKind.VAR_DECL,
        cindex.CursorKind.PARM_DECL,
        cindex.CursorKind.FIELD_DECL,
        cindex.CursorKind.STRUCT_DECL,
        cindex.CursorKind.TYPEDEF_DECL,
        cindex.CursorKind.ENUM_CONSTANT_DECL,
    }

def collect_decls(tu, filename, blacklist=None):
    if cindex is None or DECL_KINDS is None:
        return []
    if blacklist is None:
        blacklist = set()
    decls = {}
    def visit(node):
        try:
            kind = node.kind
        except Exception:
            return
        if kind in DECL_KINDS and node.location and node.location.file and node.location.file.name == filename:
            name = node.spelling
            if name and name not in blacklist:
                decls[name] = decls.get(name, 0) + 1
        for c in node.get_children():
            visit(c)
    try:
        visit(tu.cursor)
    except Exception:
        pass
    return list(decls.keys())

# ---------------- regex-based fallback renamer ----------------
_C_KEYWORDS = {
 'auto','break','case','char','const','continue','default','do','double','else','enum',
 'extern','float','for','goto','if','inline','int','long','register','restrict','return',
 'short','signed','sizeof','static','struct','switch','typedef','union','unsigned','void',
 'volatile','while','_Bool','_Complex','_Imaginary'
}

_COMMON_LIBC_NAMES = {
    'printf','scanf','fprintf','fscanf','malloc','free','memcpy','memset','strcpy','strncpy',
    'strlen','strcmp','strncmp','exit','main','stdin','stdout','stderr','time','rand','srand'
}

_identifier_re_simple = re.compile(r'\b([A-Za-z_][A-Za-z0-9_]*)\b')

def collect_idents_by_regex(src_text, max_results=2000):
    cand = set()
    # function-like names (heuristic)
    for m in re.finditer(r'\b([A-Za-z_][A-Za-z0-9_]*)\s*\(', src_text):
        name = m.group(1)
        if name not in _C_KEYWORDS and name not in _COMMON_LIBC_NAMES:
            # skip calls on structs/pointers (obj->func or obj.func)
            before = src_text[max(0, m.start()-3):m.start()]
            if '->' in before or '.' in before:
                continue
            cand.add(name)
    # struct names
    for m in re.finditer(r'\bstruct\s+([A-Za-z_][A-Za-z0-9_]*)\b', src_text):
        cand.add(m.group(1))
    # typedef simple capture
    for m in re.finditer(r'\btypedef\s+[^{;]*\b([A-Za-z_][A-Za-z0-9_]*)\s*;', src_text):
        cand.add(m.group(1))
    # simple var declarations
    for m in re.finditer(r'\b(?:int|char|short|long|float|double|void|size_t|unsigned|signed)\s+([A-Za-z_][A-Za-z0-9_]*)\b', src_text):
        name = m.group(1)
        if name not in _C_KEYWORDS and name not in _COMMON_LIBC_NAMES:
            cand.add(name)
    # filter
    filtered = []
    for ident in cand:
        if ident.isupper():
            continue
        if ident in ('main',) or ident in _COMMON_LIBC_NAMES:
            continue
        filtered.append(ident)
        if len(filtered) >= max_results:
            break
    return filtered

def generate_rename_map_from_regex(src_text, prefix="z"):
    idents = collect_idents_by_regex(src_text)
    rename_map = {}
    for name in idents:
        if name.startswith("__") or name.startswith("tbl_"):
            continue
        new = gen_name(prefix)
        while new in rename_map.values():
            new = gen_name(prefix)
        rename_map[name] = new
    return rename_map

# ---------------- string encryption helpers ----------------
def gen_decrypt_fn_name():
    return gen_name("dec")

def xor_encrypt_bytes(bs: bytes, key=None):
    if key is None:
        key = random.randint(1, 255)
    enc = bytes((b ^ key) & 0xff for b in bs)
    return enc, key

def xor_encrypt_to_carray_literal(s: str):
    bs = s.encode('utf-8', errors='surrogatepass')
    enc, key = xor_encrypt_bytes(bs)
    arr = ", ".join(f"0x{b:02x}" for b in enc)
    return arr, len(enc), key

def replace_strings_with_decrypt_calls(src_text: str, decrypt_fn_name: str):
    entries = []
    new_parts = []
    last = 0
    for m in _string_literal_re.finditer(src_text):
        s_start, s_end = m.span()
        literal = m.group(0)
        raw_content = literal[1:-1]
        ctx_start = max(0, s_start - 200)
        ctx = src_text[ctx_start:s_start]
        allow_replace = False
        if ';' in ctx or '(' in ctx or '=' in ctx or '{' in ctx:
            allow_replace = True
        prefix = src_text[:s_start]
        if prefix.rfind('{') > prefix.rfind('}'):
            allow_replace = True
        if not allow_replace:
            new_parts.append(src_text[last:s_end])
            last = s_end
            continue
        try:
            arr, length, key = xor_encrypt_to_carray_literal(raw_content)
        except Exception:
            arr, length, key = xor_encrypt_to_carray_literal(raw_content)
        call = f'{decrypt_fn_name}((unsigned char[]){{{arr}}}, {length}, 0x{key:02x})'
        entries.append({"original": literal, "call": call, "len": length, "key": key})
        new_parts.append(src_text[last:s_start])
        new_parts.append(call)
        last = s_end
    new_parts.append(src_text[last:])
    return ''.join(new_parts), entries

def gen_decryptor_c(decrypt_fn_name):
    fn = f"""
/* Injected decryptor (auto-generated) */
#include <stdlib.h>
#include <string.h>
static char *{decrypt_fn_name}(const unsigned char *data, int len, unsigned char key) {{
    char *buf = (char*)malloc((size_t)len + 1);
    if (!buf) return NULL;
    for (int i = 0; i < len; ++i) {{
        buf[i] = (char)(data[i] ^ key);
    }}
    buf[len] = '\\0';
    return buf;
}}
"""
    return fn

# ---------------- numeric obfuscation ----------------
def obfuscate_number_expr(num: int):
    if num == 0:
        return "(0)"
    r = random.random()
    if r < 0.35:
        k = random.randint(1, 255)
        return f"(({num ^ k}) ^ {k})"
    elif r < 0.7:
        a = random.randint(1, min(6, num.bit_length() if num>0 else 1))
        hi = num >> a
        lo = num & ((1 << a) - 1)
        return f"((( {hi} ) << {a}) + ({lo}))"
    else:
        a = random.randint(1, max(2, abs(num) // 2))
        b = num - a
        return f"(({a})+({b}))"

def split_numeric_constants_in_text_enhanced(text: str, probability=0.7):
    def repl_in_segment(seg):
        def split_num(m):
            num = m.group(0)
            try:
                if num.startswith(('0x', '0X')):
                    val = int(num, 16)
                    if random.random() < probability:
                        a = random.randint(1, 255)
                        return f"((0x{val ^ a:x}) ^ 0x{a:x})"
                    else:
                        return num
                else:
                    if '.' in num:
                        return num
                    val = int(num, 10)
                    if abs(val) > 8 and random.random() < probability:
                        return obfuscate_number_expr(val)
                    else:
                        return num
            except Exception:
                return num
        return re.sub(r'\b0x[0-9A-Fa-f]+\b|\b\d+\b', split_num, seg)
    out = []
    last_end = 0
    for m in _identifier_or_string_re.finditer(text):
        start, end = m.span()
        if last_end < start:
            seg = text[last_end:start]
            out.append(repl_in_segment(seg))
        token = m.group(0)
        out.append(token)
        last_end = end
    if last_end < len(text):
        out.append(repl_in_segment(text[last_end:]))
    return ''.join(out)

# ---------------- dead / garbage code ----------------
def gen_dead_function(name=None):
    if name is None:
        name = gen_name("zdead")
    fn = f"""
#if defined(__GNUC__) || defined(__clang__)
__attribute__((noinline)) __attribute__((optimize("O0")))
#endif
static void {name}(void) {{
    volatile unsigned long t = 0;
    for (int i = 0; i < 5; ++i) {{
        t += ((unsigned long)rand() ^ (unsigned long)time(NULL)) & 0xffffUL;
    }}
    volatile int *p = (volatile int*)(&t);
    *p = (int)(t & 0xff);
#if defined(__GNUC__) || defined(__clang__)
    __asm__ __volatile__ ("" ::: "memory");
#endif
    (void)t;
}}
"""
    return fn, name

# ---------------- opaque predicate inserter ----------------
def gen_opaque_predicate_expr():
    a = random.randint(1, 1 << 30)
    b = random.randint(1, 1 << 30)
    mod = random.randint(97, 997) | 1
    val = ((a * b) ^ ((a << 5) | (b >> 3))) % mod
    expr = f"((((unsigned long){a} * (unsigned long){b}) ^ (((unsigned long){a}<<5) | ((unsigned long){b}>>3))) % {mod} == {val})"
    return expr

def insert_opaque_predicates(src_text: str, count=3, dead_func_name=None):
    candidates = []
    for m in re.finditer(r';', src_text):
        pos = m.end()
        prefix = src_text[:pos]
        if prefix.rfind('{') > prefix.rfind('}'):
            candidates.append(pos)
    if not candidates:
        return src_text, 0
    inserted = 0
    picks = sorted(random.sample(candidates, min(count, len(candidates))), reverse=True)
    new = src_text
    for p in picks:
        expr = gen_opaque_predicate_expr()
        df = dead_func_name or gen_name("zdead")
        snippet = f'\n/* opaque predicate */ if ({expr}) {{ /* real path */ }} else {{ {df}(); }}\n'
        new = new[:p] + snippet + new[p:]
        inserted += 1
    return new, inserted

# ---------------- identifier replacer ----------------
def replace_identifiers_preserve_strings(text, rename_map):
    out = []
    last_end = 0
    for m in _identifier_or_string_re.finditer(text):
        start, end = m.span()
        if last_end < start:
            out.append(text[last_end:start])
        token = m.group(0)
        if token[0] in ('"', "'"):
            out.append(token)
        else:
            if token in rename_map:
                out.append(rename_map[token])
            else:
                out.append(token)
        last_end = end
    if last_end < len(text):
        out.append(text[last_end:])
    return ''.join(out)

# ---------------- main obfuscation pipeline ----------------
def obfuscate_file_text(input_path, rename_map=None, enable_strings=True,
                        enable_const=True, enable_opaque=True, garbage_count=2,
                        strip_comments=True):
    with open(input_path, 'rb') as f:
        raw = f.read()
    try:
        src_text = raw.decode('utf-8')
    except Exception:
        src_text = raw.decode('utf-8', errors='ignore')

    injected_top_parts = []
    injected_funcs = []

    # strip/replace comments
    if strip_comments:
        def repl_block(m):
            s = m.group(0)
            if is_gpt_hint_comment(s):
                return s
            nl = s.count('\n')
            return (random_garbage_comment() + ("\n" * nl))
        src_text = re.sub(r'/\*.*?\*/', repl_block, src_text, flags=re.S)
        def repl_line(m):
            s = m.group(0)
            if is_gpt_hint_comment(s):
                return s
            return '//' + ''.join(random.choices('abcdefghijklmnopqrstuvwxyz', k=8))
        src_text = re.sub(r'//[^\n]*', repl_line, src_text)

    # strings
    decrypt_fn_name = None
    string_entries = []
    if enable_strings:
        decrypt_fn_name = gen_decrypt_fn_name()
        src_text, string_entries = replace_strings_with_decrypt_calls(src_text, decrypt_fn_name)
        if string_entries:
            decrypt_c = gen_decryptor_c(decrypt_fn_name)
            injected_top_parts.append(decrypt_c)

    # numeric obfuscation
    if enable_const:
        src_text = split_numeric_constants_in_text_enhanced(src_text, probability=0.7)

    # identifier rename
    if rename_map:
        src_text = replace_identifiers_preserve_strings(src_text, rename_map)

    # inject dead functions
    dead_names = []
    for i in range(garbage_count):
        fn_c, fn_name = gen_dead_function()
        injected_funcs.append(fn_c)
        dead_names.append(fn_name)

    # insert opaque predicates
    if enable_opaque and dead_names:
        src_text, inserted = insert_opaque_predicates(src_text, count=len(dead_names)*2, dead_func_name=random.choice(dead_names))

    # assemble top injections after includes
    insert_pos = 0
    incs = list(re.finditer(r'^\s*#\s*include.*$', src_text, flags=re.M))
    if incs:
        last_inc = incs[-1]
        insert_pos = last_inc.end()
    top_inject = "\n".join(injected_top_parts + injected_funcs) + "\n"
    if top_inject.strip():
        src_text = src_text[:insert_pos] + "\n" + top_inject + src_text[insert_pos:]

    return src_text, {"strings": string_entries, "dead": dead_names}

# ---------------- CLI main ----------------
def main():
    parser = argparse.ArgumentParser(description="C obfuscator with regex fallback renamer.")
    parser.add_argument('input', help='input C file (single TU)')
    parser.add_argument('-o','--output', help='output obfuscated C file', default=None)
    parser.add_argument('--libclang', help='path to libclang.dll (optional)', default=None)
    parser.add_argument('--no-strip-comments', action='store_true', help='do not replace comments')
    parser.add_argument('--no-strings', action='store_true', help='do not encrypt string literals')
    parser.add_argument('--no-const', action='store_true', help='do not obfuscate numeric constants')
    parser.add_argument('--no-opaque', action='store_true', help='do not insert opaque predicates')
    parser.add_argument('--clang-args', help='semicolon-separated additional clang args (e.g. -IC:\\inc;-DDEBUG)', default=None)
    parser.add_argument('--garbage-count', type=int, default=2, help='how many dead/garbage functions to inject')
    args = parser.parse_args()

    input_file = args.input
    if not os.path.isfile(input_file):
        print("Error: input file not found:", input_file)
        sys.exit(1)

    lib_ok = False
    if cindex is not None:
        lib_ok = set_libclang_path(args.libclang)
        if lib_ok:
            print("Using libclang (set).")
        else:
            print("Warning: libclang not set or failed; continuing in regex-fallback mode.")
    else:
        print("clang.cindex not available; running in regex-fallback mode.")

    rename_map = {}
    # Try AST parse if libclang available
    if cindex is not None and lib_ok:
        try:
            idx = cindex.Index.create()
            parse_args = []
            if args.clang_args:
                for part in args.clang_args.split(';'):
                    p = part.strip()
                    if p:
                        parse_args.append(p)
            try:
                tu = idx.parse(input_file, args=parse_args)
                blacklist = set(['main'])
                decls = collect_decls(tu, input_file, blacklist=blacklist)
                for name in decls:
                    if not name:
                        continue
                    new = gen_name("z")
                    while new in rename_map.values():
                        new = gen_name("z")
                    rename_map[name] = new
                print(f"Collected declarations (count={len(decls)}).")
                if len(decls) > 0:
                    print(", ".join(decls[:50]))
            except Exception as e:
                print("Warning: libclang parse failed:", e)
                print("Falling back to regex-based rename.")
                # fallback below
                lib_ok = False
        except Exception as e:
            print("Warning creating clang Index failed:", e)
            lib_ok = False

    # If AST parse unavailable, use regex-based fallback to build rename_map
    if not rename_map:
        try:
            with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
                src_sample = f.read()
            rename_map = generate_rename_map_from_regex(src_sample, prefix="z")
            if rename_map:
                print(f"[INFO] Generated rename_map via regex fallback (count={len(rename_map)}).")
            else:
                print("[INFO] Regex fallback generated no rename candidates (rename_map empty).")
        except Exception as e:
            print("[WARN] regex fallback failed to read file:", e)
            rename_map = {}

    # run obfuscation pipeline
    obf_text, meta = obfuscate_file_text(
        input_file,
        rename_map=rename_map,
        enable_strings=(not args.no_strings),
        enable_const=(not args.no_const),
        enable_opaque=(not args.no_opaque),
        garbage_count=max(0, args.garbage_count),
        strip_comments=(not args.no_strip_comments)
    )

    out_file = args.output or (os.path.splitext(input_file)[0] + "_obf.c")
    with open(out_file, 'w', encoding='utf-8', errors='ignore') as f:
        f.write(obf_text)

    print("Wrote obfuscated file to:", out_file)
    if meta.get("strings"):
        print("Injected decryptor calls for", len(meta["strings"]), "strings.")
    if meta.get("dead"):
        print("Injected dead/garbage functions:", meta["dead"])
    if rename_map:
        print("Example rename_map (up to 40):")
        for k, v in list(rename_map.items())[:40]:
            print(f"  {k} -> {v}")
    else:
        print("No rename_map applied (AST unavailable and regex fallback found no candidates).")

if __name__ == "__main__":
    main()
