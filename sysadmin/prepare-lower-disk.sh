#!/bin/bash

LANG=C
DISK_DEV=$1
# first-gen Minebox used MBLower here
LABEL_BASE="StorageLower"

if [ ! -b "${DISK_DEV}" ]; then
  echo "Please call this script with a disk as parameter. '${DISK_DEV}' is not a disk or block device."
  exit 1
fi

if [ ! -b "${DISK_DEV}" ]; then
  echo "Please call this script with a disk as parameter. '${DISK_DEV}' is not a disk or block device."
  exit 1
fi

if [ ! -e "/usr/sbin/sgdisk" ]; then
  echo "gdisk is not installed, we need to install it."
  /usr/bin/yum install -y gdisk
fi

PART1_GUID=$(/usr/sbin/sgdisk -i 1 ${DISK_DEV}|grep -oP "(?<=Partition unique GUID: )[^ ]+")

if [ -n "${PART1_GUID}" ]; then
  echo "The disk '${DISK_DEV}' is not empty, please use |sgdisk -o ${DISK_DEV}| to clear all partitions if you really want to use it."
  exit 1
fi

DISK_SECTORS=$(/usr/sbin/sgdisk -p ${DISK_DEV}|grep -oP "(?<=Total free space is )[^ ]+")

# Add partitions that are 1M, 4G and rest of the disk in size. Types are biosboot,swap,linux-fs
/usr/sbin/sgdisk -n 1:0:1M -n 2:0:+4G -n 3:0:0 -t 1:EF02 -t 2:8200 -t 3:8300 ${DISK_DEV}

# Find out the lower number to use for the label.
LOWERNUM=1
for i in 1 2 3 4 5 6 7 8; do
  if [ ! -e "/dev/disk/by-label/${LABEL_BASE}$i" ]; then
    LOWERNUM=$i
    break
  fi
done

# To specify partitions, it's easiest to make sure we are using the original dev file (/dev/sdX).
ORIGDEV=$(readlink -f ${DISK_DEV})

# Create swap area and record UUID.
SWAP_GUID=$(mkswap ${ORIGDEV}2|grep -oP "(?<=no label, UUID=)[^ ]+")

# Format main partition (#3) as BTRFS and record UUID.
# The echo is there to cut whitespace.
MAIN_GUID=$(echo $(mkfs.btrfs -f --nodiscard --label ${LABEL_BASE}${LOWERNUM} ${ORIGDEV}3|grep  -oP '(?<=UUID:) +[^ ]+'))

LOWERPATH="/mnt/lower${LOWERNUM}"
mkdir -p ${LOWERPATH}

#Add entries to fstab
echo >> /etc/fstab
echo "# Automatically added for capacity box disk ${DISK_DEV}" >> /etc/fstab
echo "UUID=${SWAP_GUID} swap                    swap    defaults,nofail 0 0" >> /etc/fstab
echo "UUID=${MAIN_GUID} ${LOWERPATH}             btrfs   defaults,nofail 0 0" >> /etc/fstab

mount ${LOWERPATH}
#btrfs subvolume create ${LOWERPATH}/lower
btrfs subvolume create ${LOWERPATH}/data
btrfs subvolume create ${LOWERPATH}/sia
# Minebox used mineboxmeta here
btrfs subvolume create ${LOWERPATH}/metadata
chown -R sia:sia ${LOWERPATH}/sia
