#!/usr/bin/bash

nbdevice="/dev/nbd0"
mountpath="/mnt/storage"

if [ "`file -s $nbdevice`" == "$nbdevice: data" ]; then
  # Use --nodiscard to not do a whole-device trim as nbd is pristine anyhow.
  mkfs.btrfs --nodiscard --label Minebox-storage $nbdevice
fi
mkdir -p $mountpath
mount $nbdevice $mountpath

# Enable quotas if they are not enabled (required by Rockstor).
btrfs qgroup show $mountpath 2>&1 | grep 'qgroupid' > /dev/null
if [ "$?" != "0" ]; then
  btrfs quota enable $mountpath
fi

# Create subvolume if it doesn't exist.
btrfs subvolume list $mountpath | grep 'rockons' > /dev/null
if [ "$?" != "0" ]; then
  btrfs subvolume create $mountpath/rockons
fi
