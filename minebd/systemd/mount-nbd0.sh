#!/usr/bin/bash

nbdevice="/dev/nbd0"
mountpath="/mnt/storage"

if [ "`file -s $nbdevice`" == "$nbdevice: data" ]; then
  mkfs.btrfs --label Minebox-storage $nbdevice
fi
mkdir -p $mountpath
mount $nbdevice $mountpath

# Create subvolume if it doesn't exist.
btrfs subvolume list $mountpath | grep 'rockons' > /dev/null
if [ "$?" != "0" ]; then
  btrfs subvolume create $mountpath/rockons
fi
