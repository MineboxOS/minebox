#!/usr/bin/env python

from flask import Flask, Response, request, jsonify, json
from os import listdir
from os.path import isfile, isdir, join
from glob import glob
from zipfile import ZipFile
import re
import logging
import urllib
import requests
app = Flask(__name__)

REST_PORT=5000
DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
SIAD_URL="http://localhost:9980/"


@app.route("/")
def api_root():
    links = []
    for rule in app.url_map.iter_rules():
        # Flask has a default route for serving static files, let's exclude it.
        if rule.endpoint != "static":
            links.append({"url": rule.rule, "methods": ','.join(rule.methods)})
    return jsonify(supported_urls=sorted(links, key=lambda rule: rule["url"]))


@app.route("/backup/list", methods=['GET'])
def api_backup_list():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backuplist
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    metalist = getBackupList()
    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(metalist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(metalist),  mimetype='application/json')


@app.route("/backup/<backupname>/status", methods=['GET'])
def api_backup_status(backupname):
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backup1493807150status
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    if backupname == "last":
        backuplist = getBackupList()
        if len(backuplist):
            backupname = backuplist.pop()
    elif not re.match(r'^\d+$', backupname):
        return jsonify(error="Illegal backup name."), 400

    backupstatus, status_code = getBackupStatus(backupname)

    return jsonify(backupstatus), status_code


def getBackupStatus(backupname):
    backupfiles, is_finished = getBackupFiles(backupname)
    if backupfiles is None:
        return {"message": "No backup found with that name."}, 404

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
    }, status_code


@app.route("/backup/all/status", methods=['GET'])
def api_backup_all_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-backupallstatus
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    backuplist = getBackupList()

    statuslist = []
    for backupname in backuplist:
        backupstatus, status_code = getBackupStatus(backupname)
        statuslist.append(backupstatus)

    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(statuslist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(statuslist),  mimetype='application/json')


@app.route("/key/status", methods=['GET'])
def api_key_status():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keystatus
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key/generate", methods=['GET'])
def api_key_generate():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-get-keygenerate
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key/verify", methods=['POST'])
def api_key_verify():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-keyverify
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key", methods=['PUT'])
def api_key_put():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-put-key
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/key", methods=['POST'])
def api_key_post():
    # Doc: https://bitbucket.org/mineboxgmbh/minebox-client-tools/src/master/doc/mb-ui-gateway-funktionen-skizze.md#markdown-header-post-key
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    return jsonify(message="Not yet implemented."), 501


@app.route("/contracts", methods=['GET'])
def api_contracts():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, status_code = getFromSia('renter/contracts')
    # For now, just return the info from Sia directly.
    return jsonify(siadata), status_code


@app.route("/wallet/status", methods=['GET'])
def api_wallet_status():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    walletdata, status_code = getFromSia('wallet')
    # For now, just return the info from Sia directly.
    return jsonify(walletdata), status_code


@app.route("/wallet/unlock", methods=['POST'])
def api_wallet_unlock():
    # Doc: *** not documented yet***
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    # Make sure we only hand parameters to siad that it supports.
    pwd = request.form["encryptionpassword"]
    siadata, status_code = postToSia('wallet/unlock', {"encryptionpassword": pwd})
    if status_code == 204:
        # This (No Content) should be the default returned on success.
        return jsonify(message="Wallet unlocked.")
    else:
        return jsonify(siadata), status_code


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
          return True
        else:
          app.logger.warn('No valid login found: %s' % response.text)
          return False
    except requests.exceptions.RequestException as e:
        app.logger.error('Error checking login: %s' % str(e))
        return False


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
    return jsonify(error="Method not supported: "+ str(error)), 404

@app.errorhandler(500)
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: "+ str(error)), 500


if __name__ == "__main__":
    #app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host='0.0.0.0', port=REST_PORT)
