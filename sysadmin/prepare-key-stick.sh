#!/bin/bash

echo "Preparing Minebox Key USB Stick:"
echo "Formatting to ext4..."
/usr/sbin/mkfs.ext4 /dev/disk/by-id/usb-Generic_Flash_Disk_*-0\:0-part1
echo "Setting label..."
/usr/sbin/e2label /dev/disk/by-id/usb-Generic_Flash_Disk_*-0\:0-part1 MINEBOXKEY
echo "Done, the USB stick should be ready to hold a Minebox key."
