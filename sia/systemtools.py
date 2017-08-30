# Tools for accessing system and machine functionality.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import json
import re
import subprocess

from connecttools import (post_to_adminservice)

MACHINE_AUTH_FILE = "/etc/minebox/machine_auth.json"
DMIDECODE = "/usr/sbin/dmidecode"

def submit_machine_auth():
    machine_info = get_machine_info()

    admindata, admin_status_code = post_to_adminservice("authMachine",
        {"uuid": machine_info["system_uuid"],
         "serialNumber": machine_info["chassis_serial"],
         "model": machine_info["system_sku"]})
    if admin_status_code >= 400:
        return False, "ERROR: admin error %s: %s" % (admin_status_code, admindata["message"])

    with open(MACHINE_AUTH_FILE, 'w') as outfile:
        json.dump(machine_info, outfile)
    return True, ""

def get_machine_info():
    machine_info = {
      "system_uuid": subprocess.check_output([DMIDECODE, "-s", "system-uuid"]),
      "chassis_serial": subprocess.check_output([DMIDECODE, "-s", "chassis-serial-number"]),
      "system_sku": None,
    }
    outlines = subprocess.check_output([DMIDECODE, "-t", "system"]).splitlines()
    for line in outlines:
        matches = re.match(r"^\s+SKU Number:\s+(\S+)\s*$", line)
        if matches:
            machine_info["system_sku"] = matches.group(1)  # 1st parenthesis expression
    return machine_info
