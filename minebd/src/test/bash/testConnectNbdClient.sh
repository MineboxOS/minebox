#!/usr/bin/env bash
systemctl stop  nbd-server
sudo modprobe nbd

#_term() {
#  echo "Caught SIGTERM signal!"
#  kill -TERM "$child" 2>/dev/null
#}
#
#trap _term SIGTERM
#
#echo "Starting nbd-client"

nbd-client -N defaultMount localhost 10809 /dev/nbd0 -n

#child=$!
#wait "$child"
