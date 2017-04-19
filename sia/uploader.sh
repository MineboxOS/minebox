#!/usr/bin/bash

# Minebox uploader script.

# Uploading is a multi-step process:
# 1) Create a read-only snapshot of the data subvolumes on all lower disks.
# 2) Unlock sia wallet if it is locked.
# 3) Initiate uploads for all non-zero-size files in the snapshot(s) whose
#    unique name does not exist in the uploaded sia files yet.
#    Create a metadata list of all files belonging to the snapshot(s).
# 4) When all uploads are done, save/upload the metadata.
#    a) Zip the file list and all sia files into a backup metadata bundle.
#    b) Upload that bundle to the metadata storage.
# 5) Remove the snapshot(s) of the data subvolume(s).
#
# Open questions:
# - Are old files on sia cleaned up or are they just timing out at some point?


# Step 1: Create snapshot.
snapname=`date "+%s"`
echo "Creating lower-level data snapshot(s) with name: $snapname"
# Potentially, we should ensure that those data/ directories are actually subvolumes.
for subvol in /mnt/lower*/data; do
  mkdir -p $subvol/snapshots
  btrfs subvolume snapshot -r $subvol $subvol/snapshots/$snapname
done


# Step 2: Unlock sia wallet.

echo "TBD: Unlocking wallet."
# This can use the REST API to make it fully automated:
# https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#walletunlock-post
# We would need a copy of the wallet seed for that, though.
# Otherwise, `siac wallet unlock` blocks waiting for user input.
# This roadmap ticket would help as well:
# https://trello.com/c/yRFaIgLb/65-enable-the-wallet-to-unlock-at-startup-without-user-intervention-advanced-less-secure-feature-primarily-for-hosts

# TBD

# TODO: Should we also check allowance here?


# Step 3: Initiate needed uploads.

echo "Start downloads."
metadir="/mnt/lower1/mineboxmeta/backup.$snapname"
mkdir -p $metadir
uploaded_files=`siac renter list | awk '/.dat$/ { print $3; }'`
uploading_files=`siac renter list | awk '/.dat \(uploading/ { print $3; }'`
uploads_in_progress=0
for filepath in /mnt/lower*/data/snapshots/$snapname/*.dat; do
  # Only use files of non-zero size.
  if [ -s "$filepath" ]; then
    filename=`basename $filepath`
    sia_filename=${filename/.dat/}.`stat --format "%Y" $filepath`.dat
    if [[ $uploaded_files =~ (^|[[:space:]])"$sia_filename"($|[[:space:]]) ]]; then
      echo "$sia_filename is part of the set but already uploaded."
    elif [[ $uploading_files =~ (^|[[:space:]])"$sia_filename"($|[[:space:]]) ]]; then
      echo "$sia_filename is part of the set but the upload is already in progress."
      (( uploads_in_progress += 1 ))
    else
      echo "$sia_filename has to be uploaded, starting that."
      siac renter upload $filepath $sia_filename
      (( uploads_in_progress += 1 ))
    fi
    # Add filename to the backup file list (metadata for restore).
    echo $sia_filename >> $metadir/files
  fi
done

# Step 4: Save/upload metadata.

while [ $uploads_in_progress -gt 0 ]; do
  echo "$uploads_in_progress uploads are in progress, wait 30 minutes and see if they clear."
  sleep 30m
  # uploads in progress can be retrieved from `siac renter uploads` or
  # REST API /renter/files: https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#renterfiles-get
  uploads_in_progress=0
  files_in_progress=`siac renter uploads | awk '/.dat($| )/ { print $3; }'`
  ourfiles=`cat $metadir/files`
  for file in $files_in_progress; do
    if [[ $ourfiles =~ (^|[[:space:]])"$file"($|[[:space:]]) ]]; then
      (( uploads_in_progress += 1 ))
    fi
  done
done

echo "TBD: Save and upload metadata."

# TBD


# Step 5: Remove snapshot.
echo "(NOT) Removing lower-level data snapshot(s) with name: $snapname"
for snap in /mnt/lower*/data/snapshots/$snapname; do
  echo btrfs subvolume delete $snap
done
