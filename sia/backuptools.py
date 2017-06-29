# Various tools around backups, mostly to get info about them.

from flask import current_app, json
from os import path, stat, mkdir, makedirs, remove
import os
import shutil
from glob import glob
from zipfile import ZipFile
import re
import time
import subprocess

from connecttools import (getDemoURL, getFromSia, postToSia, putToMineBD,
                          putToMetadata)
from backupinfo import *

SIA_DIR="/mnt/lower1/sia"
MINEBD_STORAGE_PATH="/mnt/storage"

def check_prerequisites():
    # Step 0: Check if prerequisites are met to make backups.
    consdata, cons_status_code = getFromSia('consensus')
    if cons_status_code == 200:
        if not consdata["synced"]:
          return False, "ERROR: sia seems not to be synced. Please again when the consensus is synced."
    else:
        return False, "ERROR: sia daemon needs to be running for any uploads."
    return True, ""

def snapshot_upper(status):
    current_app.logger.info('Creating snapshots of stored data.')
    status["message"] = "Creating snapshots of stored data"
    snapname = status["snapname"]
    upper_subvols = []
    outlines = subprocess.check_output(['/usr/sbin/btrfs', 'subvolume', 'list', MINEBD_STORAGE_PATH]).splitlines()
    for line in outlines:
        current_app.logger.info(line)
        matches = re.match(r"^ID ([0-9]+).*gen ([0-9]+).*top level ([0-9]+).*path (.+)$", line)
        toplevel = int(matches.group(3))  # 3rd parenthesis expression
        subpath = matches.group(4)        # 4th parenthesis expression
        if toplevel == 5 and not subpath.startswith("snapshots/"):
            upper_subvols.append(subpath)

    for subvol in upper_subvols:
        if not path.isdir(path.join(MINEBD_STORAGE_PATH, 'snapshots', subvol)):
            makedirs(path.join(MINEBD_STORAGE_PATH, 'snapshots', subvol))
        subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'snapshot', '-r',
                         os.path.join(MINEBD_STORAGE_PATH, subvol),
                         os.path.join(MINEBD_STORAGE_PATH, 'snapshots', subvol, snapname)])
    return

def create_lower_snapshots(status):
    status["message"] = "Creating backup files"
    snapname = status["snapname"]
    backupname = status["backupname"]

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
    return

def initiate_uploads(status):
    current_app.logger.info('Starting uploads.')
    status["message"] = "Starting uploads"
    snapname = status["snapname"]
    backupname = status["backupname"]

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
    status["backupfileinfo"] = []
    status["uploadfiles"] = []
    status["backupsize"] = 0
    status["uploadsize"] = 0
    for filepath in glob(path.join(DATADIR_MASK, 'snapshots', snapname, '*', '*.dat')):
        fileinfo = stat(filepath)
        # Only use files of non-zero size.
        if fileinfo.st_size:
            status["backupsize"] += fileinfo.st_size
            filename = path.basename(filepath)
            (froot, fext) = path.splitext(filename)
            sia_fname = '%s.%s%s' % (froot, int(fileinfo.st_mtime), fext)
            if any(sf['siapath'] == sia_fname and sf['available'] for sf in siafiles):
                current_app.logger.info('%s is part of the set but already uploaded.' % sia_fname)
            elif any(sf['siapath'] == sia_fname for sf in siafiles):
                current_app.logger.info('%s is part of the set but the upload is already in progress.' % sia_fname)
            else:
                status["uploadsize"] += fileinfo.st_size
                status["uploadfiles"].append(sia_fname)
                current_app.logger.info('%s has to be uploaded, starting that.' % sia_fname)
                siadata, sia_status_code = postToSia('renter/upload/%s' % sia_fname,
                                                     {'source': filepath})
                if sia_status_code != 204:
                    return False, "ERROR: sia upload error %s: %s" % (sia_status_code, siadata['message'])
            status["backupfileinfo"].append({"siapath": sia_fname, "size": fileinfo.st_size})

    with open(bfinfo_path, 'w') as outfile:
        json.dump(status["backupfileinfo"], outfile)
    return True, ""

def wait_for_uploads(status):
    status["message"] = "Waiting for uploads to complete"
    uploaded_size = 0
    fully_available = False
    # Loop and sleep as long as the backup is not fully available and uploaded.
    # TODO: here and for the sleep below, we should actually use *or*.
    while not fully_available and uploaded_size < status["uploadsize"]:
        sia_filedata, sia_status_code = getFromSia('renter/files')
        if sia_status_code == 200:
            uploaded_size = 0
            fully_available = True
            sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
            for bfile in status["backupfileinfo"]:
                if bfile["siapath"] in sia_map:
                    fdata = sia_filedata["files"][sia_map[bfile["siapath"]]]
                    if fdata["siapath"] in status["uploadfiles"]:
                        uploaded_size += fdata["filesize"] * fdata["uploadprogress"] / 100.0
                    if not fdata["available"]:
                        fully_available = False
                elif re.match(r'.*\.dat$', bfile["siapath"]):
                    fully_available = False
                    current_app.logger.warn('File "%s" not found on Sia!', bfile["siapath"])
                else:
                    current_app.logger.debug('File "%s" not on Sia and not matching watched names.', bfile["siapath"])
            status["uploadprogress"] = 100.0 * uploaded_size / status["uploadsize"]
            if not fully_available and uploaded_size < status["uploadsize"]:
                # Sleep 5 minutes.
                time.sleep(5 * 60)
        else:
            return False, "ERROR: Sia daemon needs to be running for any uploads."
    return True, ""

def save_metadata(status):
    status["message"] = "Saving metadata"
    snapname = status["snapname"]
    backupname = status["backupname"]
    metadir = path.join(METADATA_BASE, backupname)
    # Copy .sia files to metadata directory.
    for bfile in status["backupfileinfo"]:
        dest_siafile = path.join(metadir, "%s.sia" % bfile["siapath"])
        if not path.isfile(dest_siafile):
            shutil.copy2(path.join(SIA_DIR, "renter", "%s.sia" % bfile["siapath"]), metadir)
    # Create a bundle of all metadata for this backup.
    zipname = join(METADATA_BASE, "%s.zip" % backupname)
    if path.isfile(zipname):
        remove(zipname)
    with ZipFile(zipname, 'w') as backupzip:
        for bfile in status["backupfileinfo"]:
            siafile = path.join(metadir, "%s.sia" % bfile["siapath"])
            backupzip.write(siafile)
        backupzip.write(path.join(metadir, 'fileinfo'))
    # Upload metadata bundle.
    current_app.logger.info("Upload metadata.")
    with open(zipname) as zipfile:
        zipdata = zipfile.read()
        mdata, md_status_code = putToMetadata("file/%s.zip" % backupname, zipdata)
        if md_status_code >= 400:
            return False, mdata["message"]
    return True, ""

def remove_lower_snapshots(status):
    snapname = status["snapname"]
    status["message"] = "Cleaning up backup data"
    current_app.logger.info('Removing lower-level data snapshot(s) with name: %s' % snapname)
    for snap in glob(path.join(DATADIR_MASK, 'snapshots', snapname)):
        subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'delete', snap])
    return

def remove_old_backups(status, activebackups):
    status["message"] = "Cleaning up old backups"
    allbackupnames = getList()
    sia_filedata, sia_status_code = getFromSia('renter/files')
    if sia_status_code == 200:
        sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
    else:
        return False, "ERROR: Sia daemon needs to be running for any uploads."
    keepfiles = []
    keepset_complete = False
    for backupname in allbackupnames:
        if not keepset_complete:
            # We don't have all files to keep yet, see if this is our "golden" backup.
            backupfiles, is_finished = getFiles(backupname)
            current_app.logger.info("Keeping %s backup %s" %
                                    ("finished" if is_finished else "unfinished",
                                     backupname))
            if backupfiles:
                # Note that extend does not return anything.
                keepfiles.extend(backupfiles)
                # Converting to a set eliminates duplicates.
                # Convert back to list for type consistency.
                keepfiles = list(set(keepfiles))
            if is_finished:
                files_missing = False
                for bfile in backupfiles:
                   if (not bfile in sia_map
                       or not sia_filedata["files"][sia_map[bfile]]["available"]):
                       files_missing = True
                       current_app.logger.info("%s is not fully available!" % bfile)
                if not files_missing:
                    # Yay! A finished backup with all files available!
                    # Keep this and everything we already collected, but that's it.
                    current_app.logger.info("Backup %s is fully complete!" % backupname)
                    keepset_complete = True
        else:
            # We already have all files to keep, any older backup can be discarded.
            current_app.logger.info("Discard old backup %s" % backupname)
            zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
            dirname = join(METADATA_BASE, "backup.%s" % backupname)
            if path.isfile(zipname):
                os.rename(zipname, join(METADATA_BASE, "old.backup.%s.zip" % backupname))
            else:
                os.rename(dirname, join(METADATA_BASE, "old.backup.%s" % backupname))

    if keepfiles and sia_filedata["files"]:
        current_app.logger.info("Removing unneeded files from Sia network")
        for siafile in sia_filedata["files"]:
            if not siafile["siapath"] in keepfiles:
                siadata, sia_status_code = postToSia("renter/delete/%s" % siafile["siapath"], "")
                if sia_status_code != 204:
                    return False, "ERROR: sia delete error %s: %s" % (sia_status_code, siadata['message'])

    current_app.logger.info("Removing possibly unneeded old lower-level data snapshots")
    for snap in glob(path.join(DATADIR_MASK, "snapshots", "*")):
        snapname = path.basename(snap)
        zipname = join(METADATA_BASE, "backup.%s.zip" % snapname)
        dirname = join(METADATA_BASE, "backup.%s" % snapname)
        if (not path.isfile(zipname) and not path.isdir(dirname)
            and not snapname in activebackups):
            subprocess.call(['/usr/sbin/btrfs', 'subvolume', 'delete', snap])
    return True, ""

