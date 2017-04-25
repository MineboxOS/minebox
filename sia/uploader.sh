#!/usr/bin/bash

# Minebox uploader script. See README.md in this directory for more info.

# Uploading is a multi-step process (see ReadME.md for details):
# 1) Create a read-only snapshot(s) on lower disk(s).
# 2) Initiate uploads to sia where needed.
# 3) Wait for finished uploads and, save/upload the metadata.
# 4) Remove the snapshot(s).

DATADIR_MASK="/mnt/lower*/data"
METADATA_BASE="/mnt/lower1/mineboxmeta"
SIA_DIR="/mnt/lower1/sia"
SIAC="/usr/local/bin/siac"

die() {
    echo -e "$1"
    exit 1
}
LANG=C

# Step 0: Check if siad is running.
systemctl status sia > /dev/null
if [ "$?" != "0" ]; then
  die "ERROR: sia daemon needs to be running for any uploads."
fi
siasync=`$SIAC | awk '/^Synced:/ { print $2; }'`
if [ "$siasync" != "Yes" ]; then
  die "ERROR: sia seems not to be synced. Check yourself with |siac| and run again when it's synced."
fi

# Step 1: Create snapshot.
if [ -n "$1" ]; then
  snapname=""
  for subvol in $DATADIR_MASK; do
    if [ -d "$subvol/snapshots/$1" ]; then
      snapname=$1
    fi
  done
  if [ -z "$snapname" ]; then
    die "ERROR: A started backup with the name $1 does not exist."
  fi
  mode="restart"
else
  snapname=`date "+%s"`
  mode="new"
fi

loggeropts=""
# Detect if we run in the background.
# If in foreground, state according to ps has a "+" in it.
pstate=$(ps -o stat= -p $$)
pstate=${pstate//[^+]/}
if [ -n "$pstate" ]; then
  # We run in the foreground. Add -s to logger options: echo to stderr as well.
  loggeropts="-s"
fi
# Redirect stdout to syslog (via logger), and stderr to stdout (to syslog).
exec 1> >(logger $loggeropts -t $(basename $0)":"$snapname) 2>&1

if [ "$mode" = "restart" ]; then
  echo "Re-starting backup $snapname"
else
  snapname=`date "+%s"`
  echo "Flushing filesystem caches to make sure user data has been written."
  sync
  echo "Creating lower-level data snapshot(s) with name: $snapname"
  # Potentially, we should ensure that those data/ directories are actually subvolumes.
  for subvol in $DATADIR_MASK; do
    mkdir -p $subvol/snapshots
    btrfs subvolume snapshot -r $subvol $subvol/snapshots/$snapname
  done
  echo "Telling MineBD to pause (for 1.5s) to make sure no modified blocks exist with the same timestamp as in our snapshots."
  # To see all possible REST commands of MineDB, see http://localhost:8080/v1/swagger
  curl -X PUT --header 'Content-Type: application/json' --header 'Accept: text/plain' 'http://localhost:8080/v1/pause'
fi

# Step 2: Initiate needed uploads.

echo "Start downloads."
metadir="$METADATA_BASE/backup.$snapname"
if [ ! -d $metadir ]; then
  mkdir -p $metadir
fi
if [ -e $metadir/files ]; then
  rm $metadir/files
fi
uploaded_files=`$SIAC renter list | awk '/.dat$/ { print $3; }'`
# NOTE: We may have unifinished uploads but this still may not say "uploading". :(
uploading_files=`$SIAC renter list | awk '/.dat \(uploading/ { print $3; }'`
# We have a randomly named subdirectory containing the .dat files.
# As the random string is based on the wallet seed, we can be pretty sure there
# is only one and we can ignore the risk of catching multiple directories with
# the * wildcard.
for filepath in $DATADIR_MASK/snapshots/$snapname/*/*.dat; do
  # Only use files of non-zero size.
  if [ -s "$filepath" ]; then
    filename=`basename $filepath`
    sia_filename=${filename/.dat/}.`stat --format "%Y" $filepath`.dat
    if [[ $uploaded_files =~ (^|[[:space:]])"$sia_filename"($|[[:space:]]) ]]; then
      echo "$sia_filename is part of the set but already uploaded."
    elif [[ $uploading_files =~ (^|[[:space:]])"$sia_filename"($|[[:space:]]) ]]; then
      echo "$sia_filename is part of the set but the upload is already in progress."
    else
      echo "$sia_filename has to be uploaded, starting that."
      timeout 30s $SIAC renter upload $filepath $sia_filename
      if [ "$?" = "124" ]; then
        die "ERROR: upload command timed out. You may need to restart the sia daemon, see https://github.com/NebulousLabs/Sia/issues/1605 for more information. You can re-start this backup process later by calling |$0 $snapname|."
      elif [ "$?" != "0" ]; then
        die "ERROR: upload unsuccessful. Please check what the problem is. You can re-start this backup process later by calling |$0 $snapname|."
      fi
    fi
    # Add filename to the backup file list (metadata for restore).
    echo $sia_filename >> $metadir/files
  fi
done

# Step 3: Save/upload metadata.

ourfiles=`cat $metadir/files`
uploads_in_progress=0
upload_mb_total=0
upload_mb_remaining=0
calc_remaining() {
  # uploads in progress can be retrieved from `siac renter uploads` or
  # REST API /renter/files: https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#renterfiles-get
  local rx=""
  for file in $ourfiles; do
    # If rx exists and has content, add a | at the end, and always add $file but
    # escape the dots in it for use in a regular expression.
    rx=${rx:+$rx|}${file//./\\.}
  done
  # If we want more details, we may want to use `siac renter list -v` and also
  # take available/redundancy into account.
  local uploads=`$SIAC renter uploads`
  # We replace all dots by escaped dots for a proper regular expression.
  uploads_in_progress=`echo "$uploads" | awk "BEGIN { count=0 } / ($rx) \(uploading/ { count+=1; } END { print count }"`
  # We assume all file sizes are MB (smaller doesn't weigh much, and we never get larger than 40 MB)
  upload_mb_total=`echo "$uploads" | awk "BEGIN { total=0 } / MB +($rx) \(uploading/ { total+=\\$1; } END { print total }"`
  upload_mb_remaining=`echo "$uploads" | awk "BEGIN { remaining=0 } / MB +($rx) \(uploading/ { remaining+=\\$1*(100-\\$5)/100; } END { print remaining }"`
}
calc_remaining
while [ $uploads_in_progress -gt 0 ]; do
  echo "$uploads_in_progress uploads are in progress ($upload_mb_remaining of $upload_mb_total MB still to do), wait 30 minutes and see if they clear. Current time: "`date`
  sleep 30m
  calc_remaining
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


# Step 4: Remove snapshot.
echo "Removing lower-level data snapshot(s) with name: $snapname"
for snap in $DATADIR_MASK/snapshots/$snapname; do
  btrfs subvolume delete $snap
done

echo "Checking for non-processed snapshots..."
(for subvol in $DATADIR_MASK; do
  btrfs subvolume list $subvol
done) | grep 'snapshots'
if [ "$?" = "0" ]; then
  echo "There are snapshots that haven't been fully processed yet. You can finish them with |$0 <timestamp>|."
fi

echo "Backup $snapname has been uploaded."
