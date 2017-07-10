# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
import subprocess

from connecttools import (get_from_sia, post_to_sia, get_from_minebd,
                          get_from_mineboxconfig)

H_PER_SC=1e24 # hastings per siacoin ("siacoinprecision" in /daemon/constants)
SEC_PER_BLOCK=600 # seconds per block ("blockfrequency" in /daemon/constants)
SIA_CONFIG_JSON="/etc/minebox/sia_config.json"

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
    current_app.logger.info("TODO: Fetch base allotment of coins from Minebox.")
    # We need the Minebox sia faucet service for this (See MIN-130).
    return

def set_allowance():
    current_app.logger.info("Set an allowance for renting out files.")
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
    current_app.logger.info("TODO: Set up sia hosting.")
    # See https://blog.sia.tech/how-to-run-a-host-on-sia-2159ebc4725 for a
    # blog post explaining all steps to do.
    settings = get_sia_config()
    # Set min*price, collateral, collateralbudget, maxcollateral, maxduration
    siadata, sia_status_code = post_to_sia("host", settings["host"])
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # TODO: Set up sia hosting (see MIN-129).
    # add folder
    # announce host
    return

def restart_sia():
    current_app.logger.info("Restarting sia service.")
    retcode = subprocess.call(['/usr/bin/systemctl', 'restart', 'sia'])
    if retcode != 0:
        current_app.logger.error("Restarting sia failed with return code %s." % retcode)
        return False
    # If the return code is 0, we get here and return True (success).
    return True

def get_sia_config():
    if (not hasattr(get_sia_config, "settings") or get_sia_config.settings is None
        or get_sia_config.timestamp < time.time() - 24 * 3600):
        # Always set the timestamp so we do not have to test above if it's set,
        #  as it's only unset when token is also unset
        get_sia_config.timestamp = time.time()
        if not hasattr(get_sia_config, "settings"):
            get_sia_config.settings = None
        if isfile(SIA_CONFIG_JSON):
            fileinfo = stat(SIA_CONFIG_JSON)
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
                current_app.logger.error('Error %s getting Sia config from Minebox config service: %s' % (cf_status_code,  cfdata["message"]))
                get_sia_config.settings = None
        if not get_sia_config.settings and isfile(SIA_CONFIG_JSON):
            # If we did not get settings remotely, read them from the file.
            with open(CONFIG_JSON_PATH) as json_file:
                get_sia_config.settings = json.load(json_file)
        elif not get_sia_config.settings:
            # We only get here if no config file exists and the remote service
            # also doesn't work. Set useful defaults.
            bytes_per_tb = 2 ** 40
            months_per_block = 30 * 24 * 3600 / SEC_PER_BLOCK
            sctb_per_hb = H_PER_SC / BYTES_PER_TB # SC / TB -> hastings / byte
            sctbmon_per_hbblk = sctb_per_hb * months_per_block # SC / TB / month -> hastings / byte / block
            get_sia_config.settings = {
              "renter": {
                "allowance_funds": 0 * H_PER_SC,
                "allowance_period": 6 * months_per_block,
              },
              "host": {
                "mincontractprice": 0 * H_PER_SC,
                "mindownloadbandwidthprice": 0 * sctb_per_hb,
                "minstorageprice": 0 * sctbmon_per_hbblk,
                "minuploadbandwidthprice": 0 * sctb_per_hb,
                "collateral": 0 * sctbmon_per_hbblk,
                "collateralbudget": 0 * H_PER_SC,
                "maxcollateral": 0 * H_PER_SC,
                "maxduration": 6 * months_per_block,
              },
            }
    return get_sia_config.settings
