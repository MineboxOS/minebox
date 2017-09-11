#!/usr/bin/bash

# Script for setting/getting the Minebox encryption key.

# File to store the encryption key.
KEY_FILE="/etc/minebox/minebox.key"

# Make sure we are root.
if [[ $EUID > 0 ]]; then
  echo "ERROR: You have to be root to run this script."
  exit 1
fi

case "$1" in
set)
  # Make sure a key is set (catch if Python hands over "None" for some reason).
  newkey="$2"
  if [ -z "$newkey" -o "$newkey" = "None" ]; then
    echo "ERROR: You cannot set an empty key."
    exit 1
  fi
  # Make sure the path to store the key file exists.
  keypath=`dirname $KEY_FILE`
  if [ ! -d "$keypath" ]; then
    mkdir -p $keypath
  fi
  # Check that the key file is a normal file if it exists.
  if [ -e $KEY_FILE -a ! -f $KEY_FILE ]; then
    echo "ERROR: Key file is not a normal file."
    exit 1
  fi
  # Make sure we do not overwrite an existing key.
  if [ -f $KEY_FILE ]; then
    curkey=`cat $KEY_FILE`
    # Remove leading whitespace characters.
    curkey="${curkey#"${curkey%%[![:space:]]*}"}"
    # Remove trailing whitespace characters.
    curkey="${curkey%"${curkey##*[![:space:]]}"}"
    if [ -n "$curkey" ]; then
      echo "ERROR: The is a non-empty key already set, you cannot set a different one."
      exit 1
    fi
  fi
  # Write the key to the file and set its attributes so only root can read it.
  echo -n "$newkey" > $KEY_FILE
  chown root:root $KEY_FILE
  chmod gu+r,o-r,a-wx $KEY_FILE
  # Also set the full key as root password
  echo "root:$newkey" | chpasswd
  # We should be done!
  ;;
get)
  # Check that the key file is a normal file if it exists.
  if [ -e $KEY_FILE -a ! -f $KEY_FILE ]; then
    echo "ERROR: Key file is not a normal file."
    exit 1
  fi
  # Check if key file exists.
  if [ ! -f $KEY_FILE ]; then
    echo "ERROR: Key file does not exist."
    exit 1
  fi
  # Read the key.
  curkey=`cat $KEY_FILE`
  # Remove leading whitespace characters.
  curkey="${curkey#"${curkey%%[![:space:]]*}"}"
  # Remove trailing whitespace characters.
  curkey="${curkey%"${curkey##*[![:space:]]}"}"
  # Print the key to STDOUT.
  echo "$curkey"
  ;;
*)
  echo "Usage: mbkey.sh <command> [<args>]"
  echo
  echo "Commands:"
  echo "  set <key>     Set new Minebox encryption key to given value (only valid if current key is empty)."
  echo "  get           Return the currently set Minebox encryption key."
  echo
  ;;
esac
