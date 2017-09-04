#!/usr/bin/bash

# Script for registering Minebox machine.
cd /usr/lib/minebox
source mbvenv/bin/activate
mbvenv/register-machine.py
