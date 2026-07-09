#!/usr/bin/env python3
import os
import sys
import re
import argparse
import random
import string
import base64

# Try to import clang.cindex
try:
    from clang import cindex
except Exception as e:
    print("Error: clang.cindex import failed. Make sure python clang bindings are installed (pip install clang).")
    print("Exception:", e)
    sys.exit(1)

# Default libclang path for Windows (as you specified)
DEFAULT_LIBCLANG = r"C:\Program Files\LLVM\bin\libclang.dll"

# Allow override by env CINDEX_LIBRARY_FILE or command-line argument
def set_libclang_path(maybe_path=None):
    # prefer explicit argument
    if maybe_path:
        if os.path.exists(maybe_path):
            cindex.Config.set_library_file(maybe_path)
            return True
        else:
            print(f"Warning: provided libclang path not found: {maybe_path}")
            return False
    # try environment variable
    env = os.environ.get("CINDEX_LIBRARY_FILE") or os.environ.get("LLVM_PATH") or os.environ.get("LLVM_HOME")
    if env:
        if os.path.isdir(env):
            p = os.path.join(env, 'bin', 'libclang.dll')
        else:
            p = env
        if os.path.exists(p):
            cindex.Config.set_library_file(p)
            return True
    # default known location
    if os.path.exists(DEFAULT_LIBCLANG):
        cindex.Config.set_library_file(DEFAULT_LIBCLANG)
        return True
    return False

# ---------- utilities ----------
def gen_name(prefix="z"):
    s = ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"{prefix}_{s}"

def b64_encode_bytes(b: bytes) -> str:
    return base64.b64encode(b).decode('ascii')

# 顶层辅助方法（插入在 random_garbage_comment 后面）
def random_garbage_comment():
    return "/* " + ''.join(random.choices('abcdefghijklmnopqrstuvwxyz0123456789', k=12)) + " */"

# 新增：用于识别并保留 GPT 提示注释
def is_gpt_hint_comment(text: str) -> bool:
    try:
        return ('<<$' in text) and ('$>>' in text)
    except Exception:
        return False

# ---------- AST collection ----------
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
    """Collect names of declarations defined in the given filename's translation unit."""
    if blacklist is None:
        blacklist = set()
    decls = {}
    def visit(node):
        try:
            kind = node.kind
        except Exception:
            return
        # Only consider declarations that are located in the target file
        if kind in DECL_KINDS and node.location and node.location.file and node.location.file.name == filename:
            name = node.spelling
            if name and name not in blacklist:
                decls[name] = decls.get(name, 0) + 1
        for c in node.get_children():
            visit(c)
    visit(tu.cursor)
    return list(decls.keys())

# ---------- text-based rewrite that preserves whitespace/indentation ----------
_identifier_or_string_re = re.compile(
    r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|\b[a-zA-Z_][a-zA-Z0-9_]*\b',
    flags=re.S
)

def replace_identifiers_preserve_strings(text, rename_map):
    """
    Replace identifiers according to rename_map while preserving string literals and their contents.
    This scans the file and only replaces identifier matches that are not inside quoted strings.
    """
    out = []
    last_end = 0
    for m in _identifier_or_string_re.finditer(text):
        start, end = m.span()
        # append intermediate text (whitespace, punctuation, etc.)
        if last_end < start:
            out.append(text[last_end:start])
        token = m.group(0)
        # if token is a quoted string, keep as-is
        if token[0] in ('"', "'"):
            out.append(token)
        else:
            # identifier token
            if token in rename_map:
                out.append(rename_map[token])
            else:
                out.append(token)
        last_end = end
    # append tail
    if last_end < len(text):
        out.append(text[last_end:])
    return ''.join(out)

def split_numeric_constants_in_text(text, probability=0.6):
    """
    Split simple integer constants in non-string parts (preserving strings).
    Uses the same tokenizer approach: operate only on non-quoted regions.
    """
    def repl_in_segment(seg):
        # replace decimal and hex integers but avoid floats (contain '.')
        def split_num(m):
            num = m.group(0)
            try:
                if num.startswith(('0x', '0X')):
                    val = int(num, 16)
                else:
                    if '.' in num:
                        return num
                    val = int(num, 10)
                if abs(val) > 16 and random.random() < probability:
                    a = random.randint(2, max(2, int(abs(val) ** 0.4)))
                    if val % a == 0:
                        return f"({a}*{val//a})"
                    else:
                        b = val - a
                        return f"(({a})+({b}))"
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
            # process non-token segment
            seg = text[last_end:start]
            out.append(repl_in_segment(seg))
        token = m.group(0)
        out.append(token)  # leave tokens (identifiers/strings) unchanged here
        last_end = end
    if last_end < len(text):
        out.append(repl_in_segment(text[last_end:]))
    return ''.join(out)

# ---------- token-based rewrite (reimplemented as text-based for formatting preservation) ----------
def obfuscate_tokens(tu, filename, rename_map, strip_comments=True, encrypt_strings=True, split_constants=True):
    # read original file bytes (preserve encoding robustly)
    with open(filename, 'rb') as f:
        raw = f.read()
    try:
        src_text = raw.decode('utf-8')
    except UnicodeDecodeError:
        try:
            src_text = raw.decode('utf-8', errors='ignore')
        except Exception:
            src_text = raw.decode('latin1', errors='ignore')

    # Quick comment replacement at source-text level to avoid some tokenization pitfalls
    if strip_comments:
        # 替换块注释：提示注释保留，其余替换为垃圾注释（保留换行数）
        def repl_block(m):
            s = m.group(0)
            if is_gpt_hint_comment(s):
                return s
            nl_count = s.count('\n')
            garbage = random_garbage_comment()
            return (garbage + ("\n" * nl_count))
        src_text = re.sub(r'/\*.*?\*/', repl_block, src_text, flags=re.S)

        # 替换行注释：提示注释保留，其余替换为短垃圾注释
        def repl_line(m):
            s = m.group(0)
            if is_gpt_hint_comment(s):
                return s
            return '//' + ''.join(random.choices('abcdefghijklmnopqrstuvwxyz', k=8))
        src_text = re.sub(r'//[^\n]*', repl_line, src_text)

    # If encrypt_strings is True, we WILL NOT inject or output any decryptor/encoder code.
    # To honor "不用输出编码器" we leave string literals unchanged (i.e., do not replace them
    # with calls to a runtime decrypt function). This preserves compilable output and keeps
    # indentation/linebreaks intact.

    # Step 1: replace identifiers according to rename_map while preserving strings
    transformed = replace_identifiers_preserve_strings(src_text, rename_map)

    # Step 2: optionally split numeric constants (again preserve strings)
    if split_constants:
        transformed = split_numeric_constants_in_text(transformed)

    # write result
    return transformed

# ---------- main ----------
def main():
    parser = argparse.ArgumentParser(description="Windows-ready C obfuscator (text-preserving mode; no injected decoder)")
    parser.add_argument('input', help='input C file (single translation unit)')
    parser.add_argument('-o','--output', help='output obfuscated C file', default=None)
    parser.add_argument('--libclang', help='path to libclang.dll (optional)', default=None)
    parser.add_argument('--no-strip-comments', action='store_true', help='do not replace comments')
    parser.add_argument('--no-strings', action='store_true', help='do not encrypt string literals (note: decoder will NOT be injected)')
    parser.add_argument('--no-const-split', action='store_true', help='do not split numeric constants')
    parser.add_argument('--clang-args', help='semicolon-separated additional clang args (e.g. -IC:\\inc;-DDEBUG)', default=None)
    args = parser.parse_args()

    input_file = args.input
    if not os.path.isfile(input_file):
        print("Error: input file not found:", input_file)
        sys.exit(1)

    # set libclang
    ok = set_libclang_path(args.libclang)
    if not ok:
        print("Warning: libclang not set automatically. If you get 'library not found' errors, set --libclang to your libclang.dll path or set CINDEX_LIBRARY_FILE env var.")
    else:
        print("Using libclang from:", args.libclang if args.libclang else DEFAULT_LIBCLANG)

    # DISABLE compatibility check to avoid libclang API mismatch errors (Scheme A)
    try:
        # Some versions of clang.cindex expose this API
        cindex.Config.set_compatibility_check(False)
        print("Note: clang.cindex compatibility check disabled (proceeding despite minor API mismatches).")
    except Exception:
        # If the API isn't available, ignore silently
        pass

    # create index
    try:
        idx = cindex.Index.create()
    except Exception as e:
        print("Error creating clang Index:", e)
        print("If this fails, consider ensuring libclang.dll matches your python clang bindings or use the quick regex fallback.")
        sys.exit(1)

    # prepare clang parse args
    parse_args = []
    if args.clang_args:
        for part in args.clang_args.split(';'):
            p = part.strip()
            if p:
                parse_args.append(p)

    # parse translation unit
    try:
        tu = idx.parse(input_file, args=parse_args)
    except Exception as e:
        print("Error parsing with libclang:", e)
        print("Try adding include paths or pre-processing the file (e.g. clang -E input.c > preprocessed.i).")
        sys.exit(1)

    # conservative blacklist: do not rename main and common libc names
    blacklist = set(['main',])
    # collect declarations
    decls = collect_decls(tu, input_file, blacklist=blacklist)
    rename_map = {}
    for name in decls:
        if not name:
            continue
        new = gen_name("z")
        while new in rename_map.values():
            new = gen_name("z")
        rename_map[name] = new

    print("Collected declarations to rename (count={}):".format(len(decls)))
    if len(decls) > 0:
        print(", ".join(decls[:50]))

    out = obfuscate_tokens(
        tu,
        input_file,
        rename_map,
        strip_comments=not args.no_strip_comments,
        encrypt_strings=not args.no_strings,
        split_constants=not args.no_const_split
    )

    out_file = args.output or (os.path.splitext(input_file)[0] + "_obf.c")
    with open(out_file, 'w', encoding='utf-8', errors='ignore') as f:
        f.write(out)

    print("Wrote obfuscated file to:", out_file)
    if len(rename_map) > 0:
        print("Example rename map (first 40 entries):")
        for k, v in list(rename_map.items())[:40]:
            print(f"  {k} -> {v}")
    else:
        print("No declarations found to rename (maybe the file is empty or uses only external declarations).")

if __name__ == '__main__':
    main()
