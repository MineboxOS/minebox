# Various tools around backups, mostly to get info about them.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from flask import current_app, json
from os.path import isfile, isdir, join
import os
from glob import glob
from zipfile import ZipFile
import re
from connecttools import get_from_sia, get_from_backupservice


DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
MINEBD_STORAGE_PATH="/mnt/storage"
UPLOADER_CMD="/usr/lib/minebox/uploader-bg.sh"
INFO_FILENAME="fileinfo"


def get_status(backupname, allow_old=False):
    status_code = 0

    # If this is a backup that backupservice is tracking, let's use info from there.
    bsdata, bs_status_code = get_from_backupservice("status")
    if bs_status_code == 200:
        for binfo in bsdata["backup_info"]:
            if binfo["name"] == backupname:
                time_snapshot = binfo["time_snapshot"]
                files = binfo["filecount"]
                total_size = binfo["size"]
                rel_size = binfo["upload_size"]
                progress = binfo["total_progress"]
                rel_progress = binfo["upload_progress"]
                fully_available = binfo["fully_available"]
                if binfo["failed"]:
                    status = "ERROR"
                    metadata = "ERROR"
                    status_code = 503
                elif binfo["finished"]:
                    status = "FINISHED"
                    metadata = "FINISHED"
                    status_code = 200
                elif binfo["upload_size"]:
                    status = "UPLOADING"
                    metadata = "PENDING"
                    status_code = 200
                else:
                    status = "PENDING"
                    metadata = "PENDING"
                    status_code = 200

    if not status_code:
        backupfileinfo, is_finished, is_archived = get_fileinfo(backupname)

        status_code = 200
        time_snapshot = int(backupname)
        if backupfileinfo is None:
            # This backup has no file info available or doesn't exist.
            status_code = 404
            files = -1
            total_size = -1
            rel_size = -1
            progress = 100 if is_finished else 0
            rel_progress = 100 if is_finished else 0
            status = "ERROR"
            metadata = "ERROR"
            fully_available = False
        elif len(backupfileinfo) < 1:
            # Before uploads are scheduled, we find a backup but no files.
            # If we believe we are finished/archived though, the data is damaged.
            files = -1
            total_size = -1
            rel_size = -1
            progress = 0
            rel_progress = 0
            if is_archived or is_finished:
                status = "DAMAGED"
                metadata = "DAMAGED"
            else:
                status = "PENDING"
                metadata = "PENDING"
            fully_available = False
        else:
            backuplist = get_list(allow_old)
            currentidx = backuplist.index(backupname)
            if currentidx < len(backuplist) - 1:
                prev_backupfiles, prev_finished = get_files(backuplist[currentidx + 1])
            else:
                prev_backupfiles = None
            sia_filedata, sia_status_code = get_from_sia("renter/files")
            if sia_status_code == 200:
                # create a dict generated from the JSON response.
                files = 0
                total_size = 0
                total_pct_size = 0
                rel_size = 0
                rel_pct_size = 0
                fully_available = True
                sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
                for finfo in backupfileinfo:
                    if not is_archived and finfo["siapath"] in sia_map:
                        files += 1
                        fdata = sia_filedata["files"][sia_map[finfo["siapath"]]]
                        # For now, report all files.
                        # We may want to only report files not included in previous backups.
                        total_size += fdata["filesize"]
                        total_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                        if prev_backupfiles is None or not fdata["siapath"] in prev_backupfiles:
                            rel_size += fdata["filesize"]
                            rel_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                        if not fdata["available"]:
                            fully_available = False
                    elif re.match(r".*\.dat$", finfo["siapath"]):
                        files += 1
                        total_size += finfo["size"]
                        if not is_archived:
                            fully_available = False
                            current_app.logger.warn("File %s not found on Sia!",
                                                    finfo["siapath"])
                    else:
                        current_app.logger.debug('File "%s" not on Sia and not matching watched names.',
                                                 finfo["siapath"])
                # If size is 0, we report 100% progress.
                # This is really needed for relative as otherwise a backup with no
                # difference to the previous would never go to 100%.
                progress = total_pct_size / total_size * 100 if total_size else 100
                rel_progress = rel_pct_size / rel_size * 100 if rel_size else 100
                if is_archived:
                    status = "ARCHIVED"
                elif is_finished and fully_available:
                    status = "FINISHED"
                elif is_finished and not fully_available:
                    status = "DAMAGED"
                elif total_pct_size:
                    status = "UPLOADING"
                else:
                    status = "PENDING"
                if is_finished:
                    # Assume metadata is always uploaded when we are finished.
                    # As right now we report finished only if we have a .zip but no
                    # directory, and we delete the directory only after metadata
                    # upload is done, this is actually the case.
                    metadata = "FINISHED"
                elif is_archived:
                    # Archived backups that are not finished are missing metadata.
                    metadata = "DAMAGED"
                else:
                    # Otherwise, always report pending metadata.
                    metadata = "PENDING"
            else:
                current_app.logger.error("Error %s getting Sia files: %s",
                                         status_code, sia_filedata["message"])
                status_code = 503
                files = -1
                total_size = -1
                rel_size = -1
                progress = 0
                rel_progress = 0
                status = "ERROR"
                metadata = "ERROR"
                fully_available = False

    return {
      "name": backupname,
      "time_snapshot": time_snapshot,
      "status": status,
      "metadata": metadata,
      "numFiles": files,
      "size": total_size,
      "progress": progress,
      "relative_size": rel_size,
      "relative_progress": rel_progress,
    }, status_code


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
                     or isdir(join(METADATA_BASE, "old.backup.%s" % snapname)))):
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
