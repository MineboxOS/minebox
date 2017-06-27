#!/usr/bin/env python

from flask import Flask, request, jsonify, json
import os
import re
import logging
from connecttools import (getDemoURL, getFromMineBD,
                          getFromMetadata, putToMetadata, deleteFromMetadata)


# Define various constants.
REST_PORT=9900
SERVER_URI="http://localhost:8050/v1/"
SIA_DIR="/mnt/lower1/sia"

app = Flask(__name__)

@app.route("/")
def api_root():
    links = []
    for rule in app.url_map.iter_rules():
        # Flask has a default route for serving static files, let's exclude it.
        if rule.endpoint != "static":
            links.append({"url": rule.rule,
                          "methods": ','.join([x for x in rule.methods if x not in ["OPTIONS","HEAD"]])})
    return jsonify(supported_urls=sorted(links, key=lambda rule: rule["url"])), 200


@app.route("/consensus", methods=['GET'])
def api_consensus():
    return jsonify(
      synced=True,
      height=162248,
      currentblock="00000000000008a84884ba827bdc868a17ba9c14011de33ff763bd95779a9cf1",
      target=[0,0,0,0,0,0,11,48,125,79,116,89,136,74,42,27,5,14,10,31,23,53,226,238,202,219,5,204,38,32,59,165],
      difficulty=1234
    )

@app.route("/renter/files", methods=['GET'])
def api_renter_files():
    mdata, md_status_code = getFromMetadata('file/list')
    if md_status_code >= 400:
        return jsonify(mdata), md_status_code
    files = []
    for line in mdata["message"].splitlines():
        usize, unit, fpath = line.split()
        # Do not report files that do not match the .dat file pattern (e.g. backup zips).
        if re.match(r'^minebox_.+\.dat$', fpath):
            # Right now, the only unit we get from metadata service is MB.
            if unit == "MB":
                fsize = int(usize) * 2**20
            else:
                fsize = int(usize)

            files.append({
              "siapath": fpath,
              "filesize": fsize,
              "available": True,
              "renewing": True,
              "redundancy": 1,
              "uploadprogress": 100,
              "expiration": 60000
            })
    return jsonify(files=files), 200

@app.route("/renter/upload/<siapath>", methods=['POST'])
def api_renter_upload(siapath):
    filename = request.form["source"]
    # Upload the local file. Note that this reads all its data into memory.
    with open(filename) as file:
        fdata = file.read()
        mdata, md_status_code = putToMetadata("file/%s" % siapath, fdata)
        if md_status_code >= 400:
            return jsonify(mdata), md_status_code
    # Do the equivalent of a "touch <path>.sia"
    siafname = os.path.join(SIA_DIR, "renter", "%s.sia" % siapath)
    with open(siafname, 'a') as siafile:
        os.utime(siafname, None)
    return "", 204

@app.route("/renter/delete/<siapath>", methods=['POST'])
def api_renter_delete(siapath):
    mdata, md_status_code = deleteFromMetadata("file/%s" % siapath)
    if md_status_code >= 400:
        return jsonify(mdata), md_status_code
    # Delete .sia file as well.
    siafname = os.path.join(SIA_DIR, "renter", "%s.sia" % siapath)
    if os.path.isfile(siafname):
        os.remove(siafname)
    return "", 204

@app.route("/wallet", methods=['GET'])
def api_wallet():
    return jsonify(
      encrypted=True,
      unlocked=True,
      rescanning=False,
      confirmedsiacoinbalance="50000471100000000000000000000", #hastings
      unconfirmedoutgoingsiacoins="246801357900000000000000000", #hastings
      unconfirmedincomingsiacoins="1234000000000000000000000", #hastings
      siafundbalance="0", #hastings
      siacoinclaimbalance="0" #hastings
    )

@app.route("/wallet/unlock", methods=['POST'])
def api_wallet_unlock():
    return jsonify(message="Not yet implemented."), 501


@app.errorhandler(404)
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(error="Method not supported: "+ str(error)), 404

@app.errorhandler(500)
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: "+ str(error)), 500


if __name__ == "__main__":
    if 'DEBUG' in os.environ:
        app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host='0.0.0.0', port=REST_PORT, threaded=True)
