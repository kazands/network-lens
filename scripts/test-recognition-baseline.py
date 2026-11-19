#!/usr/bin/env python3
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("baseline", Path(__file__).with_name("assess-recognition.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BaselineTests(unittest.TestCase):
    def row(self, number=1):
        return {"id": f"id-{number}", "hardware": f"hardware-{number}", "group": f"group-{number}",
                "expected": {"manufacturer": "Example", "model": "Model One", "category": "Thermostat"},
                "observed": {"manufacturer": "Example", "model": "Model One", "category": "Thermostat"},
                "stages": {"present": True, "requested": True, "response": True, "parsed": True, "conflict": False}}

    def assess(self, rows):
        return module.assess({"environment": "synthetic", "apkSha256": "0" * 64, "devices": rows})

    def test_each_failure_stage(self):
        changes = [({"present": False, "requested": None, "response": None, "parsed": None}, "not_discovered"),
                   ({"requested": False, "response": None, "parsed": None}, "no_identity_opportunity"),
                   ({"response": False, "parsed": None}, "identity_response_unavailable"),
                   ({"parsed": False}, "identity_not_parsed"),
                   ({"conflict": True}, "conflicting_evidence"),
                   ({}, "identity_selection_or_presentation"),
                   ({"requested": None, "response": None, "parsed": None}, "insufficient_stage_evidence")]
        for changed, stage in changes:
            with self.subTest(stage=stage):
                row = self.row()
                row["observed"]["model"] = ""
                row["stages"].update(changed)
                if not row["stages"]["present"]:
                    row["stages"]["conflict"] = None
                self.assertEqual(self.assess([row])["failureStages"], {stage: 1})

    def test_grouping_and_distinct_hardware_recall(self):
        a, b, c = self.row(1), self.row(2), self.row(3)
        b["group"] = a["group"]
        c["hardware"] = a["hardware"]
        report = self.assess([a, b, c])
        self.assertEqual(report["expectedHardware"], 2)
        self.assertEqual(report["foundHardware"], 2)
        self.assertEqual(report["groupsMergingDistinctHardware"], 1)
        self.assertEqual(report["hardwareSplitAcrossGroups"], 1)
        self.assertEqual(report["identity"]["model_matched"], 3)

    def test_rejects_identifiers_and_inconsistent_evidence(self):
        for value in (None, [], "unexpected"):
            with self.assertRaises(ValueError): module.assess(value)
        a = self.row()
        for key, value in (("ip", "192.0.2.1"), ("serial", "synthetic-private")):
            row = copy.deepcopy(a)
            row[key] = value
            with self.assertRaises(ValueError): self.assess([row])
        with self.assertRaises(ValueError): self.assess([a, a])
        a["stages"]["response"] = False
        with self.assertRaises(ValueError): self.assess([a])

    def test_missing_different_absent_and_unknown_are_separate(self):
        rows = [self.row(n) for n in range(1, 6)]
        rows[1]["observed"]["model"] = " "
        rows[2]["observed"]["model"] = "Another model"
        rows[3]["stages"] = {"present": False, "requested": None, "response": None,
                              "parsed": None, "conflict": None}
        rows[4]["expected"]["model"] = ""
        report = self.assess(rows)
        self.assertEqual(report["identityOutcomes"]["model"],
                         {"matched": 1, "missing": 1, "different": 1,
                          "not_discovered": 1, "unassessed": 1})
        self.assertEqual(report["identity"]["model_expected"], 4)
        self.assertEqual(report["identity"]["model_matched"], 1)
        # Aggregate output never reproduces model strings or per-device labels.
        self.assertNotIn("Another model", str(report))
        self.assertNotIn("hardware-1", str(report))

    def test_case_and_outer_spacing_do_not_create_differences(self):
        row = self.row()
        row["observed"]["model"] = " MODEL ONE "
        self.assertEqual(self.assess([row])["identityOutcomes"]["model"]["matched"], 1)


if __name__ == "__main__":
    unittest.main()
