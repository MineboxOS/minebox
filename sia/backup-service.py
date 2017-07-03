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

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import Flask, request, jsonify, json
from os import environ
import time
import logging
import threading
from backuptools import *
from backupinfo import get_backups_to_restart, get_latest
from connecttools import get_from_sia

# Define various constants.
REST_PORT=5100

threadstatus = {}

app = Flask(__name__)


@app.before_first_request
def before_first_request():
    restart_backups()


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
    bthread = start_backup_thread()
    return jsonify(message="Backup started: %s." % bthread.name), 200


@app.route("/status")
def api_start():
    # This is a very temporary debug-style status output for now.
    statusdata = {"active": get_running_backups(), "all": []}
    for tname in threadstatus:
        statusdata["all"].append({
          "name": threadstatus[tname]["snapname"],
          "time_snapshot": int(threadstatus[tname]["snapname"]),
          "message": threadstatus[tname]["message"],
          "finished": threadstatus[tname]["finished"],
          "failed": threadstatus[tname]["failed"],
          "size": threadstatus[tname]["backupsize"],
          "upload_size": threadstatus[tname]["uploadsize"],
          "upload_progress": threadstatus[tname]["uploadprogress"],
        })
    return jsonify(statusdata), 200


@app.route("/ping")
def api_ping():
    # This can be called to just have the service run something.
    # For example, we need to do this early after booting to restart backups
    # if needed (via @app.before_first_request).

    # Check for synced sia consensus as a prerequisite.
    consdata, cons_status_code = get_from_sia('consensus')
    if cons_status_code == 200:
        if not consdata["synced"]:
            # Return early, we need a synced consensus to do anything.
            return "", 204
    else:
        return jsonify(message="ERROR: sia daemon is not running."), 503

    # See if sia is fully set up and do init tasks if needed.
    walletdata, wallet_status_code = get_from_sia('wallet')
    if wallet_status_code == 200:
        if not walletdata["encrypted"]:
            # We need to seed the wallet and set up allowances, etc.
            setup_sia_system()
        elif not walletdata["unlocked"]:
            # We should unlock the wallet so new contracts can be made.
            unlock_sia_wallet()

    # Trigger a backup if the latest is older than 24h.
    timenow = int(time.time())
    timelatest = int(get_latest())
    if timelatest < timenow - 24 * 3600:
        success, errmsg = check_prerequisites()
        if success:
            bthread = start_backup_thread()
    return "", 204


def start_backup_thread(snapname=None):
    bevent = threading.Event()
    bthread = threading.Thread(target=run_backup, args=(bevent,snapname))
    bthread.daemon = True
    bthread.start()
    bevent.wait() # Wait for thread being set up.
    return bthread


def run_backup(startevent, snapname=None):
    # The routes have implicit Flask application context, but the thread needs an explicit one.
    # See http://flask.pocoo.org/docs/appcontext/#creating-an-application-context
    with app.app_context():
        if not snapname:
            snapname = str(int(time.time()))
            restarted = False
            app.logger.info('Started backup run: %s', snapname)
        else:
            app.logger.info('Restarting backup run: %s', snapname)
            restarted = True
        threading.current_thread().name = 'backup.%s' % snapname
        threadstatus[threading.current_thread().name] = {
          "snapname": snapname,
          "backupname": "backup.%s" % snapname,
          "ident": threading.current_thread().ident,
          "backupfileinfo": [],
          "backupsize": None,
          "uploadsize": None,
          "uploadfiles": [],
          "uploadprogress": 0,
          "finished": False,
          "failed": False,
          "restarted": restarted,
          "message": "started",
        }
        # Tell main thread we are set up.
        startevent.set()

        # Now start the actual tasks.
        if not restarted:
            # Snapshotting is only done on newly started backups, not on restarts.
            # Create upper-level snapshots.
            snapshot_upper(threadstatus[threading.current_thread().name])
            # Create lower-level snapshot(s).
            create_lower_snapshots(threadstatus[threading.current_thread().name])
        # Start uploads.
        success, errmsg = initiate_uploads(threadstatus[threading.current_thread().name])
        if not success:
            threadstatus[threading.current_thread().name]["failed"] = True
            threadstatus[threading.current_thread().name]["message"] = errmsg
            return
        # Wait for uploads to complete.
        success, errmsg = wait_for_uploads(threadstatus[threading.current_thread().name])
        if not success:
            threadstatus[threading.current_thread().name]["failed"] = True
            threadstatus[threading.current_thread().name]["message"] = errmsg
            return
        # Create and save metadata bundle.
        success, errmsg = save_metadata(threadstatus[threading.current_thread().name])
        if not success:
            threadstatus[threading.current_thread().name]["failed"] = True
            threadstatus[threading.current_thread().name]["message"] = errmsg
            return
        # We're done, remove lower-level snapshot(s).
        remove_lower_snapshots(threadstatus[threading.current_thread().name])
        # Clean up old backups (locally and on the network).
        success, errmsg = remove_old_backups(threadstatus[threading.current_thread().name],
                                             get_running_backups())
        if not success:
            threadstatus[threading.current_thread().name]["failed"] = True
            threadstatus[threading.current_thread().name]["message"] = errmsg
            return
        threadstatus[threading.current_thread().name]["finished"] = True
        threadstatus[threading.current_thread().name]["message"] = "done"


def get_running_backups():
    return [threadstatus[thread.name]["snapname"]
            for thread in threading.enumerate()
              if thread.name in threadstatus ]


def restart_backups():
    with app.app_context():
        active_backups = get_running_backups()
        app.logger.debug('Active backups: %s', active_backups)
        for snapname in get_backups_to_restart():
            app.logger.debug('%s should be restarted...', snapname)
            if not snapname in active_backups:
                bthread = start_backup_thread(snapname)
                app.logger.debug('%s was restarted.', bthread.name)


def setup_sia_system():
    # We may start long-running tasks here so we may want to do them in their own thread.
    # We also need to make sure to not init the same process multiple times.
    # 0) Get wallet seed from MineBD (see MIN-128).
    # 1) Init the wallet with that seed.
    # 2) Unlock the wallet, using the seed as password.
    # 3) Fetch our initial allotment of siacoins from Minebox (if applicable).
    # 4) Set an allowance for renting, so that we can start uploading backups.
    # 5) Set up sia hosting (see MIN-129).
    return


def unlock_sia_wallet():
    # We may start long-running tasks here so we may want to do them in their own thread.
    # We also need to make sure to not init the same process multiple times.
    # 0) Get wallet seed from MineBD (see MIN-128).
    # 2) Unlock the wallet, using the seed as password.
    return


@app.errorhandler(404)
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(error="Method not supported: %s" % error), 404

@app.errorhandler(500)
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: %s" % error), 500


if __name__ == "__main__":
    if 'DEBUG' in environ:
        app.debug = True
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host='0.0.0.0', port=REST_PORT, threaded=True)
