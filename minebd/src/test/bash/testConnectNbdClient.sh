#!/usr/bin/env bash

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

modprobe nbd

umount /dev/nbd0
echo "Starting nbd-client with 5 mins timeout"

#
nbd-client -N defaultMount localhost 10809 /dev/nbd0 -n -t 300 -p
echo "mounting /mnt/restoretest"
mount /dev/nbd0 /mnt/restoretest
