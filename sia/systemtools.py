# Tools for accessing system and machine functionality.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from glob import glob
import json
import os
import re
import time
from datetime import datetime
import subprocess
import socket

from connecttools import (post_to_adminservice)

MACHINE_AUTH_FILE = "/etc/minebox/machine_auth.json"
BOX_SETTINGS_JSON_PATH="/etc/minebox/minebox_settings.json"
ROCKSTOR_PATH="/opt/rockstor"
DMIDECODE = "/usr/sbin/dmidecode"
HDPARM = "/usr/sbin/hdparm"
HOSTNAME_TO_CONNECT = "minebox.io"
BTRFS="/usr/sbin/btrfs"
DF = "/usr/bin/df"
OLD_LOGFILES_MASK = "/var/log/*-*"
YUM = "/usr/bin/yum"
YUMCOMPTRANS = "/usr/sbin/yum-complete-transaction"
OWN_PACKAGES_LIST = ["minebox*", "MineBD"]
SWAPON = "/usr/sbin/swapon"
SWAPOFF = "/usr/sbin/swapoff"
SED = "/usr/bin/sed"
FSTAB = "/etc/fstab"
FINDMNT = "/usr/bin/findmnt"

def register_machine():
    machine_info = get_machine_info()

    # Second parameter is False because we do not want/need to use a token here.
    admindata, admin_status_code = post_to_adminservice("registerMachine", False,
      {
        "uuid": machine_info["system_uuid"],
        "serialNumber": machine_info["system_serial"],
        "model": machine_info["system_sku"],
        "peripherals": {"disks": machine_info["disks"]},
      }
    )
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
        {"uuid": machine_info["system_uuid"]})
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
      "system_serial": subprocess
                       .check_output([DMIDECODE, "-s", "system-serial-number"])
                       .strip(),
      "system_sku": None,
    }
    outlines = subprocess.check_output([DMIDECODE, "-t", "system"]).splitlines()
    for line in outlines:
        matches = re.match(r"^\s+SKU Number:\s+(.+)$", line)
        if matches:
            machine_info["system_sku"] = matches.group(1).strip()  # 1st parenthesis expression
    machine_info["disks"] = []
    for devfile in glob("/dev/disk/by-id/ata-*"):
        if re.search(r'\-part\d+$', devfile):
            continue
        try:
          outlines = subprocess.check_output([HDPARM, "-i", devfile]).splitlines()
          for line in outlines:
              matches = re.match(
                r"^\s*Model=([^,]+), FwRev=([^,]+), SerialNo=(.+)$",
                line)
              if matches:
                  machine_info["disks"].append({
                    "model": matches.group(1).strip(),
                    "firmware_rev": matches.group(2).strip(),
                    "serial_number": matches.group(3).strip(),
                  })
        except subprocess.CalledProcessError:
            # We just ignore if hdparm is unsuccessful.
            pass

    return machine_info

def is_rockstor_system():
    # Test for a file included in every Rockstor package for sure.
    return os.path.isdir(os.path.join(ROCKSTOR_PATH, "conf", "rockstor.service"))

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

def get_box_settings():
    settings = {}
    try:
        if os.path.isfile(BOX_SETTINGS_JSON_PATH):
            with open(BOX_SETTINGS_JSON_PATH) as json_file:
                settings = json.load(json_file)
    except:
        # If anything fails here, we'll just deliver the defaults set below.
        current_app.logger.warn("Settings file could not be read: %s"
                                % BOX_SETTINGS_JSON_PATH)
    # Set default values.
    if not "sia_upload_limit_kbps" in settings:
        settings["sia_upload_limit_kbps"] = 0
    if not "display_currency" in settings:
        settings["display_currency"] = "USD"
    if not "last_maintenance" in settings:
        settings["last_maintenance"] = 0
    return settings

def write_box_settings(settings):
    try:
        with open(BOX_SETTINGS_JSON_PATH, 'w') as outfile:
            json.dump(settings, outfile)
    except:
        return False, ("Error writing settings to file: %s"
                       % BOX_SETTINGS_JSON_PATH)
    return True, ""

def system_maintenance():
    # See if it's time to run some system maintenance and do so if required.
    settings = get_box_settings()
    timenow = int(time.time())
    if settings["last_maintenance"] < timenow - 24 * 3600:
        current_app.logger.info("Run system maintenance.")
        # Store the fact that we're running a maintenance.
        settings["last_maintenance"] = timenow
        success, errmsg = write_box_settings(settings)
        if not success:
            return False, errmsg
        # *** Logfile disk congestion ***
        # Check if old logs are taking up a large part of the root filesystem
        # and clean them if necessary.
        # Right now, delete them if they take up more space than is free.
        root_space = get_mountpoint_size("/")
        if "free" in root_space:
            if root_space["free"] < get_filemask_size(OLD_LOGFILES_MASK):
                current_app.logger.info("Root free space too low, cleaning up logs.")
                for filepath in glob(OLD_LOGFILES_MASK):
                    os.remove()
        # *** Kernels filling up /boot ***
        if is_rockstor_system():
            # One-off for Kernel 4.8.7, which was obsoleted by the time most
            # Minebox devices shipped. If a different kernel than that is running,
            # remove this old one to make /boot not overflow.
            retcode = subprocess.call([YUM, "info", "kernel-ml-4.8.7"])
            if retcode == 0 and not os.uname()[2].startswith("4.8.7-"):
                current_app.logger.info("Kernel 4.8.7 is installed but not running, removing it.")
                retcode = subprocess.call([YUM, "remove", "-y", "kernel-ml-4.8.7"])
                if retcode != 0:
                    current_app.logger.warn("Removing old kernel failed, return code: %s" % retcode)
            # Also, Kernel 4.12.4 never booted correctly on Gen10 boxes,
            # so remove it as well (still ensure it's not running though).
            retcode = subprocess.call([YUM, "info", "kernel-ml-4.12.4"])
            if retcode == 0 and not os.uname()[2].startswith("4.12.4-"):
                current_app.logger.info("Kernel 4.12.4 is installed but not running, removing it.")
                retcode = subprocess.call([YUM, "remove", "-y", "kernel-ml-4.12.4"])
                if retcode != 0:
                    current_app.logger.warn("Removing old kernel failed, return code: %s" % retcode)
        # *** Swap wearing down USB flash ***
        if is_rockstor_system():
            # Find if swap on USB stick is activated and deactivate it.
            # This may be reasonable on non-Rockstor systems but was added as a
            # fix to an issue with the original Minebox systems and also runs a
            # decent amount of commands, so let's avoid it outside of Rockstor.
            swapdevs = []
            usbswapuuids = []
            outlines = subprocess.check_output([SWAPON, "--show=NAME"]).splitlines()
            for line in outlines:
                matches = re.match(r"^/dev/(.+)$", line)
                if matches:
                    swapdevs.append(matches.group(1).strip())
            # Now see if any of those swap devices are on USB and get the UUIDs.
            for dev in swapdevs:
                for usbdevpath in glob("/dev/disk/by-id/usb-*"):
                    if os.readlink(usbdevpath).endswith(dev):
                        for uuidpath in glob("/dev/disk/by-uuid/*"):
                            if os.readlink(uuidpath).endswith(dev):
                                usbswapuuids.append(os.path.basename(uuidpath))
            # Now actually disable those UUIDs directly and in fstab.
            for swapuuid in usbswapuuids:
                current_app.logger.info("Disable USB swap with UUID %s.", swapuuid)
                subprocess.call([SED, "-i", "-e", "s/^UUID=%s/#&/" % swapuuid, FSTAB])
                subprocess.call([SWAPOFF, "-U", swapuuid])
            # *** Harddisks without swap partitions ***
            lowerparts = []
            for i in range(1, 2):
                outlines = subprocess.check_output([FINDMNT, "-P", "/mnt/lower%s" % i]).splitlines()
                for line in outlines:
                    matches = re.match(r'SOURCE="/dev/(.+)"', line)
                    if matches:
                        lowerparts.append(matches.group(1).strip())
            for part in lowerparts:
                disk = part[0:-1]
                if not disk in [dev[0:-1] for dev in swapdevs]:
                    current_app.logger.info("%s needs a swap partition." % disk)
                else:
                    current_app.logger.info("%s has a swap partition." % disk)
        # *** Updates on own packages ***
        # Check for updates to our own packages and install those if needed.
        # Note: this call may end up restarting our own process!
        # Therefore, this function shouldn't do anything important after this.
        current_app.logger.info("See if any yum transactions are pending.")
        subprocess.call([COMPTRANS, "-y"])
        current_app.logger.info("Trying to update our own packages.")
        retcode = subprocess.call([YUM, "upgrade", "-y"] + OWN_PACKAGES_LIST)
        if retcode != 0:
            return False, ("Updating failed, return code: %s" % retcode)
    return True, ""

def get_mountpoint_size(mountpath):
    # Get total, used and free sizes of a mount point.
    #df --block-size=1 --output=target,fstype,size,used,avail /
    spaceinfo = {}
    # See https://btrfs.wiki.kernel.org/index.php/FAQ#Understanding_free_space.2C_using_the_new_tools
    dfcommand = [DF, "--block-size=1", "--output=target,fstype,size,used,avail", mountpath]
    outlines = subprocess.check_output(dfcommand).splitlines()
    for line in outlines:
        matches = re.match(r"^\/[^\s]*\s+([^\s]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)$", line)
        if matches:
            spaceinfo["filesystem"] = matches.group(1)
            spaceinfo["total"] = int(matches.group(2))
            spaceinfo["used"] = int(matches.group(3))
            spaceinfo["free"] = int(matches.group(4))

    return spaceinfo

def get_filemask_size(filemask):
    # Get the summmary size of all files in the given "glob" mask.
    sumsize = 0
    for filepath in glob(filemask):
        fileinfo = os.stat(filepath)
        sumsize += fileinfo.st_size
    return sumsize

def get_btrfs_subvolumes(diskpath):
    subvols = []
    outlines = subprocess.check_output([BTRFS, 'subvolume', 'list', '-q', '-u', diskpath]).splitlines()
    for line in outlines:
        # Use .search as .match only matches start of the string.
        matches = re.search(r"parent_uuid ([0-9a-f\-]+) uuid ([0-9a-f\-]+) path (.+)$", line)
        if matches:
            subvols.append({
                "path": matches.group(3),
                "uuid": matches.group(2),
                "parent_uuid": matches.group(1),
            })

    return subvols

def get_btrfs_snapshots(diskpath):
    parents = dict((k["uuid"],k["path"]) for k in get_btrfs_subvolumes(diskpath))
    snaps = []
    outlines = subprocess.check_output([BTRFS, 'subvolume', 'list', '-s', '-q', '-u', diskpath]).splitlines()
    for line in outlines:
        # Use .search as .match only matches start of the string.
        matches = re.search(r"otime ([0-9\-]+ [0-9:]+) parent_uuid ([0-9a-f\-]+) uuid ([0-9a-f\-]+) path (.+)$", line)
        if matches:
            snaps.append({
                "path": matches.group(4),
                "uuid": matches.group(3),
                "parent_uuid": matches.group(2),
                "parent_path": parents[matches.group(2)],
                "creation_time": datetime.strptime(matches.group(1), "%Y-%m-%d %H:%M:%S"),
            })

    return snaps

def create_btrfs_subvolume(diskpath):
    subprocess.call([BTRFS, 'subvolume', 'create', diskpath])

def delete_btrfs_subvolume(diskpath):
    subprocess.call([BTRFS, 'subvolume', 'delete', diskpath])

def resize_btrfs_volume(size, diskpath):
    retcode = subprocess.call([BTRFS, 'filesystem', 'resize', size, diskpath])
    if retcode > 0:
        current_app.logger.warn("btrfs resize failed with return code %s!" % retcode)
        return False
    return True

def get_btrfs_space(diskpath):
    spaceinfo = {}
    # See https://btrfs.wiki.kernel.org/index.php/FAQ#Understanding_free_space.2C_using_the_new_tools
    outlines = subprocess.check_output([BTRFS, 'filesystem', 'usage', '--raw', diskpath]).splitlines()
    for line in outlines:
        matches = re.match(r"^\s+Device size:\s+([0-9]+)$", line)
        if matches:
            spaceinfo["total"] = int(matches.group(1))

        matches = re.match(r"^\s+Device allocated:\s+([0-9]+)$", line)
        if matches:
            spaceinfo["allocated"] = int(matches.group(1))

        matches = re.match(r"^\s+Used:\s+([0-9]+)$", line)
        if matches:
            spaceinfo["used"] = int(matches.group(1))

        matches = re.match(r"^\s+Free \(estimated\):\s+([0-9]+)\s+\(min: ([0-9]+)\)$", line)
        if matches:
            spaceinfo["free_est"] = int(matches.group(1))
            spaceinfo["free_min"] = int(matches.group(2))

    return spaceinfo

def get_device_size(devpath):
    devsize = int(subprocess.check_output(['/usr/sbin/blockdev', '--getsize64', devpath]))
    return devsize
