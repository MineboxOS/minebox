# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from glob import glob
import subprocess
import os
import re
import time

from connecttools import (get_from_sia, post_to_sia, get_from_minebd,
                          get_from_mineboxconfig, get_from_faucetservice)

H_PER_SC=1e24 # hastings per siacoin ("siacoinprecision" in /daemon/constants)
SEC_PER_BLOCK=600 # seconds per block ("blockfrequency" in /daemon/constants)
SIA_CONFIG_JSON="/etc/minebox/sia_config.json"
SIA_DIR="/mnt/lower1/sia"
HOST_DIR_BASE_MASK="/mnt/lower*"
HOST_DIR_NAME="hostedfiles"
MINEBD_STORAGE_PATH="/mnt/storage"
MINEBD_DEVICE_PATH="/dev/nbd0"
UPPER_SPACE_MIN=50*(2**20)
UPPER_SPACE_TARGET=100*(2**20)

def check_sia_sync():
    # Check if sia is running and in sync before doing other sia actions.
    consdata, cons_status_code = get_from_sia('consensus')
    if cons_status_code == 200:
        if not consdata["synced"]:
          return False, "ERROR: sia seems not to be synced. Please try again when the consensus is synced."
    else:
        return False, "ERROR: sia daemon needs to be running before you can work with it."
    return True, ""

def get_seed():
    current_app.logger.info("Fetch seed from MineBD.")
    # Get wallet seed from MineBD.
    seed = None
    mbdata, mb_status_code = get_from_minebd('siawalletseed')
    if mb_status_code == 200:
        seed = mbdata["message"]
    return seed

def init_wallet(seed):
    current_app.logger.info("Initialize sia wallet.")
    siadata, sia_status_code = post_to_sia('wallet/init/seed',
                                           {"encryptionpassword": seed,
                                            "seed": seed})
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Typically, we get a 204 status code here.
    return True

def unlock_wallet(seed):
    current_app.logger.info("Unlocking sia wallet.")
    siadata, sia_status_code = post_to_sia('wallet/unlock',
                                           {"encryptionpassword": seed})
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Typically, we get a 204 status code here.
    return True

def fetch_siacoins():
    current_app.logger.info("TODO: Fetching base allotment of coins from Minebox.")
    # We need the Minebox sia faucet service for this (See MIN-130).
    fsdata, fs_status_code = get_from_faucetservice('siacoins')
        current_app.logger.error("Faucet error %s: %s" % (fs_status_code,
                                                          fsdata["message"]))
        return False
    return True

def set_allowance():
    current_app.logger.info("Setting an allowance for renting out files.")
    settings = get_sia_config()
    siadata, sia_status_code = post_to_sia('renter',
                                           {"funds": settings["renter"]["allowance_funds"],
                                            "period": settings["renter"]["allowance_period"]})
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Typically, we get a 204 status code here.
    return True

def set_up_hosting():
    # See https://blog.sia.tech/how-to-run-a-host-on-sia-2159ebc4725 for a
    # blog post explaining all steps to do.
    settings = get_sia_config()
    if not settings["minebox_sharing"]["enabled"]:
        return
    current_app.logger.info("Setting up sia hosting.")
    # Set min*price, collateral, collateralbudget, maxcollateral, maxduration
    siadata, sia_status_code = post_to_sia("host", settings["host"])
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Add path(s) to share for hosting.
    if not _rebalance_hosting_to_ratio():
        return False
    # Announce host
    # When we have nice host names, we should announce that because of possibly
    # changing IPs down the road.
    # announce_params = {"netaddress": "host.minebox.io"}
    announce_params = None
    siadata, sia_status_code = post_to_sia("host/announce", announce_params)
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                      siadata["message"]))
        return False
    return True

def restart_sia():
    current_app.logger.info("Restarting sia service.")
    retcode = subprocess.call(['/usr/bin/systemctl', 'restart', 'sia'])
    if retcode != 0:
        current_app.logger.error("Restarting sia failed with return code %s." % retcode)
        return False
    # If the return code is 0, we get here and return True (success).
    return True

def rebalance_diskspace():
    # MineBD reports a large block device size but the filesystem is formatted
    # with much less.
    devsize = _get_device_size(MINEBD_DEVICE_PATH)
    upper_space = _get_btrfs_space(MINEBD_STORAGE_PATH)
    if (upper_space["free_est"] < UPPER_SPACE_MIN
        and devsize > upper_space["total"]):
        current_app.logger.info("Less than %s MB free on upper, try to rebalance disk space." % (UPPER_SPACE_MIN // 2**20))
        space_to_add = UPPER_SPACE_TARGET - UPPER_SPACE_MIN
        # We should enlarge upper so that we have at least UPPER_SPACE_TARGET free.
        host_freespace = 0
        for basepath in glob(HOST_DIR_BASE_MASK):
            hostpath = os.path.join(basepath, HOST_DIR_NAME)
            if not os.path.isdir(hostpath):
                subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'create', hostpath])
            hostspace = _get_btrfs_space(hostpath)
            if hostspace:
                host_freespace += hostspace["free_est"]
        if host_freespace > space_to_add:
            # We have enough free space that we can enlarge upper.
            retcode = subprocess.call(['/usr/sbin/btrfs', 'filesystem', 'resize', ('+%s' % space_to_add), MINEBD_STORAGE_PATH])
            if retcode > 0:
                current_app.logger.warn("btrfs resize failed with return code %s!" % retcode)
                return False
            # See to resize sharing folders to fulfill the defined ratio.
            # If that succeeds, we should be able to enlarge upper again in the
            # future if/when needed.
            if not _rebalance_hosting_to_ratio():
                return False
        else:
            current_app.logger.error("Not enough free space left on lower disk(s) to enlarge upper storage!")
            return False
    elif upper_space["free_est"] < UPPER_SPACE_MIN:
        current_app.logger.warn("Less than %s MB free on upper, but device fully used!" % (UPPER_SPACE_MIN // 2**20))
    return True

def _rebalance_hosting_to_ratio():
    settings = get_sia_config()
    folderdata = {}
    siadata, sia_status_code = get_from_sia("host/storage")
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    for folder in siadata["folders"]:
        folderdata[folder["path"]] = folder

    success = True

    for basepath in glob(HOST_DIR_BASE_MASK):
        hostpath = os.path.join(basepath, HOST_DIR_NAME)
        if not os.path.isdir(hostpath):
            subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'create', hostpath])
        hostspace = _get_btrfs_space(hostpath)
        if hostspace:
            share_size = hostspace["free_est"] * settings["minebox_sharing"]["shared_space_ratio"]
        else:
            share_size = 0
        if hostpath in folderdata:
            # Existing folder, (try to) resize it.
            siadata, sia_status_code = post_to_sia("host/storage/folders/resize",
                                                   {"path": hostpath,
                                                    "newsize": share_size})
            if sia_status_code >= 400:
                current_app.logger.error("Sia error %s: %s" %
                                          (sia_status_code, siadata["message"]))
                success = False
        else:
            # New folder, add it.
            siadata, sia_status_code = post_to_sia("host/storage/folders/add",
                                                   {"path": hostpath,
                                                    "size": share_size})
            if sia_status_code >= 400:
                current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                              siadata["message"]))
                success = False
    return success

def get_sia_config():
    if (not hasattr(get_sia_config, "settings") or get_sia_config.settings is None
        or get_sia_config.timestamp < time.time() - 24 * 3600):
        # Always set the timestamp so we do not have to test above if it's set,
        #  as it's only unset when token is also unset
        get_sia_config.timestamp = time.time()
        if not hasattr(get_sia_config, "settings"):
            get_sia_config.settings = None
        if os.path.isfile(SIA_CONFIG_JSON):
            fileinfo = os.stat(SIA_CONFIG_JSON)
            filetime = int(fileinfo.st_mtime)
        else:
            filetime = 0
        if filetime < time.time() - 24 * 3600:
            # Get config from remote service and write to file.
            cfdata, cf_status_code = get_from_mineboxconfig('sia')
            if cf_status_code == 200:
                get_sia_config.settings = cfdata
                with open(SIA_CONFIG_JSON, 'w') as outfile:
                    json.dump(get_sia_config.settings, outfile)
            else:
                current_app.logger.error(
                  'Error %s getting Sia config from Minebox config service: %s'
                  % (cf_status_code,  cfdata["message"]))
                get_sia_config.settings = None
        if not get_sia_config.settings and os.path.isfile(SIA_CONFIG_JSON):
            # If we did not get settings remotely, read them from the file.
            with open(CONFIG_JSON_PATH) as json_file:
                get_sia_config.settings = json.load(json_file)
        elif not get_sia_config.settings:
            # We only get here if no config file exists and the remote service
            # also doesn't work. Set useful defaults.
            bytes_per_tb = 2 ** 40
            blocks_per_month = 30 * 24 * 3600 / SEC_PER_BLOCK
            sctb_per_hb = H_PER_SC / bytes_per_tb # SC / TB -> hastings / byte
            sctbmon_per_hbblk = sctb_per_hb * blocks_per_month # SC / TB / month -> hastings / byte / block
            get_sia_config.settings = {
              "renter": {
                "allowance_funds": 0 * H_PER_SC,
                "allowance_period": 6 * blocks_per_month,
              },
              "host": {
                "mincontractprice": 0 * H_PER_SC,
                "mindownloadbandwidthprice": 0 * sctb_per_hb,
                "minstorageprice": 0 * sctbmon_per_hbblk,
                "minuploadbandwidthprice": 0 * sctb_per_hb,
                "collateral": 0 * sctbmon_per_hbblk,
                "collateralbudget": 0 * H_PER_SC,
                "maxcollateral": 0 * H_PER_SC,
                "maxduration": 6 * blocks_per_month,
              },
              "minebox_sharing": {
                "enabled": False,
                "shared_space_ratio": 0.5,
              },
            }
    return get_sia_config.settings

def _get_btrfs_space(diskpath):
    spaceinfo = {}
    # See https://btrfs.wiki.kernel.org/index.php/FAQ#Understanding_free_space.2C_using_the_new_tools
    outlines = subprocess.check_output(['/usr/sbin/btrfs', 'filesystem', 'usage', '--raw', diskpath]).splitlines()
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

def _get_device_size(devpath):
    devsize = int(subprocess.check_output(['/usr/sbin/blockdev', '--getsize64', devpath]))
    return devsize
