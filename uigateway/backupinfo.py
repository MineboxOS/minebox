# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from os.path import isfile, isdir, join
import os
from glob import glob
from zipfile import ZipFile
from datetime import datetime
import time
import re
from connecttools import get_from_sia, get_from_backupservice
from siatools import estimate_timestamp_for_height


DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
MINEBD_STORAGE_PATH="/mnt/storage"
INFO_FILENAME="fileinfo"


def get_status(backupname, allow_old=False, use_cache=True):
    status_code = 0
    backupstatus = {
      "name": backupname,
      "time_snapshot": None,
      "status": None,
      "metadata": None,
      "numFiles": None,
      "size": None,
      "progress": 0,
      "relative_size": None,
      "relative_progress": 0,
      "min_redundancy": None,
      "earliest_expiration": None,
      "earliest_expiration_esttime": None,
    }

    # If use of cache was refused, or we do not have a status from
    # backupservice yet or timestamp is older than 60 seconds, fetch a new one,
    # else use the cached status.
    if (not use_cache
        or not hasattr(get_status, "bsdata") or get_status.bsdata is None
        or get_status.bstimestamp < time.time() - 60):
        # Always set the timestamp so we do not have to test above if it's set,
        #  as it's only unset when token is also unset
        get_status.bstimestamp = time.time()
        bsdata, bs_status_code = get_from_backupservice("status")
        if bs_status_code == 200:
            get_status.bsdata = bsdata
        else:
            current_app.logger.warn('Error %s getting status from backup service: %s',
                                    bs_status_code,  bsdata["message"])
            get_status.bsdata = None

    # If this is a backup that backupservice is tracking, let's use info from there.
    if get_status.bsdata:
        for binfo in get_status.bsdata["backup_info"]:
            if binfo["name"] == backupname:
                backupstatus["time_snapshot"] = binfo["time_snapshot"]
                backupstatus["numFiles"] = binfo["filecount"]
                backupstatus["size"] = binfo["size"]
                backupstatus["relative_size"] = binfo["upload_size"]
                backupstatus["progress"] = binfo["total_progress"]
                backupstatus["relative_progress"] = binfo["upload_progress"]
                backupstatus["min_redundancy"] = binfo["min_redundancy"]
                backupstatus["earliest_expiration"] = binfo["earliest_expiration"]
                fully_available = binfo["fully_available"]
                if binfo["failed"]:
                    backupstatus["status"] = "ERROR"
                    backupstatus["metadata"] = "ERROR"
                    status_code = 503
                elif binfo["finished"] and binfo["metadata_uploaded"]:
                    backupstatus["status"] = "FINISHED"
                    backupstatus["metadata"] = "FINISHED"
                    # Do not set status code as we need to update the progress
                    # because backup service stops updating it after finish.
                    #status_code = 200
                elif binfo["finished"]:
                    backupstatus["status"] = "FINISHED"
                    backupstatus["metadata"] = "UPLOADING"
                    status_code = 200
                elif binfo["upload_size"]:
                    backupstatus["status"] = "UPLOADING"
                    backupstatus["metadata"] = "PENDING"
                    status_code = 200
                else:
                    backupstatus["status"] = "PENDING"
                    backupstatus["metadata"] = "PENDING"
                    status_code = 200
                if not binfo["size"]:
                    # Clean cache, we may have info the next time we're called.
                    get_status.bsdata = None

    if not status_code:
        backupfileinfo, is_finished, is_archived = get_fileinfo(backupname)

        status_code = 200
        backupstatus["time_snapshot"] = int(backupname)
        if backupfileinfo is None:
            # This backup has no file info available or doesn't exist.
            status_code = 404
            backupstatus["progress"] = 100 if is_finished else 0
            backupstatus["relative_progress"] = 100 if is_finished else 0
            backupstatus["status"] = "ERROR"
            backupstatus["metadata"] = "ERROR"
            fully_available = False
        elif len(backupfileinfo) < 1:
            # Before uploads are scheduled, we find a backup but no files.
            # If we believe we are finished/archived though, the data is damaged.
            backupstatus["progress"] = 0
            backupstatus["relative_progress"] = 0
            if is_archived or is_finished:
                backupstatus["status"] = "DAMAGED"
                backupstatus["metadata"] = "DAMAGED"
            else:
                backupstatus["status"] = "PENDING"
                backupstatus["metadata"] = "PENDING"
            fully_available = False
        else:
            # Nice, we actually have good file info.
            # First, find out uploadfiles.
            backuplist = get_list(allow_old)
            currentidx = backuplist.index(backupname)
            if currentidx < len(backuplist) - 1:
                prev_backupfiles, prev_finished = get_files(backuplist[currentidx + 1])
                uploadfiles = []
                for finfo in backupfileinfo:
                    if (prev_backupfiles is None
                        or not finfo["siapath"] in prev_backupfiles):
                        uploadfiles.append(finfo["siapath"])
            else:
                uploadfiles = [finfo["siapath"] for finfo in backupfileinfo]
            # Get actual status (this queries Sia).
            upstatus = get_upload_status(backupfileinfo, uploadfiles, is_archived)
            if upstatus:
                backupstatus["numFiles"] = upstatus["filecount"]
                backupstatus["size"] = upstatus["backupsize"]
                backupstatus["progress"] = upstatus["totalprogress"]
                backupstatus["relative_size"] = upstatus["uploadsize"]
                backupstatus["relative_progress"] = upstatus["uploadprogress"]
                backupstatus["min_redundancy"] = upstatus["min_redundancy"]
                backupstatus["earliest_expiration"] = upstatus["earliest_expiration"]
                if is_archived:
                    backupstatus["status"] = "ARCHIVED"
                elif is_finished and upstatus["fully_available"]:
                    backupstatus["status"] = "FINISHED"
                elif is_finished and not upstatus["fully_available"]:
                    backupstatus["status"] = "DAMAGED"
                elif upstatus["total_uploaded_size"]:
                    backupstatus["status"] = "UPLOADING"
                else:
                    backupstatus["status"] = "PENDING"
                if is_finished:
                    # Assume metadata is always uploaded when we are finished.
                    # As right now we report finished only if we have a .zip but no
                    # directory, and we delete the directory only after metadata
                    # upload is done, this is actually the case.
                    backupstatus["metadata"] = "FINISHED"
                elif is_archived:
                    # Archived backups that are not finished are missing metadata.
                    backupstatus["metadata"] = "DAMAGED"
                else:
                    # Otherwise, always report pending metadata.
                    backupstatus["metadata"] = "PENDING"
                fully_available = upstatus["fully_available"]
            else:
                status_code = 503
                backupstatus["status"] = "ERROR"
                backupstatus["metadata"] = "ERROR"
                fully_available = False

    if backupstatus["earliest_expiration"]:
        backupstatus["earliest_expiration_esttime"] = estimate_timestamp_for_height(backupstatus["earliest_expiration"])

    return backupstatus, status_code


def get_list(include_old=False):
    backuplist = [re.sub(r'.*backup\.(\d+)(\.zip)?', r'\1', f)
                  for f in glob(join(METADATA_BASE, "backup.*"))
                    if (isfile(f) and f.endswith(".zip")) or
                       isdir(f) ]
    if include_old:
        # Actually look at upper level snapshots to figure out what old backups
        # to add - and only add them if we have lower-level info as well.
        for snap in glob(os.path.join(MINEBD_STORAGE_PATH, 'snapshots', '*', '*')):
            snapname = os.path.basename(snap)
            if (not snapname in backuplist
                and (isfile(join(METADATA_BASE, "old.backup.%s.zip" % snapname))
                     or (isdir(join(METADATA_BASE, "old.backup.%s" % snapname))
                         and isfile(join(METADATA_BASE, "old.backup.%s" % snapname, "fileinfo"))))):
                backuplist.append(snapname)
    # Converting to a set eliminates duplicates.
    # Convert back to list for type consistency.
    backuplist = list(set(backuplist))
    # Sort most-recent-first.
    backuplist.sort(reverse=True)
    return backuplist


def get_latest():
    backuplist = get_list()
    return backuplist[0] if backuplist else None


def get_backups_to_restart():
    # Look at existing backups and find out which ones are unfinished and
    # should be restarted.
    restartlist = []
    backuplist = get_list()
    prevsnap = None
    prevsnap_exists = None
    for snapname in backuplist:
        backupfiles, is_finished = get_files(snapname)
        snapshot_exists = False
        if glob(os.path.join(DATADIR_MASK, 'snapshots', snapname)):
            snapshot_exists = True
        # Always add the most recent backup if it's unfinished and the
        # lower-level snapshot exists.
        if snapshot_exists and not prevsnap and not is_finished:
            restartlist.append(snapname)
        # Break on the first finished backup, add previous one (oldest
        # unfinished) unless it's already in the list.
        if is_finished:
            if prevsnap_exists and prevsnap and not prevsnap in restartlist:
                 restartlist.append(prevsnap)
            break
        # If we arrive at the last item of the list, add if it's unfinished.
        if snapname == backuplist[-1]:
            if snapshot_exists and not is_finished and not snapname in restartlist:
                 restartlist.append(snapname)
            break
        # Remember snapname for next cycle.
        prevsnap = snapname
        prevsnap_exists = snapshot_exists
    return restartlist


def get_files(backupname):
    backupfiles = None
    backupfileinfo, is_finished, is_archived = get_fileinfo(backupname)
    if backupfileinfo:
        backupfiles = [fi["siapath"] for fi in backupfileinfo]
    return backupfiles, is_finished


def get_fileinfo(backupname):
    backupfileinfo = None
    is_finished = None
    is_archived = None
    zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
    dirname = join(METADATA_BASE, "backup.%s" % backupname)
    oldzipname = join(METADATA_BASE, "old.backup.%s.zip" % backupname)
    olddirname = join(METADATA_BASE, "old.backup.%s" % backupname)
    # For "current" backups, directory overrides zipfile (as metadata upload
    # may have failed), for old/archived ones, do the reverse.
    if isdir(dirname):
        backupfileinfo = []
        is_finished = False
        is_archived = False
        bfinfo_path = join(dirname, INFO_FILENAME)
        if isfile(bfinfo_path):
            with open(bfinfo_path) as json_file:
                backupfileinfo = json.load(json_file)
    elif isfile(zipname):
        backupfileinfo = []
        is_finished = True
        is_archived = False
        with ZipFile(zipname, 'r') as backupzip:
            # The infofname_long is a workaround for misconstructed zips before July 4, 2017.
            # TODO: remove this workaround again once we have some backup history with correct zips.
            infofname_long = str(join(dirname, INFO_FILENAME))[1:]
            if INFO_FILENAME in backupzip.namelist():
                with backupzip.open(INFO_FILENAME) as json_file:
                    backupfileinfo = json.load(json_file)
            elif infofname_long in backupzip.namelist():
                with backupzip.open(infofname_long) as json_file:
                    backupfileinfo = json.load(json_file)
    elif isfile(oldzipname):
        backupfileinfo = []
        is_finished = True
        is_archived = True
        with ZipFile(oldzipname, 'r') as backupzip:
            # The infofname_long is a workaround for misconstructed zips before July 4, 2017.
            # TODO: remove this workaround again once we have some backup history with correct zips.
            infofname_long = str(join(dirname, INFO_FILENAME))[1:]
            if INFO_FILENAME in backupzip.namelist():
                with backupzip.open(INFO_FILENAME) as json_file:
                    backupfileinfo = json.load(json_file)
            elif infofname_long in backupzip.namelist():
                with backupzip.open(infofname_long) as json_file:
                    backupfileinfo = json.load(json_file)
    elif isdir(olddirname):
        backupfileinfo = []
        is_finished = False
        is_archived = True
        bfinfo_path = join(olddirname, INFO_FILENAME)
        if isfile(bfinfo_path):
            with open(bfinfo_path) as json_file:
                backupfileinfo = json.load(json_file)
    return backupfileinfo, is_finished, is_archived


def is_finished(backupname):
    backupfileinfo, is_finished, is_archived = get_fileinfo(backupname)
    return is_finished

def get_upload_status(backupfileinfo, uploadfiles, is_archived=False):
    sia_filedata, sia_status_code = get_from_sia("renter/files")
    if sia_status_code >= 400:
        current_app.logger.error("Error %s getting Sia files: %s",
                                  sia_status_code, sia_filedata["message"])
        return False

    upstatus = {
      "filecount": 0,
      "backupsize": 0,
      "uploadsize": 0,
      "total_uploaded_size": 0,
      "uploaded_size": 0,
      "fully_available": True,
    }
    redundancy = []
    expiration = []
    # create a dict generated from the JSON response.
    sia_map = dict((d["siapath"], index)
                    for (index, d) in enumerate(sia_filedata["files"]))
    for finfo in backupfileinfo:
        if not is_archived and finfo["siapath"] in sia_map:
            upstatus["filecount"] += 1
            fdata = sia_filedata["files"][sia_map[finfo["siapath"]]]
            upstatus["backupsize"] += fdata["filesize"]
            upstatus["total_uploaded_size"] += (fdata["filesize"] *
                                                fdata["uploadprogress"] /
                                                100.0)
            if fdata["siapath"] in uploadfiles:
                upstatus["uploadsize"] += fdata["filesize"]
                upstatus["uploaded_size"] += (fdata["filesize"] *
                                              fdata["uploadprogress"] /
                                              100.0)
            redundancy.append(fdata["redundancy"])
            expiration.append(fdata["expiration"])
            if not fdata["available"]:
                upstatus["fully_available"] = False
        elif re.match(r".*\.dat$", finfo["siapath"]):
            upstatus["filecount"] += 1
            upstatus["backupsize"] += finfo["size"]
            if finfo["siapath"] in uploadfiles:
                upstatus["uploadsize"] += finfo["size"]
            if not is_archived:
                upstatus["fully_available"] = False
                current_app.logger.warn("File %s not found on Sia!",
                                        finfo["siapath"])
        else:
            current_app.logger.debug(
              'File "%s" not on Sia and not matching watched names.',
              finfo["siapath"])

    # If size is 0, we report 100% progress.
    # This is really needed for upload as otherwise a backup with no
    # new uploadfiles would never go to 100%.
    upstatus["uploadprogress"] = (100.0 * upstatus["uploaded_size"] /
                                          upstatus["uploadsize"]
                                  if upstatus["uploadsize"] else 100)
    upstatus["totalprogress"] = (100.0 * upstatus["total_uploaded_size"] /
                                          upstatus["backupsize"]
                                  if upstatus["backupsize"] else 100)
    upstatus["min_redundancy"] = min(redundancy) if redundancy else 0
    upstatus["earliest_expiration"] = min(expiration) if expiration else 0
    return upstatus
