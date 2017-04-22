#!/usr/bin/env bash
modprobe nbd
nbd-client -N defaultMount localhost 10811 /dev/nbd1
echo "writing 1G"
time dd if=/dev/zero of=/dev/nbd1  bs=10M count=100
echo "reading 1G"
time dd if=/dev/nbd1 of=/dev/null  bs=10M count=100
