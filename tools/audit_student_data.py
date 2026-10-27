#!/usr/bin/env python3
"""Read-only audit of whether the XLSB contains populated student identity fields.
Requires pyxlsb. Does not alter the workbook or evaluate formulas/VBA.
"""
import argparse, json
from pathlib import Path
from pyxlsb import open_workbook

# One-based columns. Identity fields are based on the visible headers inspected in the workbook.
SPECS = {
    "F": {"ranges": [(6,205),(207,396),(406,605),(607,796),(806,1005),(1007,1196)], "seat_col": 2, "name_col": 3, "header_row": 5},
    "S": {"ranges": [(6,396),(5006,5396),(5406,5796)], "seat_col": 2, "name_col": 3, "header_row": 5},
    "namesstu": {"ranges": [(6,200),(201,418)], "groups": [(3,4),(39,40),(55,56)], "header_row": 5},
}

def present(v):
    return v is not None and str(v).strip() != ""

def audit(path):
    out={"source_file":path.name,"read_only":True,"limitation":"pyxlsb reads cached cell values only; formulas and VBA are not evaluated.","sheets":[]}
    with open_workbook(str(path)) as wb:
        for name,spec in SPECS.items():
            if name not in wb.sheets: continue
            vals={}
            with wb.get_sheet(name) as sh:
                for row in sh.rows(sparse=True):
                    rn=max((c.r for c in row),default=-1)+1
                    if name=="namesstu":
                        if not any(a<=rn<=b for a,b in spec["ranges"]): continue
                    elif not any(a<=rn<=b for a,b in spec["ranges"]): continue
                    vals[rn]={c.c+1:c.v for c in row if present(c.v)}
            if name=="namesstu":
                groups=[]
                for seat_col,name_col in spec["groups"]:
                    rows=[(r,d) for r,d in vals.items() if r>spec["header_row"] and (present(d.get(seat_col)) or present(d.get(name_col)))]
                    groups.append({"seat_column":seat_col,"name_column":name_col,"rows_with_identity_value":len(rows),"sample":[{"row":r,"seat":d.get(seat_col),"name":d.get(name_col)} for r,d in rows[:10]]})
                out["sheets"].append({"sheet":name,"identity_groups":groups})
            else:
                rows=[(r,d) for r,d in vals.items() if r>spec["header_row"] and (present(d.get(spec["seat_col"])) or present(d.get(spec["name_col"]))) ]
                out["sheets"].append({"sheet":name,"candidate_ranges":spec["ranges"],"seat_column":spec["seat_col"],"name_column":spec["name_col"],"rows_with_identity_value":len(rows),"sample":[{"row":r,"seat":d.get(spec["seat_col"]),"name":d.get(spec["name_col"])} for r,d in rows[:15]]})
    return out

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument("workbook",type=Path)
    ap.add_argument("--json",type=Path,default=Path("student_data_audit.json"))
    args=ap.parse_args()
    if not args.workbook.is_file(): ap.error(f"Workbook not found: {args.workbook}")
    result=audit(args.workbook)
    args.json.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"Audit complete: {len(result['sheets'])} sheets; JSON: {args.json.resolve()}")
    for s in result['sheets']:
        if 'rows_with_identity_value' in s: print(f"{s['sheet']}: {s['rows_with_identity_value']} rows with seat/name values")
        else: print(s['sheet']+': '+', '.join(f"group {g['seat_column']}/{g['name_column']}={g['rows_with_identity_value']}" for g in s['identity_groups']))
if __name__=='__main__': main()
