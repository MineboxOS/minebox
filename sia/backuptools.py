# Various tools around backups, mostly to get info about them.

from flask import current_app, json
from os import path, stat, mkdir, makedirs, remove
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
    snapname = str(int(time.time()))
    backupname = "backup.%s" % snapname

    metadir = path.join(METADATA_BASE, backupname)
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

def initiate_uploads(snapname):
    current_app.logger.info('Starting uploads.')
    backupname = "backup.%s" % snapname
    metadir = path.join(METADATA_BASE, backupname)
    bfinfo_path = path.join(metadir, 'fileinfo')
    if path.isfile(bfinfo_path):
        remove(bfinfo_path)
    sia_filedata, sia_status_code = getFromSia('renter/files')
    if sia_status_code == 200:
        siafiles = sia_filedata["files"]
    else:
        return False, "ERROR: sia daemon needs to be running for any uploads."

    # We have a randomly named subdirectory containing the .dat files.
    # As the random string is based on the wallet seed, we can be pretty sure there
    # is only one and we can ignore the risk of catching multiple directories with
    # the * wildcard.
    backupfileinfo = []
    for filepath in glob(path.join(DATADIR_MASK, 'snapshots', snapname, '*', '*.dat')):
        fileinfo = stat(filepath)
        # Only use files of non-zero size.
        if fileinfo.st_size:
            filename = path.basename(filepath)
            (froot, fext) = path.splitext(filename)
            sia_fname = '%s.%s%s' % (froot, int(fileinfo.st_mtime), fext)
            if any(sf['siapath'] == sia_fname and sf['available'] for sf in siafiles):
                current_app.logger.info('%s is part of the set but already uploaded.' % sia_fname)
            elif any(sf['siapath'] == sia_fname for sf in siafiles):
                current_app.logger.info('%s is part of the set but the upload is already in progress.' % sia_fname)
            else:
                current_app.logger.info('%s has to be uploaded, starting that.' % sia_fname)
                siadata, sia_status_code = postToSia('renter/upload/%s' % sia_fname,
                                                     {'source': filepath})
                if sia_status_code != 204:
                    return False, "ERROR: sia upload error %s: %s" % (sia_status_code, siadata['message'])
            backupfileinfo.append({"siapath": sia_fname, "size": fileinfo.st_size})

    with open(bfinfo_path, 'w') as outfile:
        json.dump(backupfileinfo, outfile)
    return True, ""

def wait_for_uploads():
    return

def save_metadata():
    return

def remove_lower_snapshots(snapname):
    current_app.logger.info('Removing lower-level data snapshot(s) with name: %s' % snapname)
    for snap in glob(path.join(DATADIR_MASK, 'snapshots', snapname)):
        subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'delete', snap])
    return

def remove_old_backups():
    return

