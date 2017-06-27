# Various tools around backups, mostly to get info about them.

from flask import current_app
from os.path import isfile, isdir, join
from glob import glob
from zipfile import ZipFile
import re
from connecttools import getFromSia


DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
UPLOADER_CMD="/usr/lib/minebox/uploader-bg.sh"


def getStatus(backupname):
    backupfiles, is_finished = getFiles(backupname)
    if backupfiles is None:
        return {"message": "No backup found with that name."}, 404

    status_code = 200
    if len(backupfiles) < 1:
        # Before uploads are scheduled, we find a backup but no files.
        files = -1
        total_size = -1
        rel_size = -1
        progress = 0
        rel_progress = 0
        status = "PENDING"
        metadata = "PENDING"
        fully_available = False
    else:
        backuplist = getList()
        currentidx = backuplist.index(backupname)
        if currentidx > 0:
            prev_backupfiles, prev_finished = getFiles(backuplist[currentidx - 1])
        else:
            prev_backupfiles = None
        sia_filedata, sia_status_code = getFromSia('renter/files')
        if sia_status_code == 200:
            # create a dict generated from the JSON response.
            files = 0
            total_size = 0
            total_pct_size = 0
            rel_size = 0
            rel_pct_size = 0
            fully_available = True
            sia_map = dict((d["siapath"], index) for (index, d) in enumerate(sia_filedata["files"]))
            for fname in backupfiles:
                if fname in sia_map:
                    files += 1
                    fdata = sia_filedata["files"][sia_map[fname]]
                    # For now, report all files.
                    # We may want to only report files not included in previous backups.
                    total_size += fdata["filesize"]
                    total_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                    if prev_backupfiles is None or not fdata["siapath"] in prev_backupfiles:
                        rel_size += fdata["filesize"]
                        rel_pct_size += fdata["filesize"] * fdata["uploadprogress"] / 100
                    if not fdata["available"]:
                        fully_available = False
                elif re.match(r'.*\.dat$', fname):
                    files += 1
                    fully_available = False
                    current_app.logger.warn('File %s not found on Sia!', fname)
                else:
                    current_app.logger.debug('File "%s" not on Sia and not matching watched names.', fname)
            # If size is 0, we report 100% progress.
            # This is really needed for relative as otherwise a backup with no
            # difference to the previous would never go to 100%.
            progress = total_pct_size / total_size * 100 if total_size else 100
            rel_progress = rel_pct_size / rel_size * 100 if rel_size else 100
            # We don't upload metadata atm, so always flag it as pending.
            metadata = "PENDING"
            if is_finished and fully_available:
                status = "FINISHED"
            elif is_finished and not fully_available:
                status = "DAMAGED"
            elif total_pct_size:
                status = "UPLOADING"
            else:
                status = "PENDING"
        else:
            current_app.logger.error("Error %s getting Sia files: %s", status_code, str(sia_filedata))
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
      "time_snapshot": backupname,
      "status": status,
      "metadata": metadata,
      "numFiles": files,
      "size": total_size,
      "progress": progress,
      "relative_size": rel_size,
      "relative_progress": rel_progress,
    }, status_code


def getList():
    backuplist = [re.sub(r'.*backup\.(\d+)(\.zip)?', r'\1', f)
                  for f in glob(join(METADATA_BASE, "backup.*"))
                    if (isfile(f) and f.endswith(".zip")) or
                       isdir(f) ]
    backuplist.sort()
    return backuplist


def getFiles(backupname):
    backupfiles = None
    is_finished = None
    zipname = join(METADATA_BASE, "backup.%s.zip" % backupname)
    dirname = join(METADATA_BASE, "backup.%s" % backupname)
    if isfile(zipname):
        backupfiles = []
        is_finished = True
        with ZipFile(zipname, 'r') as backupzip:
            backupfiles = [re.sub(r'.*backup\.\d+\/(.*)\.sia$', r'\1', f)
                           for f in backupzip.namelist()
                             if f.endswith(".sia")]
    elif isdir(dirname):
        backupfiles = []
        is_finished = False
        flist = join(dirname, "files")
        if isfile(flist):
            with open(flist) as f:
                backupfiles = [line.rstrip('\n') for line in f]
    return backupfiles, is_finished
