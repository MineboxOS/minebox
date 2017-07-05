#!/usr/bin/bash

# Script for launching Minebox UI Gateway.
cd /usr/lib/minebox
source mbvenv/bin/activate
PYTHONPATH="/usr/lib/minebox/mbvenv/" /root/minebox-client-tools_vm/sia/demosiad.py
