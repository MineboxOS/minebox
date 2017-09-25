#!/bin/bash +x

# This script sets traffic shaping for the sia daemon.
# Note: Only outgoing traffic is shaped, and all non-Sia traffic is unaffected.

ETHDEVICES="eno1 eno2"
# Rate limit in KB/s
RATELIMITKBS="200"
# Speed of the network port in MB/s
LINESPEEDMBS="12"

# Delete previous rules.
for ethdev in $ETHDEVICES; do
  /sbin/tc qdisc del dev ${ethdev} root
done
/sbin/iptables -t mangle -F OUTPUT

# Mark traffic from the "sia" user with a label "10".
/sbin/iptables -t mangle -A OUTPUT --match owner --uid-owner sia -j MARK --set-mark 10
for ethdev in $ETHDEVICES; do
  # "root" means outgoing/egress, not classified traffic goes to rule 1:20
  /sbin/tc qdisc add dev ${ethdev} root handle 1:0 htb default 20
  # Class 1:10 - Sia. Limit with rate, set lower priority.
  /sbin/tc class add dev ${ethdev} parent 1:0 classid 1:10 htb rate ${RATELIMITKBS}kbps prio 3
  # Class 1:20, common traffic. Limit with network port speed, high priority.
  /sbin/tc class add dev ${ethdev} parent 1:0 classid 1:20 htb rate ${LINESPEEDMBS}mbps prio 0
  # Actually filter label "10" to class "1:10".
  /sbin/tc filter add dev ${ethdev} parent 1:0 prio 3 protocol ip handle 10 fw flowid 1:10
done
