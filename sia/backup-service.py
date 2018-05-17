#!/usr/bin/env python

# Minebox backup service. See README.md in this directory for more info.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import Flask, request, jsonify, json
from os import environ
import os
import time
import logging
import threading
from backuptools import *
from siatools import *
from backupinfo import get_backups_to_restart, get_latest, get_list, is_finished
from systemtools import (MACHINE_AUTH_FILE, submit_machine_auth,
                         submit_ip_notification, system_maintenance,
                         get_btrfs_subvolumes, create_btrfs_subvolume,
                         delete_btrfs_subvolume)
from connecttools import get_from_sia

# Define various constants.
REST_HOST="127.0.0.1"
REST_HOST_DEBUG="0.0.0.0"
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
    success, errmsg = check_backup_prerequisites()
    if not success:
        return jsonify(message=errmsg), 503
    bthread = start_backup_thread()
    return jsonify(message="Backup started: %s." % bthread.name,
                   name=threadstatus[bthread.name]["snapname"]), 200


@app.route("/ip_notify")
def api_ip_notify():
    mbdata, mb_status_code = get_from_minebd('status')
    if mb_status_code >= 400:
        app.logger.error("Error %s from MineBD: %s",
                         mb_status_code, mbdata["message"])
        return jsonify(message=mbdata["message"]), 503
    if mbdata["hasEncryptionKey"]:
        # If we have an encryption key, we don't need to notify any more.
        app.logger.info("System is set up, not sending an IP notification.")
        return "", 204
    success, errmsg = submit_ip_notification()
    if not success:
        app.logger.error("Error submitting IP notification: %s", errmsg)
        return jsonify(message=errmsg), 503
    app.logger.info("IP notification sent.")
    return jsonify(message="IP Notification sent."), 200


@app.route("/status")
def api_start():
    # This status output is mainly thought for MUG who can forward a slice to UI.
    statusdata = {"backup_active": get_running_backups(),
                  "backup_info": [],
                  "helper_active": get_running_helpers()}
    for tname in threadstatus:
        statusdata["backup_info"].append({
          "name": threadstatus[tname]["snapname"],
          "time_snapshot": int(threadstatus[tname]["snapname"]),
          "time_start_step": int(threadstatus[tname]["starttime_step"]),
          "step": threadstatus[tname]["step"],
          "message": threadstatus[tname]["message"],
          "finished": threadstatus[tname]["finished"],
          "failed": threadstatus[tname]["failed"],
          "size": threadstatus[tname]["backupsize"],
          "filecount": len(threadstatus[tname]["backupfiles"]),
          "upload_size": threadstatus[tname]["uploadsize"],
          "upload_progress": threadstatus[tname]["uploadprogress"],
          "total_progress": threadstatus[tname]["totalprogress"],
          "min_redundancy": threadstatus[tname]["min_redundancy"],
          "earliest_expiration": threadstatus[tname]["earliest_expiration"],
          "fully_available": threadstatus[tname]["available"],
          "metadata_uploaded": threadstatus[tname]["metadata_uploaded"],
        })
    return jsonify(statusdata), 200


@app.route("/storage/shares", methods=['GET'])
def api_storage_shares():
    # To be called/forwarded by MUG
    subvols = get_btrfs_subvolumes(MINEBD_STORAGE_PATH)
    shares = []
    for subvol in subvols:
        if subvol["parent_uuid"] == "-":
            shares.append(subvol["path"])
    return jsonify(shares), 200


@app.route("/storage/shares/add/<share>", methods=['POST'])
def api_storage_shares_add(share):
    # To be called/forwarded by MUG
    subvols = get_btrfs_subvolumes(MINEBD_STORAGE_PATH)
    share_found = False
    for subvol in subvols:
        if subvol["path"] == share and subvol["parent_uuid"] == "-":
            share_found = True
    if share_found:
        return jsonify(message="Share does already exist, cannot be created twice."), 409
    create_btrfs_subvolume(os.path.join(MINEBD_STORAGE_PATH, share))
    return "", 204


@app.route("/storage/shares/delete/<share>", methods=['POST'])
def api_storage_shares_delete(share):
    # To be called/forwarded by MUG
    subvols = get_btrfs_subvolumes(MINEBD_STORAGE_PATH)
    share_found = False
    for subvol in subvols:
        if subvol["path"] == share and subvol["parent_uuid"] == "-":
            share_found = True
    if not share_found:
        return jsonify(message="Share does not exist."), 404
    delete_btrfs_subvolume(os.path.join(MINEBD_STORAGE_PATH, share))
    return "", 204


@app.route("/ping")
def api_ping():
    # This can be called to just have the service run something.
    # For example, we need to do this early after booting to restart backups
    # if needed (via @app.before_first_request).

    if not os.path.isfile(MACHINE_AUTH_FILE):
        app.logger.info("Submit machine authentication to Minebox admin service.")
        success, errmsg = submit_machine_auth()
        if not success:
            app.logger.error(errmsg)

    # Look if we need to run some system maintenance tasks.
    # Do this here so it runs even if Sia and upper storage are down.
    # Note that in the case of updates being available for backup-service,
    # this results in a restart and the rest of the ping will not be executed.
    success, errmsg = system_maintenance()
    if not success:
        app.logger.error(errmsg)

    # Check for synced sia consensus as a prerequisite to everything else.
    success, errmsg = check_sia_sync()
    if not success:
        # Return early, we need a synced consensus to do anything.
        app.logger.debug(errmsg)
        app.logger.info("Exiting because sia is not ready, let's check again on next ping.")
        return "", 204

    if not os.path.ismount(MINEBD_STORAGE_PATH):
        current_app.logger.info("Upper storage is not mounted (yet), let's check again on next ping.")
        return "", 204

    # See if sia is fully set up and do init tasks if needed.
    # Setting up hosting is the last step, so if that is not active, we still
    # need to do something.
    walletdata, wallet_status_code = get_from_sia('wallet')
    hostdata, host_status_code = get_from_sia('host')
    if wallet_status_code == 200 and host_status_code == 200:
        if not hostdata["internalsettings"]["acceptingcontracts"]:
            # We need to seed the wallet, set up allowances and hosting, etc.
            setup_sia_system(walletdata, hostdata)
        elif not walletdata["unlocked"]:
            # We should unlock the wallet so new contracts can be made.
            unlock_sia_wallet()

    # Trigger a backup if the latest is older than 24h.
    timenow = int(time.time())
    latestbackup = get_latest()
    timelatest = int(latestbackup) if latestbackup else 0
    if timelatest < timenow - 24 * 3600:
        success, errmsg = check_backup_prerequisites()
        if success:
            bthread = start_backup_thread()

    # If no backup is active but the most recent one is not finished,
    # perform a restart of backups.
    active_backups = get_running_backups()
    if not active_backups:
        snapname = get_latest()
        if snapname:
            if not is_finished(snapname):
                restart_backups()
    else:
        # If the upload step is stuck (taking longer than 30 minutes),
        # we should restart the sia service.
        # See https://github.com/NebulousLabs/Sia/issues/1605
        for tname in threadstatus:
            if (threadstatus[tname]["snapname"] in active_backups
                and threadstatus[tname]["step"] == "initiate uploads"
                and threadstatus[tname]["starttime_step"] < time.time() - 30 * 60):
                # This would return True for success but already logs errors.
                restart_sia()
        # If the list of unfinished backups is significantly larger than active
        # backups, we very probably have quite a few backups hanging around
        # that we need to cleanup but don't get to routine cleanup (which
        # happens only when a backup finishes).
        unfinished_backups = get_list()
        if len(unfinished_backups) > len(active_backups) + 3:
            app.logger.info("We have %s unfinished backups but only %s active ones, let's clean up."
                            % (len(unfinished_backups), len(active_backups)))
            start_cleanup_thread()

    # Update Sia config if more than 10% off.
    update_sia_config()

    # See if we need to rebalance the disk space.
    rebalance_diskspace()

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
          "backupfiles": [],
          "uploadsize": None,
          "uploadfiles": [],
          "uploadprogress": 0,
          "totalprogress": 0,
          "min_redundancy": None,
          "earliest_expiration": None,
          "starttime_thread": time.time(),
          "starttime_step": time.time(),
          "available": False,
          "metadata_uploaded": False,
          "finished": False,
          "failed": False,
          "restarted": restarted,
          "step": "init",
          "message": "started",
        }
        # Tell main thread we are set up.
        startevent.set()

        # Doing backups is a multi-step process (see README.md for details):
        # 0) Check prerequisites (sia running and sync, etc.) - done outside the thread.
        # 1) Create read-only snapshots of all subvolumes on upper layer.
        # 2) Create a read-only snapshot(s) on lower disk(s).
        # 3) Initiate uploads to sia where needed.
        # 4) Wait for finished uploads and, save/upload the metadata.
        # 5) Remove the snapshot(s).
        # 6) Remove backups if they are older than the last finished and fully available one.

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
        threadstatus[threading.current_thread().name]["step"] = "complete"
        threadstatus[threading.current_thread().name]["starttime_step"] = time.time()


def start_cleanup_thread():
    cevent = threading.Event()
    cthread = threading.Thread(target=run_cleanup, args=(cevent,))
    cthread.daemon = True
    cthread.start()
    cevent.wait() # Wait for thread being set up.
    return cthread


def run_cleanup(startevent):
    # The routes have implicit Flask application context, but the thread needs an explicit one.
    # See http://flask.pocoo.org/docs/appcontext/#creating-an-application-context
    with app.app_context():
        threading.current_thread().name = 'cleanup.backups'
        # Tell main thread we are set up.
        startevent.set()
        # Clean up old backups (locally and on the network).
        remove_old_backups({}, get_running_backups())


def get_running_backups():
    return [threadstatus[thread.name]["snapname"]
            for thread in threading.enumerate()
              if thread.name in threadstatus ]


def get_running_helpers():
    return [thread.name
            for thread in threading.enumerate()
              if (thread.name.startswith("sia.")
                  or thread.name.startswith("cleanup.")) ]


def restart_backups():
    with app.app_context():
        success, errmsg = check_backup_prerequisites()
        if not success:
            app.logger.error('Prerequisites not met, not restarting any backups.')
            return
        active_backups = get_running_backups()
        app.logger.debug('Active backups: %s', active_backups)
        for snapname in get_backups_to_restart():
            app.logger.debug('%s should be restarted...', snapname)
            if not snapname in active_backups:
                bthread = start_backup_thread(snapname)
                app.logger.debug('%s was restarted.', bthread.name)


def setup_sia_system(walletdata, hostdata):
    # We may start long-running tasks here so we do them in their own thread.
    # We also need to make sure to not init the same process multiple times.
    if [thread.name for thread in threading.enumerate()
          if thread.name.startswith("sia.") ]:
        # Some kind of sia thread is running, return early.
        return None

    sevent = threading.Event()
    sthread = threading.Thread(target=run_sia_setup, args=(sevent, walletdata, hostdata))
    sthread.daemon = True
    sthread.start()
    sevent.wait() # Wait for thread being set up.
    return sthread.name


def run_sia_setup(startevent, walletdata, hostdata):
    # The routes have implicit Flask application context, but the thread needs an explicit one.
    # See http://flask.pocoo.org/docs/appcontext/#creating-an-application-context
    with app.app_context():
        threading.current_thread().name = "sia.setup"
        # Tell main thread we are set up.
        startevent.set()
        # Do the initial setup of the sia system, so uploading and hosting files works.
        # 0) Check if sia is running and consensus in sync.
        # If wallet is not unlocked:
        #   1) Get wallet seed from MineBD.
        #   2) If the wallet is not encrypted yet, init the wallet with that seed.
        #   3) Unlock the wallet, using the seed as password.
        # 4) Fetch our initial allotment of siacoins from Minebox (if applicable).
        # 5) Set an allowance for renting, so that we can start uploading backups.
        # 6) Set up sia hosting.
        success, errmsg = check_sia_sync()
        if not success:
            app.logger.error(errmsg)
            app.logger.info("Exiting sia setup because sia is not ready, will try again on next ping.")
            return
        if not walletdata["unlocked"]:
            seed = get_seed()
            if not seed:
                app.logger.error("Did not get a useful seed, cannot initialize the sia wallet.")
                return
            if not walletdata["encrypted"]:
                if not init_wallet(seed):
                    return
            if not unlock_wallet(seed):
                return
        if (walletdata["confirmedsiacoinbalance"] == "0"
              and walletdata["unconfirmedoutgoingsiacoins"] == "0"
              and walletdata["unconfirmedincomingsiacoins"] == "0"):
            # We have an empty wallet, let's try to fetch some siacoins.
            fetch_siacoins()
            # If we succeeded, we need to wait for the coins to arrive,
            # and if we failed, we have no balance and can't set an allowance
            # or host files, so in any case, we return here.
            return
        renterdata, renter_status_code = get_from_sia('renter')
        if renter_status_code == 200 and renterdata["settings"]["allowance"]["funds"] == "0":
            # No allowance, let's set one.
            if not set_allowance():
                return
        if not hostdata["internalsettings"]["acceptingcontracts"]:
            set_up_hosting()


def unlock_sia_wallet():
    # We may start long-running tasks here so we do them in their own thread.
    # We also need to make sure to not init the same process multiple times.
    if [thread.name for thread in threading.enumerate()
          if thread.name.startswith("sia.") ]:
        # Some kind of sia thread is running, return early.
        return None

    sevent = threading.Event()
    sthread = threading.Thread(target=run_wallet_unlock, args=(sevent,))
    sthread.daemon = True
    sthread.start()
    sevent.wait() # Wait for thread being set up.
    return sthread.name


def run_wallet_unlock(startevent):
    # The routes have implicit Flask application context, but the thread needs an explicit one.
    # See http://flask.pocoo.org/docs/appcontext/#creating-an-application-context
    with app.app_context():
        threading.current_thread().name = "sia.wallet-unlock"
        # Tell main thread we are set up.
        startevent.set()
        # Do the initial setup of the sia system, so uploading and hosting files works.
        # 0) Check if sia is running and consensus in sync.
        # 1) Get wallet seed from MineBD.
        # 2) Unlock the wallet, using the seed as password.
        success, errmsg = check_sia_sync()
        if not success:
            app.logger.error(errmsg)
            app.logger.info("Exiting wallet unlock because sia is not ready, will try again on next ping.")
            return
        seed = get_seed()
        if not seed:
            app.logger.error("Did not get a useful seed, cannot unlock the sia wallet.")
            return
        unlock_wallet(seed) # No need to catch a failure here.


@app.errorhandler(404)
def page_not_found(error):
    app.logger.error('Method not found: %s' % request.url)
    return jsonify(error="Method not supported: %s" % error), 404

@app.errorhandler(500)
def page_not_found(error):
    app.logger.error('Internal server error @ %s %s' % (request.url , str(error)))
    return jsonify(error="Internal server error: %s" % error), 500


if __name__ == "__main__":
    useHost = REST_HOST
    if 'DEBUG' in environ:
        app.debug = True
        useHost = REST_HOST_DEBUG
    if not app.debug:
        # In production mode, add log handler to sys.stderr.
        app.logger.addHandler(logging.StreamHandler())
        app.logger.setLevel(logging.INFO)
    app.run(host=useHost, port=REST_PORT, threaded=True)
