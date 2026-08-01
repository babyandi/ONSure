#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, pathlib, subprocess, sys, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
AUTHORITY = ROOT / "contracts/validation-case-registry.v1.json"

def fail(code: str) -> None:
    print(f"VALIDATION_CASE_REGISTRY_FAIL {code}", file=sys.stderr)
    raise SystemExit(1)

def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main() -> int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--surefire-dir", action="append", default=[])
    parser.add_argument("--static-only", action="store_true")
    parser.add_argument("--receipt")
    args=parser.parse_args()
    body=json.loads(AUTHORITY.read_text(encoding="utf-8"))
    if body.get("contract")!="ONSURE_VALIDATION_CASE_REGISTRY_V1" or body.get("authority") is not True:
        fail("AUTHORITY_INVALID")
    groups=body.get("case_classes",{})
    if set(groups)!={"positive","negative","adversarial"}:
        fail("CASE_CLASS_SET_INVALID")
    registered=[]
    ids=set()
    for kind, group in groups.items():
        cases=group.get("cases",[])
        if len(cases)<group.get("minimum_registered",0):
            fail(f"{kind.upper()}_DENOMINATOR_UNDERSIZED")
        for case in cases:
            required=("id","oracle","oracle_assertion_id","expected_reason","test_class","test_method")
            if case.get("id") in ids or any(not case.get(field) for field in required):
                fail(f"{kind.upper()}_CASE_INVALID")
            ids.add(case["id"]); registered.append((kind,case))
    if args.static_only:
        print(f"VALIDATION_CASE_REGISTRY_STATIC_PASS registered={len(registered)}")
        return 0
    if not args.surefire_dir:
        fail("SUREFIRE_DIR_REQUIRED")
    suites={}; methods=set()
    for raw in args.surefire_dir:
        directory=pathlib.Path(raw)
        for report in directory.rglob("TEST-*.xml") if directory.exists() else []:
            root=ET.parse(report).getroot()
            name=root.attrib.get("name","")
            values={key:int(root.attrib.get(key,"0")) for key in ("tests","failures","errors","skipped")}
            prior=suites.setdefault(name,{"tests":0,"failures":0,"errors":0,"skipped":0})
            for key,value in values.items(): prior[key]+=value
            for testcase in root.findall(".//testcase"):
                methods.add((testcase.attrib.get("classname", name), testcase.attrib.get("name", "")))
    source_commit=subprocess.run(
        ["git","rev-parse","HEAD"],cwd=ROOT,text=True,capture_output=True,check=True
    ).stdout.strip()
    result={"contract":"ONSURE_VALIDATION_CASE_EXECUTION_RECEIPT_V2","authority":str(AUTHORITY.relative_to(ROOT)),"authority_sha256":sha256(AUTHORITY),"source_commit":source_commit,"registered":len(registered),"case_classes":{},"cases":[],"decision":"PASS_NONFINAL"}
    for kind in ("positive","negative","adversarial"):
        executed=failures=errors=skipped=0
        for _,case in [item for item in registered if item[0]==kind]:
            stats=suites.get(case["test_class"])
            if (case["test_class"], case["test_method"]) not in methods:
                fail(f"REGISTERED_METHOD_NOT_EXECUTED:{case['id']}")
            if not stats or stats["tests"]<case.get("minimum_tests",1):
                fail(f"REGISTERED_CASE_NOT_EXECUTED:{case['id']}")
            executed+=stats["tests"]; failures+=stats["failures"]; errors+=stats["errors"]; skipped+=stats["skipped"]
            result["cases"].append({
                "case_id":case["id"],"case_class":kind,"test_class":case["test_class"],
                "test_method":case["test_method"],"oracle_assertion_id":case["oracle_assertion_id"],
                "expected_reason":case["expected_reason"],"executed":True,"decision":"PASS",
            })
        if executed==0: fail(f"{kind.upper()}_ZERO_EXECUTED")
        if failures or errors or skipped: fail(f"{kind.upper()}_NONCLEAN:{failures}:{errors}:{skipped}")
        result["case_classes"][kind]={"registered":len(groups[kind]["cases"]),"executed_tests":executed,"failures":failures,"errors":errors,"skipped":skipped}
    if args.receipt:
        path=pathlib.Path(args.receipt); path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("VALIDATION_CASE_REGISTRY_RUNTIME_PASS_NONFINAL")
    return 0
if __name__=="__main__": raise SystemExit(main())
