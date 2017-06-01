#!/usr/bin/bash

# Script for launching Minebox UI Gateway.
cd /usr/lib/minebox
source mug/bin/activate
PYTHONPATH="/usr/lib/minebox/mug/" /root/minebox-client-tools_vm/sia/demosiad.py
