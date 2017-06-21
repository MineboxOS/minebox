#!/usr/bin/bash

# Script for launching Minebox backup service.
cd /usr/lib/minebox
source mug/bin/activate
mug/backup-service.py
