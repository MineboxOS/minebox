#!/usr/bin/env python

from flask import Flask, Response, request, jsonify, json
from os import listdir
from os.path import isfile, isdir, join
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
    return jsonify(
      info="Hello World!"
    )


@app.route("/backup/list", methods=['GET'])
def api_backup_list():
    metalist = [re.sub(r'backup\.(\d+)(\.zip)?', r'\1', f)
                for f in listdir(METADATA_BASE)
                  if f.startswith("backup.") and
                     ((isfile(join(METADATA_BASE, f)) and f.endswith(".zip")) or
                      isdir(join(METADATA_BASE, f))) ]
    # Does not work in Flask 0.10 and lower, see http://flask.pocoo.org/docs/0.10/security/#json-security
    #return jsonify(metalist)
    # Work around that so it works even in 0.10.
    return Response(json.dumps(metalist),  mimetype='application/json')


@app.route("/backup/<backupname>/status", methods=['GET'])
def api_backup_status(backupname):
    if not re.match(r'^\d+$', backupname):
        return jsonify(error="Illegal backup name."), 400
    if isfile(join(METADATA_BASE, "backup.%s.zip" % backupname)):
        progress=100
        status="FINISHED"
    elif isdir(join(METADATA_BASE, "backup.%s" % backupname)):
        progress=0
        status="UPLOADING|PENDING|ERROR"
    else:
        return jsonify(error="No backup known with that name."), 400

    return jsonify(
      progress=progress,
      status=status,
      metadata="TBD",
      numFiles="TBD"
    )


@app.route("/wallet/status", methods=['GET'])
def api_wallet_status():
    response = getFromSia('wallet')
    if response.status_code == 200:
      # return a dict generated from the JSON response.
      walletdata = response.json()
      # For now, just return the info from Sia directly.
      return jsonify(walletdata)
    elif re.match(r'^application/json', response.headers['Content-Type']):
      # return a dict generated from the JSON response.
      siadata = response.json()
      siadata["messagesource"] = "sia"
      # For now, just return the info from Sia directly.
      return jsonify(siadata), response.status_code
    else:
      return jsonify(error=response.text,messagesource="sia"), response.status_code

@app.route("/wallet/unlock", methods=['POST'])
def api_wallet_unlock():
    # Make sure we only hand parameters to siad that it supports.
    pwd = request.form["encryptionpassword"]
    response = postToSia('wallet/unlock', {"encryptionpassword": pwd})
    if response.status_code == 204:
      # This (No Content) should be the default returned on success.
      return jsonify(message="Wallet unlocked.")
    elif re.match(r'^application/json', response.headers['Content-Type']):
      # return a dict generated from the JSON response.
      siadata = response.json()
      siadata["messagesource"] = "sia"
      # For now, just return the info from Sia directly.
      return jsonify(siadata), response.status_code
    else:
      return jsonify(error=response.text,messagesource="sia"), response.status_code


def getFromSia(api):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    response = requests.get(url, headers=headers)
    return response

def postToSia(api, formData):
    url = SIAD_URL + api
    # siad requires a specific UA header, so add that to defaults.
    headers = requests.utils.default_headers()
    headers.update({'User-Agent': 'Sia-Agent'})
    response = requests.post(url, data=formData, headers=headers)
    return response


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
