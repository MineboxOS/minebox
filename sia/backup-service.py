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
import time
import logging
import threading
from backuptools import *
from backupinfo import get_backups_to_restart, get_latest

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
    bevent = threading.Event()
    bthread = threading.Thread(target=run_backup, args=(bevent,))
    bthread.daemon = True
    bthread.start()
    bevent.wait() # Wait for thread being set up.
    return jsonify(message="Backup started: %s." % bthread.name), 200


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


@app.route("/status")
def api_start():
    # This is a very temporary debug-style status output for now.
    statusdata = {"threads": [], "backups": []}
    for thread in threading.enumerate():
        statusdata["threads"].append(thread.name)
        app.logger.debug('Found thread: %s', thread.name)
    for tname in threadstatus:
        statusdata["backups"].append(threadstatus[tname])
    return jsonify(statusdata), 200


@app.route("/ping")
def api_ping():
    # This can be called to just have the service run something.
    # For example, we need to do this early after booting to restart backups
    # if needed (via @app.before_first_request).
    # Also, trigger a backup if the latest is older than 24h.
    timenow = int(time.time())
    timelatest = int(get_latest())
    if timelatest < timenow - 24 * 3600:
        success, errmsg = check_prerequisites()
        if success:
            bevent = threading.Event()
            bthread = threading.Thread(target=run_backup, args=(bevent,))
            bthread.daemon = True
            bthread.start()
            bevent.wait() # Wait for thread being set up.
    return "", 204


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
                bevent = threading.Event()
                bthread = threading.Thread(target=run_backup, args=(bevent,snapname))
                bthread.daemon = True
                bthread.start()
                bevent.wait() # Wait for thread being set up.
                app.logger.debug('%s was restarted.', snapname)


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
