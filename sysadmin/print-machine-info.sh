#!/usr/bin/bash

# Script for printing Minebox machine info.
cd /usr/lib/minebox
source mbvenv/bin/activate
mbvenv/print-machine-info.py
