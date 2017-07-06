#!/usr/bin/env python

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import Flask, request, jsonify, json
from os.path import ismount, isfile
from os import environ
import re
import logging
import time
import subprocess
import pwd
import backupinfo
from connecttools import (set_origin, check_login, get_demo_url,
                          get_from_sia, post_to_sia, get_from_minebd,
                          get_from_backupservice)
from siatools import H_PER_SC, SEC_PER_BLOCK


# Define various constants.
REST_PORT=5000
CONFIG_JSON_PATH="/etc/minebox/mug_config.json"
# TODO: The Rockstor certs are at a different location in production!
SSL_CERT="/root/rockstor-core_vm/certs/rockstor.cert"
SSL_KEY="/root/rockstor-core_vm/certs/rockstor.key"
MINEBD_STORAGE_PATH="/mnt/storage"
UPLOADER_CMD=backupinfo.UPLOADER_CMD
DEMOSIAC_CMD="/root/minebox-client-tools_vm/sia/demosiac.sh"
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
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backuplist
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    metalist = backupinfo.get_list(True)
    return jsonify(metalist), 200


@app.route("/backup/<backupname>/status", methods=['GET'])
@set_origin()
def api_backup_status(backupname):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backup1493807150status
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    if backupname == "last":
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
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backupallstatus
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
    # Doc: *** TBD - not documented yet***
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    bsdata, bs_status_code = get_from_backupservice('trigger')
    return jsonify(bsdata), bs_status_code


@app.route("/key/status", methods=['GET'])
@set_origin()
def api_key_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keystatus
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key/generate", methods=['GET'])
@set_origin()
def api_key_generate():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keygenerate
    mbdata, mb_status_code = get_from_minebd('keys/asJson')
    # For the moment, just blindly hand over the result from MineBD.
    return jsonify(mbdata), mb_status_code


@app.route("/key/verify", methods=['POST'])
@set_origin()
def api_key_verify():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-keyverify
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key", methods=['PUT'])
@set_origin()
def api_key_put():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-put-key
    mbdata, mb_status_code = get_from_minebd('serialnumber')
    if mb_status_code == 200:
        return jsonify(message="File system is already encrypted, cannot set key."), 400
    elif "messagesource" in mbdata and mbdata["messagesource"] == "MineBD":
        # MineBD is running but encryption is not yet set up, we can actually set a key!
        if len(request.data):
          retcode = subprocess.call([MBKEY_CMD, "set", request.data])
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
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-key
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/consensus", methods=['GET'])
@set_origin()
def api_consensus():
    # Doc: *** TBD - not documented yet***
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, status_code = get_from_sia('consensus')
    # For now, just return the info from Sia directly.
    return jsonify(siadata), status_code


@app.route("/contracts", methods=['GET'])
@set_origin()
def api_contracts():
    # Doc: *** not documented yet***
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
    # Doc: *** not documented yet***
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
    # Doc: *** TBD - not documented yet***
    username = check_login()
    outdata = {}
    if username:
        outdata["logged_in"] = True
        outdata["user"] = username
    else:
        outdata["logged_in"] = False
        outdata["user"] = None

    mbdata, mb_status_code = get_from_minebd('serialnumber')
    if mb_status_code == 200:
        outdata["minebd_running"] = True
        outdata["minebd_encrypted"] = True
        outdata["minebd_storage_mounted"] = ismount(MINEBD_STORAGE_PATH)
        if username:
            outdata["minebd_serialnumber"] = mbdata["message"]
        else:
            outdata["minebd_serialnumber"] = None
    elif "messagesource" in mbdata and mbdata["messagesource"] == "MineBD":
        outdata["minebd_running"] = True
        outdata["minebd_encrypted"] = False
        outdata["minebd_storage_mounted"] = False
        outdata["minebd_serialnumber"] = None
    else:
        outdata["minebd_running"] = False
        outdata["minebd_encrypted"] = None
        outdata["minebd_storage_mounted"] = False
        outdata["minebd_serialnumber"] = None

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


@app.route("/wallet/status", methods=['GET'])
@set_origin()
def api_wallet_status():
    # Doc: *** not documented yet***
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
    # For now, just return the info from Sia directly.
    return jsonify(walletdata), 200


@app.route("/wallet/unlock", methods=['POST'])
@set_origin()
def api_wallet_unlock():
    # Doc: *** not documented yet***
    if not check_login():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    # Make sure we only hand parameters to siad that it supports.
    pwd = request.form["encryptionpassword"]
    siadata, status_code = post_to_sia('wallet/unlock', {"encryptionpassword": pwd})
    if status_code == 204:
        # This (No Content) should be the default returned on success.
        return jsonify(message="Wallet unlocked."), 200
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
    if 'DEBUG' in environ:
        app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host='0.0.0.0', port=REST_PORT, ssl_context=(SSL_CERT, SSL_KEY),
            threaded=True)
