#!/usr/bin/env python3
"""Assess an explicitly supplied, redacted compatibility baseline; never contacts a network."""
import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path


def assess(data):
    if not isinstance(data, dict) or set(data) != {"environment", "apkSha256", "devices"}:
        raise ValueError("Use only environment, apkSha256 and devices; omit addresses and personal identifiers")
    if data["environment"] not in ("synthetic", "physical"):
        raise ValueError("Label the actual environment")
    if not isinstance(data["apkSha256"], str) or not re.fullmatch(r"[a-f0-9]{64}", data["apkSha256"]):
        raise ValueError("Record the exact APK hash")
    rows = data["devices"]
    if not isinstance(rows, list) or not 1 <= len(rows) <= 4096:
        raise ValueError("Supply 1 to 4096 redacted observations")
    failures = Counter()
    metrics = Counter()
    outcomes = {field: Counter({key: 0 for key in
                ("matched", "missing", "different", "not_discovered", "unassessed")})
                for field in ("manufacturer", "model", "category")}
    expected_hardware, found_hardware, ids = set(), set(), set()
    groups, hardware_groups = defaultdict(set), defaultdict(set)
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"id", "hardware", "group", "expected", "observed", "stages"}:
            raise ValueError("Unexpected device fields; do not include IP, MAC, SSID, serial or personal name")
        for key in ("id", "hardware", "group"):
            if not isinstance(row[key], str) or not re.fullmatch(key + r"-[0-9]{1,6}", row[key]):
                raise ValueError("Use unrelated numbered labels such as id-1, hardware-1 and group-1")
        if row["id"] in ids:
            raise ValueError("Observation IDs must be unique")
        ids.add(row["id"])
        for key in ("expected", "observed"):
            if not isinstance(row[key], dict) or set(row[key]) != {"manufacturer", "model", "category"}:
                raise ValueError("Identity contains only public manufacturer, model and category")
            if any(not isinstance(v, str) or len(v) > 160 or any(ord(c) < 32 for c in v) for v in row[key].values()):
                raise ValueError("Identity values must be bounded plain text")
        stages = row["stages"]
        if not isinstance(stages, dict) or set(stages) != {"present", "requested", "response", "parsed", "conflict"}:
            raise ValueError("Record all five stages; use null for unknown evidence")
        if any(v is not None and type(v) is not bool for v in stages.values()):
            raise ValueError("Stages must be true, false or null")
        if type(stages["present"]) is not bool:
            raise ValueError("Presence must be observed, not guessed")
        if stages["parsed"] is True and stages["response"] is not True:
            raise ValueError("Parsed identity requires a confirmed identity response")
        if not stages["present"] and any(stages[k] is True for k in ("requested", "response", "parsed", "conflict")):
            raise ValueError("Absent devices cannot have current identity observations")
        expected_hardware.add(row["hardware"])
        if stages["present"]:
            found_hardware.add(row["hardware"])
            groups[row["group"]].add(row["hardware"])
            hardware_groups[row["hardware"]].add(row["group"])
        mismatched = False
        for field in ("manufacturer", "model", "category"):
            expected = row["expected"][field].strip().casefold()
            observed = row["observed"][field].strip().casefold()
            if expected:
                metrics[field + "_expected"] += 1
                match = stages["present"] and observed == expected
                metrics[field + "_matched"] += bool(match)
                mismatched |= not match
                outcome = ("not_discovered" if not stages["present"] else
                           "missing" if not observed else "matched" if match else "different")
                outcomes[field][outcome] += 1
            else:
                outcomes[field]["unassessed"] += 1
        if not stages["present"]:
            failures["not_discovered"] += 1
        elif mismatched:
            if stages["conflict"] is True:
                stage = "conflicting_evidence"
            elif stages["requested"] is False and stages["response"] is not True:
                stage = "no_identity_opportunity"
            elif stages["requested"] is True and stages["response"] is False:
                stage = "identity_response_unavailable"
            elif stages["response"] is True and stages["parsed"] is False:
                stage = "identity_not_parsed"
            elif stages["parsed"] is True:
                stage = "identity_selection_or_presentation"
            else:
                stage = "insufficient_stage_evidence"
            failures[stage] += 1
    return {"environment": data["environment"], "apkSha256": data["apkSha256"],
            "observations": len(rows), "expectedHardware": len(expected_hardware),
            "foundHardware": len(found_hardware), "identity": dict(metrics), "failureStages": dict(failures),
            "identityOutcomes": {field: dict(counts) for field, counts in outcomes.items()},
            "groupsMergingDistinctHardware": sum(len(v) > 1 for v in groups.values()),
            "hardwareSplitAcrossGroups": sum(len(v) > 1 for v in hardware_groups.values()),
            "limits": "Applies only to the supplied baseline. Unknown stages remain unknown; this is not universal accuracy. Different values require review for aliases or incorrect identification; they are not automatically false positives."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    # Household input and reports belong only under ignored local evidence directories.
    root = Path(__file__).resolve().parent.parent
    target = args.output.resolve()
    if not any(target.is_relative_to(root / folder) for folder in ("artifacts", ".tools", "evidence")):
        parser.error("Write reports only under artifacts/, .tools/ or evidence/")
    if args.input == Path("-"):
        raw = sys.stdin.buffer.read(1_048_577)
    else:
        with args.input.open("rb") as stream:
            raw = stream.read(1_048_577)
    if len(raw) > 1_048_576:
        parser.error("Baseline exceeds 1 MiB")
    try:
        data = json.loads(raw)
        if isinstance(data, dict) and data.get("environment") == "physical" and args.input != Path("-"):
            parser.error("Physical records must use --input - and stay in memory; only aggregate reports are saved")
        result = assess(data)
    except (ValueError, TypeError, KeyError) as error:
        parser.error(str(error))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2) + "\n")
    print("Assessed " + str(result["observations"]) + " " + result["environment"] + " observations; aggregate report written")


if __name__ == "__main__":
    main()
