#!/usr/bin/env python3
"""Update a ledger row: ledger.py ID status [backend] [frontend] [e2e] [screenshot] [notes]; '-' keeps a column.
Add a deliverable row (after the last deliverable row, idempotent): ledger.py --add ID "deliverable text"."""
import sys, pathlib
path = pathlib.Path(__file__).resolve().parent.parent / 'docs' / 'ledger.md'
lines = path.read_text().splitlines()
if sys.argv[1:2] == ['--add']:
    rid, text = sys.argv[2], sys.argv[3]
    if any(len(l.split('|')) > 9 and l.split('|')[1].strip() == rid for l in lines):
        sys.exit(0)
    start = next(i for i, l in enumerate(lines) if l.strip() == '## Deliverables')
    last = max(i for i, l in enumerate(lines) if i > start and l.startswith('| D'))
    lines.insert(last + 1, f'| {rid} | {text} | todo | | | | | |')
    path.write_text('\n'.join(lines) + '\n')
    sys.exit(0)
rid, *vals = sys.argv[1:]
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
