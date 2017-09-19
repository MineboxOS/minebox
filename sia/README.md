# sia tools
## systemd/
Contains the service file(s) needed to run the sia daemon (and potentially other
sia-related processes, like the backup service).

## cron.d/
Contains the cron tabs needed for sia and related tools (e.g. backup service).

## backup-service.py
The Minebox backup service (previously "uploader"). Handles creation of a
consistent backup of the lower layer of the Minebox storage.
This is a background service with a REST API that starts long-running threads
(as it waits for uploads to complete) and also takes care of some other
sia/backup-related tasks like restarting backups after a precess termination or
reboot, unlocking the wallet or even setting up the sia environment on a new box.
Its "ping" endpoint is being called by cron every 5 minutes to trigger those
tasks.
 
![](https://bitbucket.org/mineboxgmbh/minebox-client-tools/downloads/uploader.sequence.png){width=100%}

## backup-service
This script has commands to start the backup service as well as to issue a
"ping" or trigger a new backup "manually" from the command line. Execute it
without options to get more help.

## backuptools.py
A python module with functions needed by the backup service.

## demosiad.py
A small Flask service to provide demo sia output to the MUG when in DEMO mode.
Should run from the MUG virtualenv.

## demosiad.sh
A script to run demosiad.py in the MUG virtualenv (launched from systemd).


## Uploader details

Uploading is a multi-step process:

1. Create a read-only snapshot of the data subvolumes on all lower disks.
2. Initiate uploads for all non-zero-size files in the snapshot(s) whose
   unique name does not exist in the uploaded sia files yet.
   Create a metadata list of all files belonging to the snapshot(s).
3. When all uploads are done, save/upload the metadata.  
   a. Zip the file list and all sia files into a backup metadata bundle.  
   b. Upload that bundle to the metadata storage.
4. Remove the snapshot(s) of the data subvolume(s).

Questions & tasks:

* Are old files on sia cleaned up or are they just timing out at some point?  
  --> We should only keep the most recent finished backup.
* Do we care to have things on the upper level being snapshotted?
  If so, how do we do that?  
  --> The backup-service does that as the first step of doing a backup.
* How/where to actually upload the metadata?  
  --> REST API @ minebox.io, with Authentication, encryption, signing. TBD.
* Do we need to include all of renter/ in the backup metadata bundles (what
  exactly do we need for restore)?  
  --> Right now, as long as sia runs, the sia files get updated, and we need
      the latest version of that at the time of restoring. This should get
      fixed in a few weeks. Also see
      https://forum.sia.tech/topic/157/insufficient-hosts-to-recover-file/8
* Does timestamp really make sense for sia files or would md5sum be better?  
  --> No, because comparing all blocks of the whole snapshot for backing is
      too slow (needs sequential reads of all data).
* Do we run uploader as a permanent-on daemon or one-shot process?  
  --> backup-service daemon
* What to do with instances where uploader was prematurely terminated?
  How do we handle previously unfinished uploads (when/how do we restart, do
  we run multiple forked processes for them, etc.)?  
  --> Right now, the oldest and the newest backup run are restarted by the
      backup-service.
* How do we get/save the wallet seed?  
  --> Minebd takes care of that
* Are there circumstances where the wallet needs to be unlocked when uploading?  
  --> We need some service to set/manage allowance!
* Do we wait for full 3x redundancy or may we call or backup "done" earlier?  
  --> Right now, stick with 100% redundancy.
* We need (web) UI for SIA details and uploader progress, how do we integrate
  that?  
  --> Target to have that integrated in Minebox UI.
* Upload can take over all your outgoing bandwidth (and take it for a longer
  time after upload is said to be finished), is this a problem?
* Can we do some kind of traffic shaping/prioritization of sia to ensure the
  system can still do other things while uploading?  
  --> We can either use tc in some way, or trickle: http://www.tecmint.com/manage-and-limit-downloadupload-bandwidth-with-trickle-in-linux/3/  
  --> In any case, we probably need to provide a setting for the user to set
      available bandwidth and then take e.g. 70% of that for limiting sia.
* Can we ensure decent upload speeds? With VM traffic shaped to 256 KiB/s
  (~2 MBit/s) I got about 1 MB / minute of actual data uploaded (avg over 3 h).
  If that speed continues over 24 h, that makes ~1.5 GB/day.
* TODO: Report when finished (via email?)
* TODO: Should we also check allowance before uploading?

## Unlocking wallet

This can use the REST API to make it fully automated:
https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#walletunlock-post
We would need a copy of the wallet seed for that, though.
Otherwise, `siac wallet unlock` blocks waiting for user input.
This roadmap ticket would help as well:
https://trello.com/c/yRFaIgLb/65-enable-the-wallet-to-unlock-at-startup-without-user-intervention-advanced-less-secure-feature-primarily-for-hosts
