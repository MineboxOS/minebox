#!/usr/bin/env python

# The Minebox UI Gateway (MUG)
# API Docs: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md


from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import Flask, request, jsonify, json
from os.path import ismount, isfile
from os import environ,uname
from distutils.util import strtobool
import os
import re
import logging
import time
import subprocess
import pwd
import decimal
import backupinfo
from connecttools import (set_origin, check_login, get_demo_url,
                          get_from_sia, post_to_sia, get_from_minebd,
                          get_from_backupservice, post_to_backupservice,
                          rockstor_user_setup)
from siatools import (H_PER_SC, SEC_PER_BLOCK, estimate_current_height,
                      estimate_timestamp_for_height)
from systemtools import (get_box_settings, write_box_settings)


# Define various constants.
REST_HOST="127.0.0.1"
REST_HOST_DEBUG="0.0.0.0"
REST_HOST_TLS="0.0.0.0"
REST_PORT=5000
MUG_CONFIG_JSON_PATH="/etc/minebox/mug_config.json"
SSL_CERT="/opt/rockstor/certs/rockstor.cert"
SSL_KEY="/opt/rockstor/certs/rockstor.key"
MINEBD_STORAGE_PATH="/mnt/storage"
DEMOSIAC_CMD="/root/minebox-client-tools_vm/sia/demosiac.sh"
SUDO="/usr/bin/sudo"
MBKEY_CMD="/usr/lib/minebox/mbkey.sh"
TRAFFICSHAPER_CMD="/usr/lib/minebox/trafficshaper.sh"

config = {}

app = Flask(__name__)


@app.before_first_request
def before_first_request():
    global config
    # Read configuration from JSON file.
    if isfile(MUG_CONFIG_JSON_PATH):
        with open(MUG_CONFIG_JSON_PATH) as json_file:
            config = json.load(json_file)
    # Set default values.
    if not "allowed_cors_hosts" in config:
        config["allowed_cors_hosts"] = []
    # Copy those values to the Flask app that may be used in imports.
    app.config["allowed_cors_hosts"] = config["allowed_cors_hosts"]


@app.route("/")
@set_origin()
def api_root():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get
    links = []
    for rule in app.url_map.iter_rules():
        # Flask has a default route for serving static files, let's exclude it.
        if rule.endpoint != "static":
            links.append({"url": rule.rule,
                          "methods": ','.join([x for x in rule.methods if x not in ["OPTIONS","HEAD"]])})
    return jsonify(supported_urls=sorted(links, key=lambda rule: rule["url"])), 200


@app.route("/backup/list", methods=['GET'])
@set_origin()
def api_backup_list():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-backuplist
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    metalist = backupinfo.get_list(True)
    return jsonify(metalist), 200


@app.route("/backup/<backupname>/status", methods=['GET'])
@set_origin()
def api_backup_status(backupname):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-backup1493807150status
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-backuplateststatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    if backupname == "latest":
        backupname = backupinfo.get_latest()
    elif not re.match(r'^\d+$', backupname):
        return jsonify(error="Illegal backup name."), 400

    if backupname:
        backupstatus, status_code = backupinfo.get_status(backupname, True)
    else:
        status_code = 404
    if status_code == 404:
        # Use different error message with 404.
        backupstatus = {"message": "No backup found with that name or its file info is missing."}

    return jsonify(backupstatus), status_code


@app.route("/backup/all/status", methods=['GET'])
@set_origin()
def api_backup_all_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-backupallstatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    backuplist = backupinfo.get_list(True)

    statuslist = []
    for backupname in backuplist:
        backupstatus, status_code = backupinfo.get_status(backupname, True)
        statuslist.append(backupstatus)

    return jsonify(statuslist), 200


@app.route("/backup/start", methods=['POST'])
@set_origin()
def api_backup_start():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-backupstart
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bsdata, bs_status_code = get_from_backupservice('trigger')
    if bs_status_code == 200:
        # Call into get_status with use_cache=False so that we guarantee to
        # re-fetch info from backup-service and know about this new backup.
        backupinfo.get_status(bsdata["name"], False, False)
    return jsonify(bsdata), bs_status_code


@app.route("/key/status", methods=['GET'])
@set_origin()
def api_key_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-keystatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key/generate", methods=['GET'])
@set_origin()
def api_key_generate():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-keygenerate
    mbdata, mb_status_code = get_from_minebd('keys/asJson')
    # For the moment, just blindly hand over the result from MineBD.
    return jsonify(mbdata), mb_status_code


@app.route("/key/verify", methods=['POST'])
@set_origin()
def api_key_verify():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-keyverify
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key", methods=['PUT'])
@set_origin()
def api_key_put():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-put-key
    mbdata, mb_status_code = get_from_minebd('status')
    if mb_status_code == 200:
        if mbdata["hasEncryptionKey"]:
            return jsonify(message="File system is already encrypted, cannot set key."), 400
        # MineBD is running but encryption is not yet set up, we can actually set a key!
        if len(request.data):
          retcode = subprocess.call([SUDO, MBKEY_CMD, "set", request.data])
          if retcode == 0:
              return jsonify(message="Key set successfully"), 200
          else:
              return jsonify(message="Setting key failed."), 500
        else:
            return jsonify(message="No useful key handed over."), 400
    else:
        return jsonify(message="The Minebox storage is not running, please reboot the box or call support!"), 503


@app.route("/key", methods=['POST'])
@set_origin()
def api_key_post():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-key
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/consensus", methods=['GET'])
@set_origin()
def api_consensus():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-consensus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, status_code = get_from_sia('consensus')
    # For now, just return the info from Sia directly.
    return jsonify(siadata), status_code


@app.route("/contracts", methods=['GET'])
@set_origin()
def api_contracts():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-contracts
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, sia_status_code = get_from_sia('renter/contracts')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code
    # Create a summary similar to what `siac renter contracts` presents.
    # We could expose the full details of a contract in a different route, e.g. /contract/<id>.
    contractlist = []
    for contract in siadata["contracts"]:
        contractlist.append({
          "id": contract["id"],
          "host": contract["netaddress"],
          "funds_remaining_sc": int(contract["renterfunds"]) / H_PER_SC,
          "funds_spent_sc": (int(contract["StorageSpending"]) +
                             int(contract["uploadspending"]) +
                             int(contract["downloadspending"])) / H_PER_SC,
          "fees_spent_sc": int(contract["fees"]) / H_PER_SC,
          "totalcost_sc": int(contract["totalcost"]) / H_PER_SC,
          "data_size": contract["size"],
          "height_end": contract["endheight"],
          "esttime_end": estimate_timestamp_for_height(contract["endheight"]),
        })
    return jsonify(contractlist), 200


@app.route("/contractstats", methods=['GET'])
@set_origin()
def api_contractstats():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-contractstats
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, sia_status_code = get_from_sia('renter/contracts')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code
    # List stats about the contracts.
    statdata = {
      "contract_count": 0,
      "data_size": 0,
      "totalcost_sc": 0,
      "funds_remaining_sc": 0
    }
    for contract in siadata["contracts"]:
        statdata["contract_count"] += 1
        statdata["data_size"] += contract["size"]
        statdata["totalcost_sc"] += int(contract["totalcost"]) / H_PER_SC
        statdata["funds_remaining_sc"] += int(contract["renterfunds"]) / H_PER_SC
    return jsonify(statdata), 200


@app.route("/transactions", methods=['GET'])
@set_origin()
def api_transactions():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-transactions
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    consdata, cons_status_code = get_from_sia('consensus')
    if cons_status_code == 200:
        consensus_height = consdata["height"]
    else:
        return jsonify(consdata), cons_status_code
    blocks_per_day = 24 * 3600 / SEC_PER_BLOCK
    offset = int(request.args.get('offsetdays') or 0) * blocks_per_day
    duration = int(request.args.get('durationdays') or 1) * blocks_per_day
    endheight = int(consensus_height - offset)
    startheight = int(endheight - duration)
    siadata, sia_status_code = get_from_sia("wallet/transactions?startheight=%s&endheight=%s" % (startheight, endheight))
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code
    # For now, just return the info from Sia directly.
    return jsonify(siadata), sia_status_code


@app.route("/status", methods=['GET'])
@set_origin()
def api_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-status
    username = check_login()
    outdata = {}
    outdata["hostname"] = uname()[1]
    if username:
        outdata["logged_in"] = True
        outdata["user"] = username
    else:
        outdata["logged_in"] = False
        outdata["user"] = None

    mbdata, mb_status_code = get_from_minebd('status')
    if mb_status_code == 200:
        outdata["minebd_running"] = True
        outdata["minebd_encrypted"] = mbdata["hasEncryptionKey"]
        outdata["minebd_storage_mounted"] = ismount(MINEBD_STORAGE_PATH)
        ST_RDONLY = 1 # In Python >= 3.2, we could use os.ST_RDONLY directly instead.
        outdata["minebd_storage_mount_readonly"] = bool(os.statvfs(MINEBD_STORAGE_PATH).f_flag & ST_RDONLY)
        outdata["restore_running"] = mbdata["restoreRunning"]
        outdata["restore_progress"] = mbdata["completedRestorePercent"]
    else:
        outdata["minebd_running"] = False
        outdata["minebd_encrypted"] = None
        outdata["minebd_storage_mounted"] = False
        outdata["minebd_storage_mount_readonly"] = None
        outdata["restore_running"] = False
        outdata["restore_progress"] = None

    hasusers = False
    for user in pwd.getpwall():
        if (user.pw_uid >= 1000 and user.pw_uid < 65500
            and user.pw_name != "sia" and not user.pw_name.endswith("$")):
            hasusers = True
    outdata["users_created"] = hasusers
    outdata["user_setup_complete"] = rockstor_user_setup()

    if 'DEMO' in environ:
        outdata["backup_type"] = "sia_demo"
    else:
        outdata["backup_type"] = "sia"
    consdata, cons_status_code = get_from_sia('consensus')
    if cons_status_code == 200:
        outdata["sia_daemon_running"] = True
        outdata["consensus_height"] = consdata["height"]
        outdata["consensus_synced"] = consdata["synced"]
    else:
        outdata["sia_daemon_running"] = False
        outdata["consensus_height"] = None
        outdata["consensus_synced"] = None
    walletdata, wallet_status_code = get_from_sia('wallet')
    if username and wallet_status_code == 200:
        outdata["wallet_unlocked"] = walletdata["unlocked"]
        outdata["wallet_encrypted"] = walletdata["encrypted"]
        outdata["wallet_confirmed_balance_sc"] = int(walletdata["confirmedsiacoinbalance"]) / H_PER_SC
        outdata["wallet_unconfirmed_delta_sc"] = (int(walletdata["unconfirmedincomingsiacoins"]) -
                                                  int(walletdata["unconfirmedoutgoingsiacoins"])) / H_PER_SC

    else:
        outdata["wallet_unlocked"] = None
        outdata["wallet_encrypted"] = None
        outdata["wallet_confirmed_balance_sc"] = None
        outdata["wallet_unconfirmed_delta_sc"] = None
    return jsonify(outdata), 200


@app.route("/sia/status", methods=['GET'])
@set_origin()
def api_sia_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-siastatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bytes_per_tb = 1e12 # not 2 ** 40 as Sia uses SI TB, see https://github.com/NebulousLabs/Sia/blob/v1.2.2/modules/host.go#L14
    blocks_per_month = 30 * 24 * 3600 / SEC_PER_BLOCK
    sctb_per_hb = H_PER_SC / bytes_per_tb # SC / TB -> hastings / byte
    sctbmon_per_hbblk = sctb_per_hb / blocks_per_month # SC / TB / month -> hastings / byte / block
    outdata = {}
    consdata, cons_status_code = get_from_sia('consensus')
    if cons_status_code == 200:
        outdata["sia_daemon_running"] = True
        outdata["consensus"] = {
          "height": consdata["height"],
          "synced": consdata["synced"],
        }
        if consdata["synced"]:
            outdata["consensus"]["sync_progress"] = 100
        else:
            outdata["consensus"]["sync_progress"] = (100 * consdata["height"]
                                                     // estimate_current_height())
    else:
        outdata["sia_daemon_running"] = False
        outdata["consensus"] = {
          "height": None,
          "synced": None,
          "sync_progress": None,
        }
    verdata, ver_status_code = get_from_sia("daemon/version")
    if ver_status_code == 200:
        outdata["sia_version"] = verdata["version"]
    else:
        outdata["sia_version"] = None
    walletdata, wallet_status_code = get_from_sia('wallet')
    if wallet_status_code == 200:
        outdata["wallet"] = {
          "unlocked": walletdata["unlocked"],
          "encrypted": walletdata["encrypted"],
          "confirmed_balance_sc": int(walletdata["confirmedsiacoinbalance"]) / H_PER_SC,
          "unconfirmed_delta_sc": (int(walletdata["unconfirmedincomingsiacoins"]) -
                                   int(walletdata["unconfirmedoutgoingsiacoins"])) / H_PER_SC,
        }
    else:
        outdata["wallet"] = {
          "unlocked": None,
          "encrypted": None,
          "confirmed_balance_sc": None,
          "unconfirmed_delta_sc": None,
        }
    siadata, sia_status_code = get_from_sia("renter/contracts")
    if sia_status_code == 200:
        outdata["renting"] = {"contracts": len(siadata["contracts"])
                                           if siadata["contracts"] else 0}
    else:
        outdata["renting"] = {"contracts": None}
    siadata, sia_status_code = get_from_sia("renter/files")
    if sia_status_code == 200:
        if siadata["files"]:
            outdata["renting"]["uploaded_files"] = len(siadata["files"])
            upsize = 0
            for fdata in siadata["files"]:
                upsize += fdata["filesize"] * fdata["redundancy"]
            outdata["renting"]["uploaded_size"] = upsize
        else:
            outdata["renting"]["uploaded_files"] = 0
            outdata["renting"]["uploaded_size"] = 0
    else:
        outdata["renting"]["uploaded_files"] = None
        outdata["renting"]["uploaded_size"] = None
    siadata, sia_status_code = get_from_sia("renter")
    if sia_status_code == 200:
        outdata["renting"]["allowance_funds_sc"] = int(siadata["settings"]["allowance"]["funds"]) / H_PER_SC
        outdata["renting"]["allowance_months"] = siadata["settings"]["allowance"]["period"] / blocks_per_month
        outdata["renting"]["siacoins_spent"] = (int(siadata["financialmetrics"]["contractspending"]) +
                                                int(siadata["financialmetrics"]["downloadspending"]) +
                                                int(siadata["financialmetrics"]["storagespending"]) +
                                                int(siadata["financialmetrics"]["uploadspending"]))/ H_PER_SC
        outdata["renting"]["siacoins_unspent"] = int(siadata["financialmetrics"]["unspent"]) / H_PER_SC
    else:
        outdata["renting"]["allowance_funds_sc"] = None
        outdata["renting"]["allowance_months"] = None
        outdata["renting"]["siacoins_spent"] = None
        outdata["renting"]["siacoins_unspent"] = None
    siadata, sia_status_code = get_from_sia('host')
    if sia_status_code == 200:
        outdata["hosting"] = {
          "enabled": siadata["internalsettings"]["acceptingcontracts"],
          "maxduration_months": siadata["internalsettings"]["maxduration"] / blocks_per_month,
          "netaddress": siadata["internalsettings"]["netaddress"],
          "collateral_sc": int(siadata["internalsettings"]["collateral"]) / sctbmon_per_hbblk,
          "collateralbudget_sc": int(siadata["internalsettings"]["collateralbudget"]) / H_PER_SC,
          "maxcollateral_sc": int(siadata["internalsettings"]["maxcollateral"]) / H_PER_SC,
          "mincontractprice_sc": int(siadata["internalsettings"]["mincontractprice"]) / H_PER_SC,
          "mindownloadbandwidthprice_sc": int(siadata["internalsettings"]["mindownloadbandwidthprice"]) / sctb_per_hb,
          "minstorageprice_sc": int(siadata["internalsettings"]["minstorageprice"]) / sctbmon_per_hbblk,
          "minuploadbandwidthprice_sc": int(siadata["internalsettings"]["minuploadbandwidthprice"]) / sctb_per_hb,
          "connectabilitystatus": siadata["connectabilitystatus"],
          "workingstatus": siadata["workingstatus"],
          "contracts": siadata["financialmetrics"]["contractcount"],
          "collateral_locked_sc": int(siadata["financialmetrics"]["lockedstoragecollateral"]) / H_PER_SC,
          "collateral_lost_sc": int(siadata["financialmetrics"]["loststoragecollateral"]) / H_PER_SC,
          "collateral_risked_sc": int(siadata["financialmetrics"]["riskedstoragecollateral"]) / H_PER_SC,
          "revenue_sc": (int(siadata["financialmetrics"]["storagerevenue"]) +
                         int(siadata["financialmetrics"]["downloadbandwidthrevenue"]) +
                         int(siadata["financialmetrics"]["uploadbandwidthrevenue"])) / H_PER_SC,
        }
    else:
        outdata["hosting"] = {
          "enabled": None,
        }

    return jsonify(outdata), 200


@app.route("/settings", methods=['GET'])
@set_origin()
def api_settings_get():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-settings
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    settings = get_box_settings()
    return jsonify(settings), 200


@app.route("/settings", methods=['POST'])
@set_origin()
def api_settings_post():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-settings
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    # Read current settings from disk.
    settings = get_box_settings()
    # Put any entries from the post into mbsettings.
    if "sia_upload_limit_kbps" in request.form:
        settings["sia_upload_limit_kbps"] = int(request.form["sia_upload_limit_kbps"])
    if "display_currency" in request.form:
        settings["display_currency"] = request.form["display_currency"]
    # Write settings to disk.
    success, errmsg = write_box_settings(settings)
    if not success:
        app.logger.warn(errmsg)
        return jsonify(message="Error writing settings to disk."), 503
    retcode = subprocess.call([SUDO, TRAFFICSHAPER_CMD, "restart"])
    if retcode != 0:
        app.logger.error("Restarting traffic shaper failed, return code: %s" % retcode)
    return jsonify(message="Settings saved and applied successfully."), 200


@app.route("/storage/shares", methods=['GET'])
@set_origin()
def api_storage_shares():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-storageshares
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bsdata, bs_status_code = get_from_backupservice('storage/shares')
    return jsonify(bsdata), bs_status_code


@app.route("/storage/shares/add/<share>", methods=['POST'])
@set_origin()
def api_storage_shares_delete(share):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-storagesharesadd
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bsdata, bs_status_code = post_to_backupservice('storage/shares/add/%s' % share, {})
    return jsonify(bsdata), bs_status_code


@app.route("/storage/shares/delete/<share>", methods=['POST'])
@set_origin()
def api_storage_shares_delete(share):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-storagesharesdelete
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bsdata, bs_status_code = post_to_backupservice('storage/shares/delete/%s' % share, {})
    return jsonify(bsdata), bs_status_code


@app.route("/wallet/status", methods=['GET'])
@set_origin()
def api_wallet_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-walletstatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, sia_status_code = get_from_sia('wallet')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code
    walletdata = {
      "encrypted": siadata["encrypted"],
      "unlocked": siadata["unlocked"],
      "confirmedsiacoinbalance": siadata["confirmedsiacoinbalance"],
      "confirmedsiacoinbalance_sc": int(siadata["confirmedsiacoinbalance"]) / H_PER_SC,
      "unconfirmedincomingsiacoins": siadata["unconfirmedincomingsiacoins"],
      "unconfirmedincomingsiacoins_sc": int(siadata["unconfirmedincomingsiacoins"]) / H_PER_SC,
      "unconfirmedoutgoingsiacoins": siadata["unconfirmedoutgoingsiacoins"],
      "unconfirmedoutgoingsiacoins_sc": int(siadata["unconfirmedoutgoingsiacoins"]) / H_PER_SC,
      "siacoinclaimbalance": siadata["siacoinclaimbalance"],
      "siacoinclaimbalance_sc": int(siadata["siacoinclaimbalance"]) / H_PER_SC,
      "siafundbalance": siadata["siafundbalance"],
      "siafundbalance_sc": int(siadata["siafundbalance"]) / H_PER_SC,
    }
    return jsonify(walletdata), 200


@app.route("/wallet/address", methods=['GET'])
@set_origin()
def api_wallet_address():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-walletaddress
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, sia_status_code = get_from_sia('wallet/address')
    # Just return the info from Sia directly as it's either an error
    # or the address in a field called "address", so pretty straight forward.
    return jsonify(siadata), sia_status_code


@app.route("/wallet/send", methods=['POST'])
@set_origin()
def api_wallet_send():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-post-walletsend
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    # The sia daemon takes the amount in hastings.
    # If no amount in hastings is given, we support an amount_sc in siacoins.
    if "amount" in request.form:
        amount = int(request.form["amount"])
    elif "amount_sc" in request.form:
        # User decimal.Decimal as float math would not give good enough precision.
        decimal.getcontext().prec = 36 # 24 decimals for hastings + 12 for SC part
        amount = int(decimal.Decimal(request.form["amount_sc"]) * H_PER_SC)
    else:
        amount = 0
    destination = request.form["destination"] if "destination" in request.form else ""
    siadata, status_code = post_to_sia('wallet/siacoins',
                                       {"amount": str(amount),
                                        "destination": destination})
    if status_code == 200:
        # siadata["transactionids"] is a list of IDs of the transactions that
        # were created when sending the coins. The last transaction contains
        # the output headed to the 'destination'.
        return jsonify(transactionids=siadata["transactionids"],
                       message="%s SC successfully sent to %s."
                               % (amount / H_PER_SC, destination)), 200
    else:
        return jsonify(siadata), status_code


@app.route("/wallet/transactions", methods=['GET'])
@set_origin()
def api_wallet_transactions():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md#markdown-header-get-wallettransactions
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    # Do something similar to |siac wallet transactions|, see
    # https://github.com/NebulousLabs/Sia/blob/master/cmd/siac/walletcmd.go#L443
    siadata, sia_status_code = get_from_sia("wallet/transactions?startheight=%s&endheight=%s" % (0, 10000000))
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code
    showsplits = bool(strtobool(request.args.get("showsplits") or "false"))
    onlyconfirmed = bool(strtobool(request.args.get("onlyconfirmed") or "false"))
    tdata = []
    alltypes = ["confirmed"]
    if not onlyconfirmed:
        alltypes.append("unconfirmed")
    for ttype in alltypes:
        for trans in siadata["{0}transactions".format(ttype)]:
            # Note that inputs into a transaction are outgoing currency and outputs
            # are incoming, actually.
            txn = {
              "type": ttype,
              "height": trans["confirmationheight"],
              "timestamp": trans["confirmationtimestamp"],
              "transactionid": trans["transactionid"],
              "incoming": {},
              "outgoing": {},
              "change": 0,
              "fundschange": 0,
            }
            for t_input in trans["inputs"]:
                if t_input["walletaddress"]:
                    # Only process the rest if the address is owned by the wallet.
                    if t_input["fundtype"] in txn["outgoing"]:
                        txn["outgoing"][t_input["fundtype"]] += int(t_input["value"])
                    else:
                        txn["outgoing"][t_input["fundtype"]] = int(t_input["value"])
                    if t_input["fundtype"].startswith("siafund"):
                        txn["fundschange"] -= int(t_input["value"])
                    else:
                        txn["change"] -= int(t_input["value"])
            for t_output in trans["outputs"]:
                if t_output["walletaddress"]:
                    # Only process the rest if the address is owned by the wallet.
                    if t_output["fundtype"] in txn["incoming"]:
                        txn["incoming"][t_output["fundtype"]] += int(t_output["value"])
                    else:
                        txn["incoming"][t_output["fundtype"]] = int(t_output["value"])
                    if t_input["fundtype"].startswith("siafund"):
                        txn["fundschange"] += int(t_output["value"])
                    else:
                        txn["change"] += int(t_output["value"])
            # Convert into data that can be put into JSON properly.
            # This also adds _sc values for anything in hastings (not siafunds).
            txndata = {
              "confirmed": txn["type"] == "confirmed",
              "height": txn["height"],
              "timestamp": txn["timestamp"],
              "transactionid": txn["transactionid"],
              "incoming": {},
              "outgoing": {},
              "incoming_sc": {},
              "outgoing_sc": {},
              "change": str(txn["change"]),
              "change_sc": txn["change"] / H_PER_SC,
              "fundschange": str(txn["fundschange"]),
            }
            for tdirection in ["outgoing", "incoming"]:
                for ftype in txn[tdirection]:
                    txndata[tdirection][ftype] = str(txn[tdirection][ftype])
                    if not ftype.startswith("siafund"):
                        txndata["%s_sc" % tdirection][ftype] = txn[tdirection][ftype] / H_PER_SC
            # Only add transaction to display if it either has an actual change or
            # we want to show splits.
            if txn["change"] or txn["fundschange"] or showsplits:
                tdata.append(txndata)
    return jsonify(tdata), sia_status_code


@app.errorhandler(404)
@set_origin()
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(message="Method not supported: %s" % str(error)), 404

@app.errorhandler(500)
@set_origin()
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(message="Internal server error: %s" % str(error)), 500


if __name__ == "__main__":
    useHost = REST_HOST
    if 'DEBUG' in environ:
        app.debug = True
        useHost = REST_HOST_DEBUG
    if 'USE_TLS' in environ:
        ssl_context = (SSL_CERT, SSL_KEY)
        useHost = REST_HOST_TLS
    else:
        ssl_context = None
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host=useHost, port=REST_PORT, ssl_context=ssl_context,
            threaded=True)
