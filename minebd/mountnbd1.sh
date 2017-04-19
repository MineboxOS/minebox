#!/usr/bin/env bash
modprobe nbd
nbd-client -N defaultMount localhost 10811 /dev/nbd1
echo "reading 5G"
dd if=/dev/zero of=/dev/nbd1  bs=5000M count=1
echo "writing 5G"
dd if=/dev/nbd1 of=/dev/null  bs=5000M count=1
echo "reading 5G"
dd if=/dev/zero of=/dev/nbd1  bs=5000M count=1
echo "writing 5G"
dd if=/dev/nbd1 of=/dev/null  bs=5000M count=1
echo "reading 5G"
dd if=/dev/zero of=/dev/nbd1  bs=5000M count=1
echo "writing 5G"
dd if=/dev/nbd1 of=/dev/null  bs=5000M count=1
echo "reading 5G"
dd if=/dev/zero of=/dev/nbd1  bs=5000M count=1
echo "writing 5G"
dd if=/dev/nbd1 of=/dev/null  bs=5000M count=1
