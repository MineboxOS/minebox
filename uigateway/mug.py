#!/usr/bin/env python

from flask import Flask, Response, request, jsonify, json
from os import listdir
from os.path import isfile, isdir, join
from glob import glob
from zipfile import ZipFile
import re
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
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    metalist = [re.sub(r'.*backup\.(\d+)(\.zip)?', r'\1', f)
                for f in glob(join(METADATA_BASE, "backup.*"))
                  if (isfile(join(METADATA_BASE, f)) and f.endswith(".zip")) or
                     isdir(join(METADATA_BASE, f)) ]
    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(metalist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(metalist),  mimetype='application/json')


@app.route("/backup/<backupname>/status", methods=['GET'])
def api_backup_status(backupname):
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    if not re.match(r'^\d+$', backupname):
        return jsonify(error="Illegal backup name."), 400
    zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
    dirname = join(METADATA_BASE, "backup.%s" % backupname)
    if isfile(zipname) or isdir(dirname):
        backupfiles = None
        if isfile(zipname):
            with ZipFile(zipname, 'r') as backupzip:
                backupfiles = [re.sub(r'.*backup\.\d+\/(.*)\.sia$', r'\1', f)
                               for f in backupzip.namelist()]
        elif isdir(dirname):
            flist = join(dirname, "files")
            if isfile(flist):
                with open(flist) as f:
                    backupfiles = [line.rstrip('\n') for line in f]

        if backupfiles is not None:
            filedata, status_code = getFromSia('renter/files')
            if status_code == 200:
                # create a dict generated from the JSON response.
                files = 0
                total_size = 0
                pct_size = 0
                fully_available = True
                for file in filedata["files"]:
                    if file["siapath"] in backupfiles:
                        # For now, report all files.
                        # We may want to only report files not included in previous backups.
                        files += 1
                        total_size += file["filesize"]
                        pct_size += file["filesize"] * file["uploadprogress"] / 100
                        if not file["available"]:
                            fully_available = False
                progress = pct_size / total_size * 100 if total_size else 0
                if isfile(zipname) and fully_available:
                    status = "FINISHED"
                elif pct_size:
                    status = "UPLOADING"
                else:
                    status = "PENDING"
            else:
                files = -1
                total_size = -1
                progress = 0
                status = "ERROR"
                fully_available = False
        else:
            files = -1
            total_size = -1
            progress = 0
            status = "PENDING"
            fully_available = False
    else:
        return jsonify(message="No backup known with that name."), 400

    return jsonify(
      progress=progress,
      status=status,
      metadata="TBD",
      numFiles=files,
      size=total_size
    )


@app.route("/contracts", methods=['GET'])
def api_contracts():
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    siadata, status_code = getFromSia('renter/contracts')
    # For now, just return the info from Sia directly.
    return jsonify(siadata), status_code


@app.route("/wallet/status", methods=['GET'])
def api_wallet_status():
    if not checkLogin():
        return jsonify(message="Unauthorized access, please log into the main UI."), 401
    walletdata, status_code = getFromSia('wallet')
    # For now, just return the info from Sia directly.
    return jsonify(walletdata), status_code


@app.route("/wallet/unlock", methods=['POST'])
def api_wallet_unlock():
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
    except requests.exceptions.RequestException as e:
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
    except requests.exceptions.RequestException as e:
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
        app.logger.error('Error chhecking login: %s' % str(e))
        return False


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
    app.run(host='0.0.0.0', port=REST_PORT)
