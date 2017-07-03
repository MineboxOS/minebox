#!/usr/bin/bash

# Script for launching Minebox backup service.
case "$1" in
start)
  cd /usr/lib/minebox
  source mbvenv/bin/activate
  mbvenv/backup-service.py
  ;;
ping)
  curl --fail "http://localhost:5100/ping"
  ;;
trigger)
  curl --fail "http://localhost:5100/trigger"
  ;;
*)
  echo "Script for controlling the Minebox Backup Service"
  echo
  echo "Usage: backup-service.sh <command>"
  echo
  echo "Commands:"
  echo "  start         Start the backup service."
  echo "  ping          Send a ping to the backup service."
  echo "  trigger       Trigger a new backup run."
  echo
  ;;
esac
