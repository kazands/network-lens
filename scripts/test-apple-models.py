#!/usr/bin/env python3
"""Synthetic alias ambiguity tests; no network or household records."""
import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location('apple_models', Path(__file__).with_name('generate-apple-models.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class BoardAliases(unittest.TestCase):
    def test_unique_board(self):
        row = {'board': ['J900AP', 'J900aAP'], 'identifier': ['iPad99,1']}
        aliases, excluded = module.select_boards([row], [row], {'iPad99,1'})
        self.assertEqual(aliases, {'J900AP': 'iPad99,1', 'J900aAP': 'iPad99,1'})
        self.assertEqual(excluded, [])

    def test_shared_with_ineligible_owner(self):
        row = {'board': ['J900AP'], 'identifier': ['iPad99,1']}
        rival = {'board': ['J900AP'], 'identifier': ['Internal99,1']}
        aliases, excluded = module.select_boards([row, rival], [row], {'iPad99,1'})
        self.assertEqual(aliases, {})
        self.assertEqual(len(excluded), 1)

    def test_shared_product_types(self):
        row = {'board': ['J900AP'], 'identifier': ['iPad99,1', 'iPad99,2']}
        aliases, excluded = module.select_boards([row], [row], set(row['identifier']))
        self.assertEqual(aliases, {})
        self.assertEqual(len(excluded), 1)

    def test_ineligible_and_unknown_targets(self):
        row = {'board': ['J900AP'], 'identifier': ['iPad99,1']}
        self.assertEqual(module.select_boards([row], [], {'iPad99,1'}), ({}, []))
        with self.assertRaises(AssertionError):
            module.select_boards([row], [row], set())

if __name__ == '__main__':
    unittest.main()
