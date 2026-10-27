#!/usr/bin/env python3
"""Read-only XLSB workbook inventory for migration planning.

Requires: pip install pyxlsb
Never writes to or modifies the source workbook.
Usage: python inspect_xlsb.py input.xlsb [--json inventory.json] [--csv inventory.csv]
"""
import argparse
import csv
import json
from pathlib import Path
from pyxlsb import open_workbook

KEY_SHEETS = {
    "F": [3, 4, 5],
    "S": [3, 4, 5],
    "namesstu": [4, 5],
    "PR_sec": [3, 4, 5],
    "PRsec": [1, 2, 3, 4],
    "final_10": [2, 3, 4, 5, 6, 7],
    "final_11C": [2, 3, 4, 5, 6, 7, 8],
}

def cell_value(value):
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)

def inspect(path: Path):
    inventory = []
    samples = {}
    with open_workbook(str(path)) as wb:
        # Focus on migration-relevant sheets; scanning every report/template sheet can be slow.
        for sheet_name in [name for name in KEY_SHEETS if name in wb.sheets]:
            max_row = max_col = nonempty = 0
            sample_rows = []
            wanted = set(KEY_SHEETS.get(sheet_name, []))
            with wb.get_sheet(sheet_name) as sheet:
                for row in sheet.rows(sparse=True):
                    present = [(cell.c + 1, cell_value(cell.v)) for cell in row
                               if cell.v is not None and str(cell.v).strip() != ""]
                    if not present:
                        continue
                    row_num = max(cell.r for cell in row) + 1
                    nonempty += len(present)
                    max_row = max(max_row, row_num)
                    max_col = max(max_col, max(c for c, _ in present))
                    if row_num in wanted:
                        sample_rows.append({"row": row_num, "cells": [
                            {"column": col, "value": value} for col, value in present
                        ]})
            inventory.append({"sheet": sheet_name, "last_observed_nonempty_row": max_row,
                              "last_observed_nonempty_column": max_col,
                              "nonempty_cells_observed": nonempty})
            if sample_rows:
                samples[sheet_name] = sample_rows
    return {"source_file": path.name, "read_only": True,
            "sheet_count": len(inventory), "scope": "migration-relevant sheets only", "sheets": inventory,
            "key_sheet_sample_rows": samples}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workbook", type=Path)
    parser.add_argument("--json", type=Path, default=Path("xlsb_inventory.json"))
    parser.add_argument("--csv", type=Path, default=Path("xlsb_inventory.csv"))
    args = parser.parse_args()
    if not args.workbook.is_file():
        parser.error(f"Workbook not found: {args.workbook}")
    result = inspect(args.workbook)
    args.json.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    with args.csv.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=["sheet", "last_observed_nonempty_row",
                                               "last_observed_nonempty_column", "nonempty_cells_observed"])
        writer.writeheader()
        writer.writerows(result["sheets"])
    print(f"Read-only inspection complete: {result['sheet_count']} sheets")
    print(f"JSON: {args.json.resolve()}")
    print(f"CSV:  {args.csv.resolve()}")

if __name__ == "__main__":
    main()
