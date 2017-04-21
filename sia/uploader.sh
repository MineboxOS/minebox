#!/usr/bin/bash

# Minebox uploader script.

# Uploading is a multi-step process:
# 1) Create a read-only snapshot of the data subvolumes on all lower disks.
# 2) Unlock sia wallet if it is locked.
#    (Is this needed at all? Seems to work fine without it.)
# 3) Initiate uploads for all non-zero-size files in the snapshot(s) whose
#    unique name does not exist in the uploaded sia files yet.
#    Create a metadata list of all files belonging to the snapshot(s).
# 4) When all uploads are done, save/upload the metadata.
#    a) Zip the file list and all sia files into a backup metadata bundle.
#    b) Upload that bundle to the metadata storage.
# 5) Remove the snapshot(s) of the data subvolume(s).
#
# Open questions/tasks:
# - Are old files on sia cleaned up or are they just timing out at some point?
# - What to do with instances where uploader was prematurely terminated?
# - Upload can take over all your outgoing bandwidth (and take it for a longer
#   time after upload is said to be finished), is this a problem?
# - How do we message the MineBD to pause for 1.5s once snapshot(s) are done?
# - Do we care to have things on the upper level being snapshotted and flushed?
#   If so, how do we do that?
# - How/where to actually upload the metadata?
# - Warn/exit if siad is not running


DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
SIA_DIR="/mnt/lower1/sia"

# Step 1: Create snapshot.
if [ -n "$1" ]; then
  snapname=""
  for subvol in $DATADIR_MASK; do
    if [ -d "$subvol/snapshots/$1" ]; then
      snapname=$1
    fi
  done
  if [ -z $snapname ]; then
    echo "A started backup with the name $1 does not exist."
    exit 1
  fi
  echo "Re-starting backup $snapname"
else
  snapname=`date "+%s"`
  echo "Creating lower-level data snapshot(s) with name: $snapname"
  # Potentially, we should ensure that those data/ directories are actually subvolumes.
  for subvol in $DATADIR_MASK; do
    mkdir -p $subvol/snapshots
    btrfs subvolume snapshot -r $subvol $subvol/snapshots/$snapname
  done
fi

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
metadir="$METADATA_BASE/backup.$snapname"
if [ ! -d $metadir ]; then
  mkdir -p $metadir
fi
if [ -e $metadir/files ]; then
  rm $metadir/files
fi
uploaded_files=`siac renter list | awk '/.dat$/ { print $3; }'`
uploading_files=`siac renter list | awk '/.dat \(uploading/ { print $3; }'`
uploads_in_progress=0
for filepath in $DATADIR_MASK/snapshots/$snapname/*.dat; do
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

ourfiles=`cat $metadir/files`
while [ $uploads_in_progress -gt 0 ]; do
  echo "$uploads_in_progress uploads are in progress, wait 30 minutes and see if they clear. Current time: "`date`
  sleep 30m
  # uploads in progress can be retrieved from `siac renter uploads` or
  # REST API /renter/files: https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#renterfiles-get
  uploads_in_progress=0
  files_in_progress=`siac renter uploads | awk '/.dat($| )/ { print $3; }'`
  for file in $files_in_progress; do
    if [[ $ourfiles =~ (^|[[:space:]])"$file"($|[[:space:]]) ]]; then
      (( uploads_in_progress += 1 ))
    fi
  done
done

# Copy .sia files to metadata directory.
for file in $ourfiles; do
  if [ ! -e $metadir/$file.sia ]; then
    cp $SIA_DIR/renter/$file.sia $metadir/
  fi
done
# We don't need the files list there, it's now implied from the list of .sia files.
rm "$metadir/files"
# Create a bundle of all metadata for this backup.
pushd $METADATA_BASE
if [ -e "backup.$snapname.zip" ]; then
  rm "backup.$snapname.zip"
fi
zip -r9 "backup.$snapname.zip" "backup.$snapname/"
if [ -e "backup.$snapname.zip" ]; then
  rm -rf "backup.$snapname"
fi
popd
# Upload metadata bundle.
echo "TBD: Upload metadata."
# TBD


# Step 5: Remove snapshot.
echo "Removing lower-level data snapshot(s) with name: $snapname"
for snap in $DATADIR_MASK/snapshots/$snapname; do
  btrfs subvolume delete $snap
done

echo "Checking for non-processed snapshots..."
(for subvol in $DATADIR_MASK; do
  btrfs subvolume list $subvol
done) | grep 'snapshots'
if [ "$?" = "0" ]; then
  echo "There are snapshots that haven't been fully processed yet. You can finish them with |uploader.sh <timestamp>|."
fi

echo "Backup $snapname has been uploaded."
