#!/usr/bin/env python3
"""Update a ledger row: ledger.py ID status [backend] [frontend] [e2e] [screenshot] [notes]; '-' keeps a column."""
import sys, pathlib
path = pathlib.Path(__file__).resolve().parent.parent / 'docs' / 'ledger.md'
rid, *vals = sys.argv[1:]
lines = path.read_text().splitlines()
for i, line in enumerate(lines):
    cells = line.split('|')
    if len(cells) > 9 and cells[1].strip() == rid:
        for k, v in enumerate(vals):
            if v != '-':
                cells[3 + k] = f' {v} '
        lines[i] = '|'.join(cells)
        break
else:
    sys.exit(f'no row {rid}')
path.write_text('\n'.join(lines) + '\n')
