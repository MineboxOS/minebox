#!/bin/bash +x

# This script sets traffic shaping for the sia daemon.
# Note: Only outgoing traffic is shaped, and all non-Sia traffic is unaffected.

SETTINGS_FILE="/etc/minebox/minebox_settings.json"
ETHDEVICES=$(ip link show|awk '/^[0-9]+: (e)/ { print gensub(/:/, "", "",$2); }')

case "$1" in
  start|restart|reload)
    # Rate limit in KB/s
    RATELIMITKBS=$(cat ${SETTINGS_FILE} | python -c "import sys, json; print (json.load(sys.stdin)['sia_upload_limit_kbps'])")
    # Speed of the network port in MB/s
    LINESPEEDMBS="125"

    # Delete previous rules.
    for ethdev in $ETHDEVICES; do
      /sbin/tc qdisc del dev ${ethdev} root
    done
    /sbin/iptables -t mangle -F OUTPUT

    if [ -z "$RATELIMITKBS" -o "$RATELIMITKBS" = "0" ]; then
      # A rate limit of zero means to impose no limits.
      exit 0
    fi
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
  ;;
  stop|halt)
    # Delete previous rules.
    for ethdev in $ETHDEVICES; do
      /sbin/tc qdisc del dev ${ethdev} root
    done
    /sbin/iptables -t mangle -F OUTPUT
  ;;
  status)
    /sbin/iptables -t mangle -L OUTPUT
    for ethdev in $ETHDEVICES; do
      echo "Traffic control on ${ethdev}:"
      /sbin/tc -s -d qdisc show dev ${ethdev}
    done
  ;;
  *)
    echo "Minebox sia traffic shaping script"
    echo
    echo "Usage:"
    echo "  start, restart, reload - empty tc qdiscs and affected iptables chains and set new rules"
    echo "  stop - empty tc qdiscs and affected iptables chains"
    echo "  status - show tc qdiscs and iptables rules"
  ;;
esac
