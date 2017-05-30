#!/usr/bin/bash

# For demo purposes, this is a script to replace siac in uploader.

SERVER_URI=${SERVER_URI:-"http://localhost:8050/v1/"}
SIA_DIR=${SIA_DIR:-"/mnt/lower1/sia"}
MINEBD_URL=${MINEBD_URL:-"http://localhost:8080/v1/"}
MINEBD_AUTH_PWD=`cat /etc/minebox/local-auth.key`
MINEBD_AUTH_USER="user"

# We need to fetch a token from MineBD to feed to the metadata service.
SERVER_TOKEN=`curl --fail --silent --user $MINEBD_AUTH_USER:$MINEBD_AUTH_PWD \
                   --header 'Content-Type: application/json' \
                   --header 'Accept: text/plain' \
                   "${MINEBD_URL}auth/getMetadataToken"`
if [ "$?" != "0" ]; then
  echo "ERROR: Cannot reach MineBD to get a metadata token."
  exit 1
fi

case "$1" in
renter)
  case "$2" in
  delete)
    echo "Not implmented."
    exit 1
    ;;
  download)
    # TODO: Need to return failure if download isn't successful.
    curl --fail --header "X-Auth-Token: ${SERVER_TOKEN}" -o $4 ${SERVER_URI}file/download/$3
    if [ "$?" != "0" ]; then
      exit 1
    fi
    ;;
  downloads)
    echo "No files are downloading."
    ;;
  list)
    # TODO: Need to return failure if list isn't successful.
    curl --fail --silent --show-error --header "X-Auth-Token: ${SERVER_TOKEN}" ${SERVER_URI}file/list
    if [ "$?" != "0" ]; then
      exit 1
    fi
    # format (leading line is not needed for uploader.sh):
    #Tracking 3 files:
    # 21.46 MB  minebox_v1_0.1492628694.dat
    #  5.56 MB  minebox_v1_15.1492711080.dat
    # 41.94 MB  minebox_v1_15.1492778280.dat
    ;;
  upload)
    # TODO: Need to return failure if upload isn't successful.
    curl --fail --header "X-Auth-Token: ${SERVER_TOKEN}" --upload-file $3 ${SERVER_URI}file/$4
    if [ "$?" != "0" ]; then
      exit 1
    fi
    touch $SIA_DIR/renter/$4.sia
    ;;
  uploads)
    echo "No files are uploading."
    ;;
  *)
    echo "Usage: demosiac.sh renter [command]"
    echo
    echo "Available Commands:"
    echo "  delete       Delete a file (not implemented yet)"
    echo "  download     Download a file (sync download, not queuing like siac)"
    echo "  downloads    View the download queue (always empty, see above)"
    echo "  list         List the status of all files (only lists files, no status)"
    echo "  upload       Upload a file (sync upload, not queuing like siac)"
    echo "  uploads      View the upload queue (always empty, see above)"
    echo
    ;;
  esac
;;
*)
  echo "Usage: demosiac.sh [command]"
  echo
  echo "Available Commands:"
  echo "  renter          Perform renter actions"
  echo
  ;;
esac
