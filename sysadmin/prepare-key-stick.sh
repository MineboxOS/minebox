#!/bin/bash -e

KEYDEVICE=${KEYDEVICE:-disk/by-id/usb-Generic_Flash_Disk_*-0\:0-part1}

echo "Preparing Minebox Key USB Stick:"
echo "Formatting to btrfs..."
/usr/sbin/mkfs.btrfs /dev/${KEYDEVICE} --label MINEBOXKEY
echo "Done, the USB stick should be ready to hold a Minebox key."
