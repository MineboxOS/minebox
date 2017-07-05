# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json

from connecttools import get_from_sia, post_to_sia, get_from_minebd

H_PER_SC=1e24 # hastings per siacoin ("siacoinprecision" in /daemon/constants)
SEC_PER_BLOCK=600 # seconds per block ("blockfrequency" in /daemon/constants)

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
    current_app.logger.info("TODO: Set an allowance for renting out files.")
    # The funds are in hastings, the period in blocks.
    allowance_funds = 0 * H_PER_SC
    allowance_period = 6 * 30 * 24 * 3600 / SEC_PER_BLOCK # 6 months
    siadata, sia_status_code = post_to_sia('renter',
                                           {"funds": allowance_funds,
                                            "period": allowance_period})
    if sia_status_code >= 400:
        current_app.logger.error("Sia error %s: %s" % (sia_status_code,
                                                       siadata["message"]))
        return False
    # Typically, we get a 204 status code here.
    return True

def set_up_hosting():
    current_app.logger.info("TODO: Set up sia hosting.")
    # TODO: Set up sia hosting (see MIN-129).
    return
