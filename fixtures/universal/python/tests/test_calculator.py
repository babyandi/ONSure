import unittest

from calculator import divide


class CalculatorTest(unittest.TestCase):
    def test_normal_path(self):
        self.assertEqual(3, divide(6, 2))

    def test_failure_path(self):
        with self.assertRaisesRegex(ValueError, "division by zero"):
            divide(1, 0)
