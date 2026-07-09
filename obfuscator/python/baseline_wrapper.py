#!/usr/bin/env python3
"""CLI wrapper for baseline.py CodeObfuscator."""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from baseline import CodeObfuscator

if len(sys.argv) != 5 or sys.argv[1] != '-i' or sys.argv[3] != '-o':
    print("Usage: python baseline_wrapper.py -i <input> -o <output>", file=sys.stderr)
    sys.exit(1)

with open(sys.argv[2], 'r', encoding='utf-8') as f:
    code = f.read()

obfuscator = CodeObfuscator()
result = obfuscator.obfuscate(code)

with open(sys.argv[4], 'w', encoding='utf-8') as f:
    f.write(result)
