# Tools for accessing system and machine functionality.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
import json
import re
import subprocess
import socket

from connecttools import (post_to_adminservice)

MACHINE_AUTH_FILE = "/etc/minebox/machine_auth.json"
DMIDECODE = "/usr/sbin/dmidecode"
HOSTNAME_TO_CONNECT = "minebox.io"

def register_machine():
    machine_info = get_machine_info()

    # Second parameter is False because we do not want/need to use a token here.
    admindata, admin_status_code = post_to_adminservice("registerMachine", False,
        {"uuid": machine_info["system_uuid"],
         "serialNumber": machine_info["chassis_serial"],
         "model": machine_info["system_sku"]})
    if admin_status_code >= 400:
        return False, ("ERROR: admin error %s: %s" %
                       (admin_status_code, admindata["message"]))
    return True, ""

def submit_ip_notification():
    machine_info = get_machine_info()
    ipaddress = get_local_ipaddress()
    if not ipaddress:
        return False, ("ERROR: No IP address found.")

    admindata, admin_status_code = post_to_adminservice("ipNotification", False,
        {"uuid": machine_info["system_uuid"],
         "localIp": ipaddress})
    if admin_status_code >= 400:
        return False, ("ERROR: admin error %s: %s" %
                       (admin_status_code, admindata["message"]))

    return True, ""

def submit_machine_auth():
    machine_info = get_machine_info()
    ipaddress = get_local_ipaddress()
    if not ipaddress:
        return False, ("ERROR: No IP address found.")

    # Second parameter is True because token is required with this one.
    admindata, admin_status_code = post_to_adminservice("authMachine", True,
        {"uuid": machine_info["system_uuid"],
         "serialNumber": machine_info["chassis_serial"],
         "model": machine_info["system_sku"],
         "localIp": ipaddress})
    if admin_status_code >= 400:
        return False, ("ERROR: admin error %s: %s" %
                       (admin_status_code, admindata["message"]))

    with open(MACHINE_AUTH_FILE, 'w') as outfile:
        json.dump(machine_info, outfile)
    return True, ""

def get_machine_info():
    machine_info = {
      "system_uuid": subprocess
                     .check_output([DMIDECODE, "-s", "system-uuid"])
                     .strip(),
      "chassis_serial": subprocess
                        .check_output([DMIDECODE, "-s", "chassis-serial-number"])
                        .strip(),
      "system_sku": None,
    }
    outlines = subprocess.check_output([DMIDECODE, "-t", "system"]).splitlines()
    for line in outlines:
        matches = re.match(r"^\s+SKU Number:\s+(.+)$", line)
        if matches:
            machine_info["system_sku"] = matches.group(1).strip()  # 1st parenthesis expression
    return machine_info

def get_local_ipaddress():
    # By opening up a socket to the outside and look at our side, we get our
    # IP address on the local network.
    # If we'd just do socket.gethostbyname(socket.gethostname()) we'd get
    # 127.0.0.1, which isn't too helpful.
    # Note that we need a working Internet connection for this function
    # to work correctly - but we want to submit this to the auth service,
    # which is out there anyhow.
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect((HOSTNAME_TO_CONNECT, 80))
        ipaddress = sock.getsockname()[0]
        sock.close()
    except: # On *ANY* exception, we error out.
        return False
    return ipaddress
