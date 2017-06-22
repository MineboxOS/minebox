# Various tools around backups, mostly to get info about them.

from flask import current_app
from os import path, mkdir, makedirs
from glob import glob
from zipfile import ZipFile
import re
import time
import subprocess

from connecttools import getDemoURL, getFromSia, postToSia, putToMineBD
from backupinfo import *

SIA_DIR="/mnt/lower1/sia"

def check_prerequisites():
    # Step 0: Check if prerequisites are met to make backups.
    consdata, cons_status_code = getFromSia('consensus')
    if cons_status_code == 200:
        if not consdata["synced"]:
          return False, "ERROR: sia seems not to be synced. Please again when the consensus is synced."
    else:
        return False, "ERROR: sia daemon needs to be running for any uploads."
    return True, ""

def snapshot_upper():
    # TODO: See MIN-104.
    return

def create_lower_snapshots():
    # Step 1: Create snapshot.
    snapname=str(int(time.time()))
    backupname="backup.%s" % snapname

    metadir="%s/%s" % (METADATA_BASE, backupname)
    if not path.isdir(metadir):
      makedirs(metadir)

    current_app.logger.info('Trimming file system to actually remove deleted data from the virtual disk.')
    subprocess.call(['/usr/sbin/fstrim', '/mnt/storage'])
    current_app.logger.info('Flushing file system caches to make sure user data has been written.')
    subprocess.call(['/usr/bin/sync'])
    current_app.logger.info('Creating lower-level data snapshot(s) with name: %s' % snapname)
    # Potentially, we should ensure that those data/ directories are actually subvolumes.
    for subvol in glob(DATADIR_MASK):
        current_app.logger.info('subvol: %s' % subvol)
        if not path.isdir(path.join(subvol, 'snapshots')):
            makedirs(path.join(subvol, 'snapshots'))
        subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'snapshot', '-r', subvol, path.join(subvol, 'snapshots', snapname)])
    current_app.logger.info(
      'Telling MineBD to pause (for 1.5s) to make sure no modified blocks exist with the same timestamp as in our snapshots.'
    )
    mbdata, mb_status_code = putToMineBD('pause', '', [{'Content-Type': 'application/json'}, {'Accept': 'text/plain'}])
    return snapname

def initiate_uploads():
    return

def wait_for_uploads():
    return

def save_metadata():
    return

def remove_lower_snapshots():
    return

def remove_old_backups():
    return

