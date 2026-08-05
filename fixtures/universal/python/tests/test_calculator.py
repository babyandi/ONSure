import unittest

from calculator import divide


class CalculatorTest(unittest.TestCase):
    def test_normal_path(self):
        self.assertEqual(3, divide(6, 2))

    def test_failure_path(self):
        with self.assertRaisesRegex(ValueError, "division by zero"):
            divide(1, 0)

    def test_retry_is_deterministic(self):
        self.assertEqual(divide(8, 2), divide(8, 2))

    def test_blocking_rejects_invalid_input(self):
        with self.assertRaises(ValueError):
            divide(1, 0)
