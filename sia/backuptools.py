# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from os import path, stat, mkdir, makedirs, remove
import os
import shutil
from glob import glob
from zipfile import ZipFile
import re
import time
import subprocess
import sys

from connecttools import (get_demo_url, get_from_sia, post_to_sia,
                          get_from_minebd, put_to_minebd, put_to_metadata)
from backupinfo import *
from siatools import check_sia_sync, SIA_DIR
from systemtools import BTRFS

REDUNDANCY_LIMIT = 2.5

def check_backup_prerequisites():
    # Check if prerequisites are met to make backups.
    success, errmsg = check_sia_sync()
    if not success:
        return False, errmsg
    siadata, sia_status_code = get_from_sia("renter/contracts")
    if sia_status_code >= 400:
        return False, siadata["message"]
    if not siadata["contracts"]:
        return False, "No Sia renter contracts, so uploading is not possible."
    mbdata, mb_status_code = get_from_minebd('status')
    if mb_status_code >= 400:
        return False, mbdata["message"]
    if mbdata["restoreRunning"] and mbdata["completedRestorePercent"] < 100:
        return False, "MineBD is running an incomplete restore, so creating a backup is not possible."
    # Potentially check things other than sia.
    return True, ""

def snapshot_upper(status):
    current_app.logger.info('Creating snapshots of stored data.')
    status["message"] = "Creating snapshots of stored data"
    # for step, use current fuction name with spaces instead of underscores
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    snapname = status["snapname"]
    upper_subvols = []
    outlines = subprocess.check_output([BTRFS, 'subvolume', 'list', MINEBD_STORAGE_PATH]).splitlines()
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
        subprocess.call([BTRFS, 'subvolume', 'snapshot', '-r',
                         os.path.join(MINEBD_STORAGE_PATH, subvol),
                         os.path.join(MINEBD_STORAGE_PATH, 'snapshots', subvol, snapname)])
    return

def create_lower_snapshots(status):
    status["message"] = "Creating backup files"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
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
        subprocess.call([BTRFS, 'subvolume', 'snapshot', '-r', subvol, path.join(subvol, 'snapshots', snapname)])
    current_app.logger.info(
      'Telling MineBD to pause (for 1.5s) to make sure no modified blocks exist with the same timestamp as in our snapshots.'
    )
    mbdata, mb_status_code = put_to_minebd('pause', '', [{'Content-Type': 'application/json'}, {'Accept': 'text/plain'}])
    return

def initiate_uploads(status):
    current_app.logger.info('Starting uploads.')
    status["message"] = "Starting uploads"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    snapname = status["snapname"]
    backupname = status["backupname"]

    metadir = path.join(METADATA_BASE, backupname)
    bfinfo_path = path.join(metadir, INFO_FILENAME)
    if path.isfile(bfinfo_path):
        remove(bfinfo_path)
    sia_filedata, sia_status_code = get_from_sia('renter/files')
    if sia_status_code == 200:
        siafiles = sia_filedata["files"]
    else:
        return False, "ERROR: sia daemon needs to be running for any uploads."

    # We have a randomly named subdirectory containing the .dat files.
    # The subdirectory matches the serial number that MineBD returns.
    mbdata, mb_status_code = get_from_minebd('serialnumber')
    if mb_status_code == 200:
        mbdirname = mbdata["message"]
    else:
        return False, "ERROR: Could not get serial number from MineBD."

    status["backupfileinfo"] = []
    status["backupfiles"] = []
    status["uploadfiles"] = []
    status["backupsize"] = 0
    status["uploadsize"] = 0
    status["min_redundancy"] = None
    status["earliest_expiration"] = None
    for filepath in glob(path.join(DATADIR_MASK, 'snapshots', snapname, mbdirname, '*.dat')):
        fileinfo = stat(filepath)
        # Only use files of non-zero size.
        if fileinfo.st_size:
            filename = path.basename(filepath)
            (froot, fext) = path.splitext(filename)
            sia_fname = '%s.%s%s' % (froot, int(fileinfo.st_mtime), fext)
            if sia_fname in status["backupfiles"]:
                # This file is already in the list, and we probably have
                # multiple lower disks, so omit this file.
                continue
            status["backupfiles"].append(sia_fname)
            status["backupsize"] += fileinfo.st_size
            if (siafiles
                and any(sf["siapath"] == sia_fname
                        and sf["available"]
                        and sf["redundancy"] > REDUNDANCY_LIMIT
                        for sf in siafiles)):
                current_app.logger.info("%s is part of the set and already uploaded."
                                        % sia_fname)
            elif (siafiles
                  and any(sf["siapath"] == sia_fname
                          for sf in siafiles)):
                status["uploadsize"] += fileinfo.st_size
                status["uploadfiles"].append(sia_fname)
                current_app.logger.info("%s is part of the set and the upload is already in progress."
                                        % sia_fname)
            else:
                status["uploadsize"] += fileinfo.st_size
                status["uploadfiles"].append(sia_fname)
                current_app.logger.info("%s has to be uploaded, starting that."
                                        % sia_fname)
                siadata, sia_status_code = post_to_sia('renter/upload/%s' % sia_fname,
                                                       {'source': filepath})
                if sia_status_code != 204:
                    return False, ("ERROR: sia upload error %s: %s"
                                   % (sia_status_code, siadata["message"]))
            status["backupfileinfo"].append({"siapath": sia_fname,
                                             "size": fileinfo.st_size})

    if not status["backupfiles"]:
        return False, "ERROR: The backup set has no files, that's impossible."
    with open(bfinfo_path, 'w') as outfile:
        json.dump(status["backupfileinfo"], outfile)
    return True, ""

def wait_for_uploads(status):
    status["message"] = "Waiting for uploads to complete"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    status["available"] = False
    if not status["backupfileinfo"]:
        return False, "ERROR: Backup file info is missing."
    # Loop and sleep as long as the backup is not fully available and uploaded.
    # To "emulate" a do loop, we loop "forever" and break on our condition.
    while True:
        # Get upload status (this queries Sia).
        upstatus = get_upload_status(status["backupfileinfo"], status["uploadfiles"])
        if upstatus:
            status["uploadprogress"] = upstatus["uploadprogress"]
            status["totalprogress"] = upstatus["totalprogress"]
            status["min_redundancy"] = upstatus["min_redundancy"]
            status["earliest_expiration"] = upstatus["earliest_expiration"]
            # Break if the backup is fully available on sia and has enough
            # minimum redundancy.
            if (upstatus["fully_available"]
                and upstatus["min_redundancy"] >= REDUNDANCY_LIMIT):
                status["available"] = True
                current_app.logger.info(
                  "Backup is fully available and minimum file redundancy is %.1f, we can finish things up.",
                  upstatus["min_redundancy"])
                break
            # Also break if the backup has no files to upload (i.e. all were
            # finished when we started). In this case, min_redundancy isn't
            # set, so emit a message that doesn't talk about it.
            if not status["uploadfiles"]:
                status["available"] = True
                current_app.logger.info(
                  "Backup is fully already fully uploaded, we can finish things up.")
                break
            # If we are still here, wait some minutes for more upload progress.
            wait_minutes = 5
            current_app.logger.info(
              "Uploads are not yet complete (%d%% / min file redundancy %.1f), wait %d minutes.",
              int(status["uploadprogress"]), upstatus["min_redundancy"], wait_minutes)
            time.sleep(wait_minutes * 60)
        else:
            return False, "ERROR: Sia daemon needs to be running for any uploads."
    return True, ""

def save_metadata(status):
    status["message"] = "Saving metadata"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    snapname = status["snapname"]
    backupname = status["backupname"]
    metadir = path.join(METADATA_BASE, backupname)
    # Copy renter, gateway and wallet folders to metadata directory.
    for siafolder in ["renter", "gateway", "wallet"]:
        # Make sure the directory does not exist.
        if path.isdir(path.join(metadir, siafolder)):
            shutil.rmtree(path.join(metadir, siafolder))
        # The copytree target needs to be the not-yet-existing target directory.
        shutil.copytree(path.join(SIA_DIR, siafolder),
                        path.join(metadir, siafolder))
    # Create a bundle of all metadata for this backup.
    zipname = join(METADATA_BASE, "%s.zip" % backupname)
    if path.isfile(zipname):
        remove(zipname)
    with ZipFile(zipname, 'w') as backupzip:
        for sfile in glob(path.join(metadir, "*", "*")):
            # Exclude files we do not require in the zip.
            if re.match(r'.*\.(json_temp|log)$', sfile):
                continue
            basefilename = path.basename(sfile)
            basedirname = path.basename(path.dirname(sfile))
            inzipfilename = path.join(basedirname, basefilename)
            backupzip.write(sfile, inzipfilename)
        backupzip.write(path.join(metadir, INFO_FILENAME), INFO_FILENAME)
    # Upload metadata bundle.
    current_app.logger.info("Upload metadata.")
    with open(zipname) as zipfile:
        zipdata = zipfile.read()
        mdata, md_status_code = put_to_metadata("file/%s.zip" % backupname, zipdata)
        if md_status_code >= 400:
            return False, mdata["message"]
    # Remove metadata directory, leave zip around.
    if path.isfile(zipname) and path.isdir(metadir):
        shutil.rmtree(metadir)
    status["metadata_uploaded"] = True
    return True, ""

def remove_lower_snapshots(status):
    snapname = status["snapname"]
    status["message"] = "Cleaning up backup data"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    current_app.logger.info('Removing lower-level data snapshot(s) with name: %s' % snapname)
    for snap in glob(path.join(DATADIR_MASK, 'snapshots', snapname)):
        subprocess.call([BTRFS, 'subvolume', 'delete', snap])
    return

def remove_old_backups(status, activebackups):
    status["message"] = "Cleaning up old backups"
    status["step"] = sys._getframe().f_code.co_name.replace("_", " ")
    status["starttime_step"] = time.time()
    restartset = get_backups_to_restart()
    allbackupnames = get_list()
    sia_filedata, sia_status_code = get_from_sia('renter/files')
    if sia_status_code == 200:
        sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
    else:
        return False, "ERROR: Sia daemon needs to be running for any uploads."
    keepfiles = []
    keepset_complete = False
    for backupname in allbackupnames:
        keep_this_backup = False
        if not keepset_complete:
            # We don't have all files to keep yet, see if this is our "golden"
            # backup, an active or to-restart one - otherwise, schedule removal.
            backupfiles, is_finished = get_files(backupname)
            if backupname in activebackups or backupname in restartset:
                keep_this_backup = True
                # If we have an active backup that has metadata uploaded,
                # we can consider it "golden".
                if (backupname in activebackups
                    and "metadata_uploaded" in status
                    and status["metadata_uploaded"]):
                    current_app.logger.info("Backup %s is complete!" % backupname)
                    keepset_complete = True
            elif backupfiles and is_finished:
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
                    keep_this_backup = True
                    keepset_complete = True
            if keep_this_backup:
                current_app.logger.info("Keeping %s backup %s" %
                                        ("finished" if is_finished else "unfinished",
                                         backupname))
                # Note that extend does not return anything.
                keepfiles.extend(backupfiles)
                # Converting to a set eliminates duplicates.
                # Convert back to list for type consistency.
                keepfiles = list(set(keepfiles))
        if not keep_this_backup:
            # We already have all files to keep, any older backup can be discarded.
            current_app.logger.info("Discard old backup %s" % backupname)
            zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
            dirname = join(METADATA_BASE, "backup.%s" % backupname)
            if path.isfile(zipname):
                os.rename(zipname, join(METADATA_BASE, "old.backup.%s.zip" % backupname))
            else:
                os.rename(dirname, join(METADATA_BASE, "old.backup.%s" % backupname))

    rinfo = _get_repair_paths()
    keepsnaps = []

    if keepfiles and sia_filedata["files"]:
        current_app.logger.info("Removing unneeded files from Sia network")
        for siafile in sia_filedata["files"]:
            if siafile["siapath"] in keepfiles:
                # Get snapname from local paths like
                # /mnt/lowerX/data/snapshots/<snapname>/<id>/minebox_v1_<num>.dat
                src_snap = rinfo[siafile["siapath"]]["RepairPath"].split("/")[5]
                # Keep snapshots that are the source of all files to keep, as
                # they could still be uploading and therefore need the source.
                if not src_snap in keepsnaps:
                    keepsnaps.append(src_snap)
            else:
                siadata, sia_status_code = post_to_sia("renter/delete/%s" % siafile["siapath"], "")
                if sia_status_code != 204:
                    return False, "ERROR: sia delete error %s: %s" % (sia_status_code, siadata['message'])

    current_app.logger.info("The following snapshots need to be kept for potential uploads: %s", keepsnaps)
    current_app.logger.info("Removing possibly unneeded old lower-level data snapshots")
    for snap in glob(path.join(DATADIR_MASK, "snapshots", "*")):
        snapname = path.basename(snap)
        zipname = join(METADATA_BASE, "backup.%s.zip" % snapname)
        dirname = join(METADATA_BASE, "backup.%s" % snapname)
        if (not path.isfile(zipname) and not path.isdir(dirname)
            and not snapname in activebackups
            and not snapname in keepsnaps):
            subprocess.call([BTRFS, 'subvolume', 'delete', snap])
    return True, ""

def _get_repair_paths():
    # HACK: Get "repair paths" from renter.json
    # That file doesn't correctly parse as JSON as it has leading non-JSON
    # data, so we cut off that leading data and take the "Tracking" branch of
    # the rest. This should be replaced with API info ASAP.
    # See https://github.com/NebulousLabs/Sia/issues/2428
    renter_raw = ''
    with open('/mnt/lower1/sia/renter/renter.json') as renter_json:
        for line in renter_json:
            if len(renter_raw) or line.startswith("{"):
                renter_raw += line
    return json.loads(renter_raw)["Tracking"]
