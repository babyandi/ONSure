#!/bin/bash
python3 -m unittest discover -s tests -p 'test_*.py' 2>&1 | tail -3
