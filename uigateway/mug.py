#!/usr/bin/env python

from flask import Flask, Response, request, jsonify, json
from os import listdir
from os.path import isfile, isdir, ismount, join
from glob import glob
from zipfile import ZipFile
from urlparse import urlparse
from OpenSSL import SSL
import re
import logging
import time
import subprocess
import pwd
import urllib
import requests
app = Flask(__name__)

REST_PORT=5000
# TODO: The Rockstor certs are at a different location in production!
SSL_CERT="/root/rockstor-core_vm/certs/rockstor.cert"
SSL_KEY="/root/rockstor-core_vm/certs/rockstor.key"
DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
SIAD_URL="http://localhost:9980/"
MINEBD_URL="http://localhost:8080/v1/"
MINEBD_AUTH_KEY_FILE="/etc/minebox/local-auth.key"
MINEBD_STORAGE_PATH="/mnt/storage"
UPLOADER_CMD="/usr/lib/minebox/uploader-bg.sh"
MBKEY_CMD="/usr/lib/minebox/mbkey.sh"
H_PER_SC=1e24 # hastings per siacoin


@app.route("/")
def api_root():
    links = []
    for rule in app.url_map.iter_rules():
        # Flask has a default route for serving static files, let's exclude it.
        if rule.endpoint != "static":
            links.append({"url": rule.rule,
                          "methods": ','.join([x for x in rule.methods if x not in ["OPTIONS","HEAD"]])})
    return jsonify(supported_urls=sorted(links, key=lambda rule: rule["url"])), 200, getHeaders()


@app.route("/backup/list", methods=['GET'])
def api_backup_list():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backuplist
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    metalist = getBackupList()
    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(metalist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(metalist),  mimetype='application/json'), 200, getHeaders()


@app.route("/backup/<backupname>/status", methods=['GET'])
def api_backup_status(backupname):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backup1493807150status
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    if backupname == "last":
        backuplist = getBackupList()
        if len(backuplist):
            backupname = backuplist.pop()
    elif not re.match(r'^\d+$', backupname):
        return jsonify(error="Illegal backup name."), 400, getHeaders()

    backupstatus, status_code = getBackupStatus(backupname)

    return jsonify(backupstatus), status_code, getHeaders()


def getBackupStatus(backupname):
    backupfiles, is_finished = getBackupFiles(backupname)
    if backupfiles is None:
        return {"message": "No backup found with that name."}, 404, getHeaders()

    status_code = 200
    if len(backupfiles) < 1:
        # Before uploads are scheduled, we find a backup but no files.
        files = -1
        total_size = -1
        rel_size = -1
        progress = 0
        rel_progress = 0
        status = "PENDING"
        metadata = "PENDING"
        fully_available = False
    else:
        backuplist = getBackupList()
        currentidx = backuplist.index(backupname)
        if currentidx > 0:
            prev_backupfiles, prev_finished = getBackupFiles(backuplist[currentidx - 1])
        else:
            prev_backupfiles = None
        sia_filedata, sia_status_code = getFromSia('renter/files')
        if sia_status_code == 200:
            # create a dict generated from the JSON response.
            files = 0
            total_size = 0
            total_pct_size = 0
            rel_size = 0
            rel_pct_size = 0
            fully_available = True
            sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
            for fname in backupfiles:
                if fname in sia_map:
                    files += 1
                    fdata = sia_filedata["files"][sia_map[fname]]
                    # For now, report all files.
                    # We may want to only report files not included in previous backups.
                    total_size += fdata["filesize"]
                    total_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                    if prev_backupfiles is None or not fdata["siapath"] in prev_backupfiles:
                        rel_size += fdata["filesize"]
                        rel_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                    if not fdata["available"]:
                        fully_available = False
                elif re.match(r'.*\.dat$', fname):
                    files += 1
                    fully_available = False
                    app.logger.warn('File %s not found on Sia!', fname)
                else:
                    app.logger.debug('File "%s" not on Sia and not matching watched names.', fname)
            # If size is 0, we report 100% progress.
            # This is really needed for relative as otherwise a backup with no
            # difference to the previous would never go to 100%.
            progress = total_pct_size / total_size * 100 if total_size else 100
            rel_progress = rel_pct_size / rel_size * 100 if rel_size else 100
            # We don't upload metadata atm, so always flag it as pending.
            metadata = "PENDING"
            if is_finished and fully_available:
                status = "FINISHED"
            elif is_finished and not fully_available:
                status = "DAMAGED"
            elif total_pct_size:
                status = "UPLOADING"
            else:
                status = "PENDING"
        else:
            app.logger.error("Error %s getting Sia files: %s", status_code, str(sia_filedata))
            status_code = 503
            files = -1
            total_size = -1
            rel_size = -1
            progress = 0
            rel_progress = 0
            status = "ERROR"
            metadata = "ERROR"
            fully_available = False

    return {
      "name": backupname,
      "time_snapshot": backupname,
      "status": status,
      "metadata": metadata,
      "numFiles": files,
      "size": total_size,
      "progress": progress,
      "relative_size": rel_size,
      "relative_progress": rel_progress,
    }, status_code, getHeaders()


@app.route("/backup/all/status", methods=['GET'])
def api_backup_all_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backupallstatus
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    backuplist = getBackupList()

    statuslist = []
    for backupname in backuplist:
        backupstatus, status_code = getBackupStatus(backupname)
        statuslist.append(backupstatus)

    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(statuslist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(statuslist),  mimetype='application/json'), 200, getHeaders()


@app.route("/backup/start", methods=['POST'])
def api_backup_start():
    # Doc: *** TBD - not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    # See if the consensus is synced as we know that uploader requires that.
    siadata, sia_status_code = getFromSia('consensus')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code, getHeaders()
    if not siadata["synced"]:
        return jsonify(message="Sia consensus is not fully synced, try again later."), 503, getHeaders()
    # TBD: Make sure MineBD is not running a restore.
    # Make uploader start a new upload.
    starttime = time.time()
    subprocess.call([UPLOADER_CMD])
    # A metadata directory with the pidfile should exist very soon after starting the uploader.
    time.sleep(10)
    lastbackup = getBackupList().pop()
    if starttime < lastbackup:
        return jsonify(message="Backup started.", name=lastbackup), 200, getHeaders()
    else:
        return jsonify(message="Error starting backup."), 500, getHeaders()


@app.route("/key/status", methods=['GET'])
def api_key_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keystatus
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    return jsonify(message="Not yet implemented."), 501, getHeaders()


@app.route("/key/generate", methods=['GET'])
def api_key_generate():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keygenerate
    mbdata, mb_status_code = getFromMineBD('keys/asJson')
    # For the moment, just blindly hand over the result from MineBD.
    if isinstance(mbdata, list):
        # jsonify cannot deal with lists in Flask <0.10
        return Response(json.dumps(mbdata),  mimetype='application/json'), mb_status_code, getHeaders()
    else:
        return jsonify(mbdata), mb_status_code, getHeaders()


@app.route("/key/verify", methods=['POST'])
def api_key_verify():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-keyverify
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    return jsonify(message="Not yet implemented."), 501, getHeaders()


@app.route("/key", methods=['PUT'])
def api_key_put():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-put-key
    mbdata, mb_status_code = getFromMineBD('serialnumber')
    if mb_status_code == 200:
        return jsonify(message="File system is already encrypted, cannot set key."), 400, getHeaders()
    elif "messagesource" in mbdata and mbdata["messagesource"] == "MineBD":
        # MineBD is running but encryption is not yet set up, we can actually set a key!
        if len(request.data):
          retcode = subprocess.call([MBKEY_CMD, "set", request.data])
          if retcode == 0:
              return jsonify(message="Key set successfully"), 200, getHeaders()
          else:
              return jsonify(message="Setting key failed."), 500, getHeaders()
        else:
            return jsonify(message="No useful key handed over."), 400, getHeaders()
    else:
        return jsonify(message="The Minebox storage is not running, please reboot the box or call support!"), 503, getHeaders()


@app.route("/key", methods=['POST'])
def api_key_post():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-key
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    return jsonify(message="Not yet implemented."), 501, getHeaders()


@app.route("/consensus", methods=['GET'])
def api_consensus():
    # Doc: *** TBD - not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    siadata, status_code = getFromSia('consensus')
    # For now, just return the info from Sia directly.
    return jsonify(siadata), status_code, getHeaders()


@app.route("/contracts", methods=['GET'])
def api_contracts():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    siadata, sia_status_code = getFromSia('renter/contracts')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code, getHeaders()
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
    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(statuslist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(contractlist),  mimetype='application/json'), 200, getHeaders()


@app.route("/status", methods=['GET'])
def api_status():
    # Doc: *** TBD - not documented yet***
    username = checkLogin()
    outdata = {}
    if username:
        outdata["logged_in"] = True
        outdata["user"] = username
    else:
        outdata["logged_in"] = False
        outdata["user"] = None

    mbdata, mb_status_code = getFromMineBD('serialnumber')
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

    outdata["backup_type"] = "sia"
    consdata, cons_status_code = getFromSia('consensus')
    if cons_status_code == 200:
        outdata["sia_daemon_running"] = True
        outdata["consensus_height"] = consdata["height"]
        outdata["consensus_synced"] = consdata["synced"]
    else:
        outdata["sia_daemon_running"] = False
        outdata["consensus_height"] = None
        outdata["consensus_synced"] = None
    walletdata, wallet_status_code = getFromSia('wallet')
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
    return jsonify(outdata), 200, getHeaders()


@app.route("/wallet/status", methods=['GET'])
def api_wallet_status():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    siadata, sia_status_code = getFromSia('wallet')
    if sia_status_code >= 400:
        return jsonify(siadata), sia_status_code, getHeaders()
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
    return jsonify(walletdata), 200, getHeaders()


@app.route("/wallet/unlock", methods=['POST'])
def api_wallet_unlock():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401, getHeaders()
    # Make sure we only hand parameters to siad that it supports.
    pwd = request.form["encryptionpassword"]
    siadata, status_code = postToSia('wallet/unlock', {"encryptionpassword": pwd})
    if status_code == 204:
        # This (No Content) should be the default returned on success.
        return jsonify(message="Wallet unlocked."), 200, getHeaders()
    else:
        return jsonify(siadata), status_code, getHeaders()


def getFromSia(api):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    try:
        response = requests.get(url, headers=headers)
        if re.match(r'^application/json', response.headers['Content-Type']):
            # create a dict generated from the JSON response.
            siadata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from Sia.
                siadata["messagesource"] = "sia"
            return siadata, response.status_code
        else:
            return {"message": response.text, "messagesource": "sia"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def postToSia(api, formData):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    try:
        response = requests.post(url, data=formData, headers=headers)
        if re.match(r'^application/json', response.headers['Content-Type']):
            # create a dict generated from the JSON response.
            siadata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from Sia.
                siadata["messagesource"] = "sia"
            return siadata, response.status_code
        else:
            return {"message": response.text, "messagesource": "sia"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def getFromMineBD(api):
    url = MINEBD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    with open(MINEBD_AUTH_KEY_FILE) as f:
        local_key = f.read().rstrip()
    try:
        response = requests.get(url, auth=("user", local_key))
        if re.match(r'^application/json', response.headers['Content-Type']):
            # create a dict generated from the JSON response.
            mbdata = response.json()
            if response.status_code >= 400:
                # For error-ish codes, tell that they are from MineBD.
                mbdata["messagesource"] = "MineBD"
            return mbdata, response.status_code
        else:
            return {"message": response.text, "messagesource": "MineBD"}, response.status_code
    except requests.ConnectionError as e:
        return {"message": str(e)}, 503
    except requests.RequestException as e:
        return {"message": str(e)}, 500


def checkLogin():
    csrftoken = request.cookies.get('csrftoken')
    sessionid = request.cookies.get('sessionid')
    user_api = "https://localhost/api/commands/current-user"
    referer = "https://localhost/"

    headers = requests.utils.default_headers()
    headers.update({'X-CSRFToken': csrftoken, 'referer': referer})
    cookiejar = requests.cookies.RequestsCookieJar()
    cookiejar.set('csrftoken', csrftoken)
    cookiejar.set('sessionid', sessionid)
    try:
        # Given that we call localhost, the cert will be wrong, so don't verify.
        response = requests.post(user_api, data=[], headers=headers, cookies=cookiejar, verify=False)
        if response.status_code == 200:
          return response.json()
        else:
          app.logger.warn('No valid login found: %s' % response.text)
          return False
    except requests.exceptions.RequestException as e:
        app.logger.error('Error checking login: %s' % str(e))
        return False


def getHeaders():
    # We mainly need custom headers for CORS so XHR can actually talk to us from the UI.
    # Note that credentials only work if -Origin is not "*".
    # Use host we are running on but respect port of requsting origin, so port forwarders work.
    myurlparts = urlparse(request.url_root)
    if "Origin" in request.headers:
        originport = urlparse(request.headers["Origin"]).port
    else:
        originport = None
    if originport is None:
        origin = "https://%s" % (myurlparts.hostname)
    else:
        origin = "https://%s:%s" % (myurlparts.hostname, originport)
    return {"Access-Control-Allow-Origin": origin,
            "Access-Control-Allow-Credentials": "true",
            "Vary": "Origin"}


def getBackupList():
    backuplist = [re.sub(r'.*backup\.(\d+)(\.zip)?', r'\1', f)
                  for f in glob(join(METADATA_BASE, "backup.*"))
                    if (isfile(f) and f.endswith(".zip")) or
                       isdir(f) ]
    backuplist.sort()
    return backuplist


def getBackupFiles(backupname):
    backupfiles = None
    is_finished = None
    zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
    dirname = join(METADATA_BASE, "backup.%s" % backupname)
    if isfile(zipname):
        backupfiles = []
        is_finished = True
        with ZipFile(zipname, 'r') as backupzip:
            backupfiles = [re.sub(r'.*backup\.\d+\/(.*)\.sia$', r'\1', f)
                           for f in backupzip.namelist()]
    elif isdir(dirname):
        backupfiles = []
        is_finished = False
        flist = join(dirname, "files")
        if isfile(flist):
            with open(flist) as f:
                backupfiles = [line.rstrip('\n') for line in f]
    return backupfiles, is_finished


@app.errorhandler(404)
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(error="Method not supported: "+ str(error)), 404, getHeaders()

@app.errorhandler(500)
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: "+ str(error)), 500, getHeaders()


if __name__ == "__main__":
    #app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    # With Werkzeug 0.10+, SSL would even be easier,
    # see http://stackoverflow.com/a/28590266/682515
    # Also, using TLS 1.0 instead of TLS is not really secure!
    context = SSL.Context(SSL.TLSv1_METHOD)
    context.use_privatekey_file(SSL_KEY)
    context.use_certificate_file(SSL_CERT)
    app.run(host='0.0.0.0', port=REST_PORT, ssl_context=context, threaded=True)
