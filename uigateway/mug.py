#!/usr/bin/env python

# The Minebox UI Gateway (MUG)
# API Docs: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-api.md


from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import Flask, request, jsonify, json
from os.path import ismount, isfile
from os import environ
import socket
import errno
import re
import logging
import time
import subprocess
import pwd
import decimal
import backupinfo
from connecttools import (set_origin, check_login, get_demo_url,
                          get_from_sia, post_to_sia, get_from_minebd,
                          get_from_backupservice)
from siatools import H_PER_SC, SEC_PER_BLOCK, estimate_current_height


# Define various constants.
REST_HOST="127.0.0.1"
REST_HOST_DEBUG="0.0.0.0"
REST_HOST_TLS="0.0.0.0"
REST_PORT=5000
CONFIG_JSON_PATH="/etc/minebox/mug_config.json"
SSL_CERT="/opt/rockstor/certs/rockstor.cert"
SSL_KEY="/opt/rockstor/certs/rockstor.key"
MINEBD_STORAGE_PATH="/mnt/storage"
DEMOSIAC_CMD="/root/minebox-client-tools_vm/sia/demosiac.sh"
SUDO="/usr/bin/sudo"
MBKEY_CMD="/usr/lib/minebox/mbkey.sh"

config = {}

app = Flask(__name__)


@app.before_first_request
def before_first_request():
    global config
    # Read configuration from JSON file.
    if isfile(CONFIG_JSON_PATH):
        with open(CONFIG_JSON_PATH) as json_file:
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
          "data_size": contract["size"],
          "height_end": contract["endheight"],
        })
    return jsonify(contractlist), 200


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
    endheight = int(consensus_height - offset)
    startheight = int(endheight - blocks_per_day)
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
        outdata["restore_running"] = mbdata["restoreRunning"]
        outdata["restore_progress"] = mbdata["completedRestorePercent"]
    else:
        outdata["minebd_running"] = False
        outdata["minebd_encrypted"] = None
        outdata["minebd_storage_mounted"] = False
        outdata["restore_running"] = False
        outdata["restore_progress"] = None

    hasusers = False
    for user in pwd.getpwall():
        if user.pw_uid >= 1000 and user.pw_uid < 65500 and user.pw_name != "sia":
            hasusers = True
    outdata["users_created"] = hasusers

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


@app.errorhandler(404)
@set_origin()
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(error="Method not supported: "+ str(error)), 404

@app.errorhandler(500)
@set_origin()
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: "+ str(error)), 500


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
    try:
        app.run(host=useHost, port=REST_PORT, ssl_context=ssl_context,
                threaded=True)
    except socket.error as e:
        if e.errno != errno.EPIPE:
            # Not a broken pipe, throw the exception
            raise
        app.logger.warn("Encountered a broken pipe, the connection was probably terminated prematurely.")
