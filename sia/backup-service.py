#!/usr/bin/env python

# Minebox backup service. See README.md in this directory for more info.

# Uploading is a multi-step process (see README.md for details):
# 0) Check prerequisites (sia running and sync, etc.)
# 1) [not implemented] Create read-only snapshots of all subvolumes on upper layer.
# 2) Create a read-only snapshot(s) on lower disk(s).
# 3) Initiate uploads to sia where needed.
# 4) Wait for finished uploads and, save/upload the metadata.
# 5) Remove the snapshot(s).
# 6) Remove backups if they are older than the last finished and fully available one.

from flask import Flask, request, jsonify, json
from os import environ
import logging
from backuptools import *

# Define various constants.
REST_PORT=5100

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


@app.route("/trigger")
def api_trigger():
    success, errmsg = check_prerequisites()
    if not success:
        return jsonify(message=errmsg), 503
    #snapshot_upper()
    snapname = create_lower_snapshots()
    initiate_uploads()
    wait_for_uploads()
    save_metadata()
    remove_lower_snapshots()
    remove_old_backups()
    return jsonify(message="Not yet implemented."), 501


@app.route("/status")
def api_start():
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
    if 'DEBUG' in environ:
        app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host='0.0.0.0', port=REST_PORT, threaded=True)
