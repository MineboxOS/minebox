#!/bin/bash

KEYDEVICE=${KEYDEVICE:-disk/by-id/usb-Generic_Flash_Disk_*-0\:0-part1}

echo "Preparing Minebox Key USB Stick:"
echo "Formatting to ext4..."
/usr/sbin/mkfs.ext4 /dev/${KEYDEVICE}
echo "Setting label..."
/usr/sbin/e2label /dev/${KEYDEVICE} MINEBOXKEY
echo "Done, the USB stick should be ready to hold a Minebox key."
