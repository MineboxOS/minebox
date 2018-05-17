# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from glob import glob
from datetime import datetime, timedelta
import subprocess
import os
import pwd
import grp
import re
import time

from connecttools import (get_from_sia, post_to_sia, get_from_minebd,
                          get_from_mineboxconfig, post_to_faucetservice)
from systemtools import (get_btrfs_space, get_device_size,
                         create_btrfs_subvolume, resize_btrfs_volume)

# Note: no not use float (e.g. 1e24) for numers that need high precision!
# The ** operator produces int, which is fine, or use decimal.Decimal()
# if you want to do decimal math with such high precision.
H_PER_SC=10**24 # hastings per siacoin ("siacoinprecision" in /daemon/constants)
SEC_PER_BLOCK=600 # seconds per block ("blockfrequency" in /daemon/constants)
EST_SEC_PER_BLOCK = 9 * 60 # estimate shorter block time for better UX
KNOWNBLOCK_HEIGHT = 100000
KNOWNBLOCK_DATETIME = datetime(2017, 4, 13, 23, 29, 49, 0) # Assumes UTC
SIA_CONFIG_JSON="/etc/minebox/sia_config.json"
SIA_DIR="/mnt/lower1/sia"
HOST_DIR_BASE_MASK="/mnt/lower*"
HOST_DIR_NAME="hostedfiles"
MINEBD_STORAGE_PATH="/mnt/storage"
MINEBD_DEVICE_PATH="/dev/nbd0"
UPPER_SPACE_MIN=50*(2**20)
UPPER_SPACE_TARGET=100*(2**20)
# Granularity of Sia hosting folders, see
# https://github.com/NebulousLabs/Sia/blob/master/modules/host/contractmanager/consts.go#L57-L66
SIA_HOST_GRANULARITY = 64 * 4 * (2**20)

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
    current_app.logger.info("Fetching base allotment of coins from Minebox.")
    # Fetch a wallet address to send the siacoins to.
    siadata, sia_status_code = get_from_sia("wallet/address")
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Use the address from above to request siacoins from the faucet.
    fsdata, fs_status_code = post_to_faucetservice("getCoins",
                                                   {"address": siadata["address"]})
    if fs_status_code >= 400:
        current_app.logger.error("Faucet error %s: %s" % (fs_status_code,
                                                          fsdata["message"]))
        return False
    return True

def set_allowance():
    current_app.logger.info("Setting an allowance for renting out files.")
    settings = get_sia_config()
    siadata, sia_status_code = post_to_sia('renter', {
      "funds": str(settings["renter"]["allowance_funds"]),
      "period": str(settings["renter"]["allowance_period"]),
    })
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
    siadata, sia_status_code = post_to_sia("host", {
      "mincontractprice": str(settings["host"]["mincontractprice"]),
      "mindownloadbandwidthprice": str(settings["host"]["mindownloadbandwidthprice"]),
      "minstorageprice": str(settings["host"]["minstorageprice"]),
      "minuploadbandwidthprice": str(settings["host"]["minuploadbandwidthprice"]),
      "collateral": str(settings["host"]["collateral"]),
      "collateralbudget": str(settings["host"]["collateralbudget"]),
      "maxcollateral": str(settings["host"]["maxcollateral"]),
      "maxduration": str(settings["host"]["maxduration"]),
    })
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

def update_sia_config():
    settings = get_sia_config()
    # Renting settings
    siadata, sia_status_code = get_from_sia("renter")
    if sia_status_code >= 400:
        # If we can't get the current settings, no use in comparing to new ones.
        return False
    renter_params = {}
    # If new settings values differ by at least 10% from currently set values,
    # only then update Sia with the new settings.
    if _absdiff(settings["renter"]["allowance_funds"],
                int(siadata["settings"]["allowance"]["funds"])) > 0.1:
        renter_params["funds"] = str(settings["renter"]["allowance_funds"])
    if _absdiff(settings["renter"]["allowance_period"],
                int(siadata["settings"]["allowance"]["period"])) > 0.1:
        renter_params["period"] = str(settings["renter"]["allowance_period"])
    if renter_params:
        current_app.logger.info("Updating Sia renter/allowance settings.")
        siadata, sia_status_code = post_to_sia("renter", renter_params)
        if sia_status_code >= 400:
            current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                          siadata["message"]))
            return False
    # Hosting settings
    siadata, sia_status_code = get_from_sia('host')
    if sia_status_code >= 400:
        # If we can't get the current settings, no use in comparing to new ones.
        return False
    if (not siadata["internalsettings"]["acceptingcontracts"]
        and not settings["minebox_sharing"]["enabled"]):
        # If hosting is deactivated, pings will call setup_sia_system()
        # This will care about settings so we don't do anything here.
        return True
    host_params = {}
    if settings["minebox_sharing"]["enabled"] != siadata["internalsettings"]["acceptingcontracts"]:
        host_params["acceptingcontracts"] = settings["minebox_sharing"]["enabled"]
    for var in ["mincontractprice", "mindownloadbandwidthprice",
                "minstorageprice", "minuploadbandwidthprice", "collateral",
                "collateralbudget", "maxcollateral", "maxduration"]:
        if _absdiff(settings["host"][var], int(siadata["internalsettings"][var])) > 0.1:
            host_params[var] = str(settings["host"][var])
    if host_params:
        current_app.logger.info("Updating Sia host settings.")
        siadata, sia_status_code = post_to_sia("host", host_params)
        if sia_status_code >= 400:
            current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                          siadata["message"]))
            return False
    # We're done here :)
    return True

def _absdiff(comparevalue, basevalue):
    # Calculate a fractional absolut difference / deviation of a compare value
    # to a base value.
    if basevalue:
        return abs(float(comparevalue - basevalue) / basevalue)
    if comparevalue != basevalue:
        return float('inf')
    return 0

def rebalance_diskspace():
    # MineBD reports a large block device size but the filesystem is formatted
    # with much less.
    if not os.path.ismount(MINEBD_STORAGE_PATH):
        current_app.logger.warn("Upper storage is not mounted (yet), cannot rebalance disk space.")
        return False
    devsize = get_device_size(MINEBD_DEVICE_PATH)
    upper_space = get_btrfs_space(MINEBD_STORAGE_PATH)
    if (upper_space["free_est"] < UPPER_SPACE_MIN
        and devsize > upper_space["total"]):
        current_app.logger.info("Less than %s MB free on upper, try to rebalance disk space." % (UPPER_SPACE_MIN // 2**20))
        space_to_add = UPPER_SPACE_TARGET - UPPER_SPACE_MIN
        # We should enlarge upper so that we have at least UPPER_SPACE_TARGET free.
        host_freespace = 0
        for basepath in glob(HOST_DIR_BASE_MASK):
            hostpath = os.path.join(basepath, HOST_DIR_NAME)
            if not os.path.isdir(hostpath):
                create_btrfs_subvolume(hostpath)
            hostspace = get_btrfs_space(hostpath)
            if hostspace:
                host_freespace += hostspace["free_est"]
        if host_freespace > space_to_add:
            # We have enough free space that we can enlarge upper.
            if not resize_btrfs_volume(('+%s' % space_to_add), MINEBD_STORAGE_PATH]):
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
    else:
        # Even if free space is plenty, let's rebalance the hosting.
        if not _rebalance_hosting_to_ratio():
            return False
    return True

def _rebalance_hosting_to_ratio():
    settings = get_sia_config()
    folderdata = {}
    if not settings["minebox_sharing"]["enabled"]:
        current_app.logger.warn("Sharing not enabled, cannot rebalance hosting space.")
        return False
    siadata, sia_status_code = get_from_sia("host/storage")
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    used_space = 0
    if siadata["folders"]:
        for folder in siadata["folders"]:
            folderdata[folder["path"]] = folder
            used_space += (folder["capacity"] - folder["capacityremaining"])

    success = True

    for basepath in glob(HOST_DIR_BASE_MASK):
        hostpath = os.path.join(basepath, HOST_DIR_NAME)
        if not os.path.isdir(hostpath):
            create_btrfs_subvolume(hostpath)
        # Make sure the directory is owned by the sia user and group.
        uid = pwd.getpwnam("sia").pw_uid
        gid = grp.getgrnam("sia").gr_gid
        os.chown(hostpath, uid, gid)
        hostspace = get_btrfs_space(hostpath)
        if hostspace:
            # We need to add already used hosting space to free space in this
            # calculation, otherwise hosted files would reduce the hosting
            # capacity!
            raw_size = ((hostspace["free_est"] + used_space)
                        * settings["minebox_sharing"]["shared_space_ratio"])
            # The actual share size must be a multiple of the granularity.
            share_size = int((raw_size // SIA_HOST_GRANULARITY)
                             * SIA_HOST_GRANULARITY)
        else:
            share_size = 0
        if hostpath in folderdata:
            # Existing folder, (try to) resize it if necessary.
            if folderdata[hostpath]["capacity"] != share_size:
                current_app.logger.info("Resize Sia hosting space at %s to %s MB.",
                                        hostpath, int(share_size // 2**20))
                siadata, sia_status_code = post_to_sia("host/storage/folders/resize",
                                                       {"path": hostpath,
                                                        "newsize": share_size})
                if sia_status_code >= 400:
                    current_app.logger.error("Sia error %s: %s" %
                                              (sia_status_code, siadata["message"]))
                    success = False
        else:
            # New folder, add it.
            current_app.logger.info("Add Sia hosting space at %s with %s MB.",
                                    hostpath, int(share_size // 2**20))
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
        sia_settings = {}
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
                sia_settings = cfdata
                with open(SIA_CONFIG_JSON, 'w') as outfile:
                    json.dump(sia_settings, outfile)
            else:
                current_app.logger.error(
                  'Error %s getting Sia config from Minebox settings service: %s'
                  % (cf_status_code,  cfdata["message"]))
        if not sia_settings and os.path.isfile(SIA_CONFIG_JSON):
            # If we did not get settings remotely, read them from the file.
            with open(SIA_CONFIG_JSON) as json_file:
                sia_settings = json.load(json_file)
        # Convert values to units that siad is using.
        # Set useful defaults in case either a value is missing or both the
        # Minebox settings service doesn't work and we have no stored config.
        for topic in ["renter", "host", "minebox_sharing"]:
            if not topic in sia_settings:
                sia_settings[topic] = {}
        bytes_per_tb = 1e12 # not 2 ** 40 as Sia uses SI TB, see https://github.com/NebulousLabs/Sia/blob/v1.2.2/modules/host.go#L14
        blocks_per_month = 30 * 24 * 3600 / SEC_PER_BLOCK
        sctb_per_hb = H_PER_SC / bytes_per_tb # SC / TB -> hastings / byte
        sctbmon_per_hbblk = sctb_per_hb / blocks_per_month # SC / TB / month -> hastings / byte / block
        get_sia_config.settings = {
          "renter": {
            "allowance_funds": int((
              sia_settings["renter"]["allowance_funds"]
              if "allowance_funds" in sia_settings["renter"]
              else 1000) * H_PER_SC),
            "allowance_period": int((
              sia_settings["renter"]["allowance_period"]
              if "allowance_period" in sia_settings["renter"]
              else 6) * blocks_per_month),
          },
          "host": {
            "mincontractprice": int((
              sia_settings["host"]["mincontractprice"]
              if "mincontractprice" in sia_settings["host"]
              else 3) * H_PER_SC),
            "mindownloadbandwidthprice": int((
              sia_settings["host"]["mindownloadbandwidthprice"]
              if "mindownloadbandwidthprice" in sia_settings["host"]
              else 41) * sctb_per_hb),
            "minstorageprice": int((
              sia_settings["host"]["minstorageprice"]
              if "minstorageprice" in sia_settings["host"]
              else 120) * sctbmon_per_hbblk),
            "minuploadbandwidthprice": int((
              sia_settings["host"]["minuploadbandwidthprice"]
              if "minuploadbandwidthprice" in sia_settings["host"]
              else 8.2) * sctb_per_hb),
            "collateral": int((
              sia_settings["host"]["collateral"]
              if "collateral" in sia_settings["host"]
              else 80) * sctbmon_per_hbblk),
            "collateralbudget": int((
              sia_settings["host"]["collateralbudget"]
              if "collateralbudget" in sia_settings["host"]
              else 2000) * H_PER_SC),
            "maxcollateral": int((
              sia_settings["host"]["maxcollateral"]
              if "maxcollateral" in sia_settings["host"]
              else 100) * H_PER_SC),
            "maxduration": int((
              sia_settings["host"]["maxduration"]
              if "maxduration" in sia_settings["host"]
              else 6) * blocks_per_month),
          },
          "minebox_sharing": {
            "enabled": (
              sia_settings["minebox_sharing"]["enabled"]
              if "enabled" in sia_settings["minebox_sharing"]
              else False),
            "shared_space_ratio": (
              sia_settings["minebox_sharing"]["shared_space_ratio"]
              if "shared_space_ratio" in sia_settings["minebox_sharing"]
              else 0.5),
          },
        }
    return get_sia_config.settings

def estimate_current_height():
    # Estimate current block height so we can estimate a sync progress.
    # See https://github.com/NebulousLabs/Sia/blob/master/cmd/siac/consensuscmd.go#L50
    est_datetime = datetime.now()
    diff_seconds = (est_datetime - KNOWNBLOCK_DATETIME).total_seconds()
    est_height = KNOWNBLOCK_HEIGHT + round(float(diff_seconds)
                                           / EST_SEC_PER_BLOCK)
    return est_height

def estimate_datetime_for_height(blockheight):
    # Estimate a datetime for a block height for display in the UI.
    est_dt = (KNOWNBLOCK_DATETIME
              + timedelta(seconds=((blockheight - KNOWNBLOCK_HEIGHT)
                                   * SEC_PER_BLOCK)))
    return est_dt


def estimate_timestamp_for_height(blockheight):
    # Estimate a timestamp for a block height for display in the UI.
    est_ts = int((estimate_datetime_for_height(blockheight)
                  - datetime(1970, 1, 1)).total_seconds())
    return est_ts
