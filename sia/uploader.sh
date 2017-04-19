#!/usr/bin/bash

# Minebox uploader script.

# Uploading is a multi-step process:
# 1) Create a read-only snapshot of the data subvolumes on all lower disks.
# 2) Unlock sia wallet if it is locked.
# 3) Initiate uploads for all non-zero-size files in the snapshot(s) whose
#    unique name does not exist in the uploaded sia files yet.
#    Create a metadata list of all files belonging to the snapshot(s).
#
#    for all files in the snapshot(s):
#        if the file size > 0:
#            sia_filename = filename + file_modification_timestamp
#            if sia_filename doesn't exist in uploaded files (sia renter list):
#                Actually upload the file to sia.
#            Add sia_filename to list of files in the backup (-> metadata).
# 4) When all uploads are done, save/upload the metadata.
#    a) Zip the file list and all sia files into a backup metadata bundle.
#    b) Upload that bundle to the metadata storage.
# 5) Remove the snapshot(s) of the data subvolume(s).
#
# Open questions:
# - Are old files on sia cleaned up or are they just timing out at some point?
