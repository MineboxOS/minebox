# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json

from connecttools import get_from_sia, post_to_sia, get_from_minebd

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
    # Get wallet seed from MineBD (see MIN-128).
    seed = None
    return seed

def init_wallet(seed):
    return

def unlock_wallet(seed):
    return

def fetch_siacoins():
    return

def set_allowance():
    return

def set_up_hosting():
    # TODO: Set up sia hosting (see MIN-129).
    return
