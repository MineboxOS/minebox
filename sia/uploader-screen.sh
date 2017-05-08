#!/usr/bin/bash

# Run the Minebox uploader script as a daemon.

if [ ! -e "/usr/bin/screen" ]; then
  echo "screen is not installed. Please run |yum install screen| before calling this daemon."
fi

screen -dmS uploader bash -c "~/minebox-client-tools_vm/sia/uploader.sh $@; read -p 'Press [ENTER] to continue/terminate ' -s"

# Give us a little bit of time so screen is sure to have created the "window".
sleep 1s

screen -list uploader
