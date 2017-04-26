#!/usr/bin/bash

# Run the Minebox uploader script in the background.

uploader=`dirname $0`/uploader.sh

# Run uploader in the background and detach from this process so it keeps running after all parents quit.
$uploader $@ &
disown
