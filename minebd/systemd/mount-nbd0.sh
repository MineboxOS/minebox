#!/usr/bin/bash

if [ "`file -s /dev/nbd0`" == "/dev/nbd0: data" ]; then
  mkfs.btrfs --label Minebox-storage /dev/nbd0
fi
mkdir -p /mnt/storage
mount /dev/nbd0 /mnt/storage

