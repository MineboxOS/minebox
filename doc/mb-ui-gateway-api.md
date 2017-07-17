# Minebox UI Gateway (MUG)

The static html/js/css UI layer accesses underlying system functionality via the
Minebox UI Gateway (MUG) via a RESTful API.
The MUG hands off most of the tasks to other services (often via local REST
calls) internally, though some assembly of information and conversions are done
in the MUG. Any UI interaction with Minebox-specific features goes through the
MUG.

The MUG is verifying the user authentication/login with Rockstor and therefore
needs to see the cookies of Rockstor's UI. The MUG reuses Rockstor's TLS
certificates and exposes CORS headers so that the UI can access it from the
browser.

All endpoints give JSON output replies and the usual standard of 2xx status
codes on success, 4xx on input error, 5xx on server error, and a "message" field
in the output JSON on all errors.

Implemented features:

* Key management functions for setup UI
* Status of backups
* Sytem status
* Sia status: consensus, contracts, trancactions
* Wallet: balance(s), receive address, send coins

Planned features:

* *must* Restore status
* *later* Rockstor dashboard integration
* *later* adding/removing disks at runtime (via Rockstor UI?)

Note we differentiate the terms **snapshot** and **backup**:

*  **snapshots** of (single) subvolumes are done on **upper** and esp. of the
   data subvolume on **lower**.
*  A **backup** contains a group up snapshots of the **upper** and is built with
   the help of a data snapshot in **lower** but is a consistent set of data that
   is being uploaded to the Sia network.


## REST API
### GET /
Returns an overview list of supported REST endpoints.

### GET /backup/list
Returns a newest-first list of backups which have been made by the system.

```json
[
  "1493807150",
  "1493682341",
  "1493472128"
]
```

### GET /backup/1493807150/status

Returns the status of a single backup.  
The `time_snaphost` is the time the backup has been started (by making the
snapshots). With current conventions, that time (in string form) also doubles as
the backup name, butbackup naming conventions could change, so use the
`time_snapshot` field when you need a time.  
The `relative_*` fields are in difference to the preceding backup, so about what
has to be freshly uploaded for this backup, whereas the other fields are for the
total amounts for this backup.

```json
{
  "name": "1493807150",
  "time_snapshot": 1493807150,
  "status": "PENDING|UPLOADING|FINISHED|DAMAGED|ERROR",
  "metadata": "PENDING|UPLOADING|FINISHED|ERROR",
  "numFiles": 23,
  "size": 403678324,
  "progress": 44.3,
  "relative_size": 21487245,
  "relative_progress": 87.5
}
```

### GET /backup/latest/status

Returns the status of the most recent backup (same output as above single-backup
status - actually implemented as a special case of that one).

### GET /backup/all/status

Bulk operation for listing all statuses (newest-first list of single-backup
status outputs).

```json
[
  {"name": "1493807150", ...},
  ...
]
```

### POST /backup/start

Trigger a backup run. Takes no input fields, and just gives a "message" field in
the JSON on success.

### GET /key/status

Returns the current key status. `Not implemented yet!`

```json
{
  "pubkey": "0123456789ABC", //ec pubkey/address or checksum
  "metadata": "BACKED_UP|NOT_FOUND",
  ""
}
```

### GET /key/generate

Just randomly generates a key, transcribed into a set of 12 words.

```json
[
  "randomly",
  "generated",
  "list", "of", "twelve", "words"
]
```

### POST /key/verify

Verifies a key. `Not implemented yet!`  
Debatable if its worthwhile to do it via client-side computed hash?

Input:

```json
[
  "randomly",
  "generated",
  "list", "of", "twelve", "words"
]
```

Output:

```json
{
  "keystatus": "INVALID|MATCH|NO_MATCH"
}
```

### PUT /key

Sets a new key. Only works on a system that has no key set yet.  
If the key was used previously and backups exist in the Minebox/Sia network, a
restore of data will be started automatically (if there is no error receiving
the metadata). Otherwise, a new storage will be set up.  
If the local system already has a key, this call will return an error 400.  
`keystatus reply not implemented yet!`

Input:

```json
[
  "randomly",
  "generated",
  "list", "of", "twelve", "words"
]

Output:

```json
{
  "message": "a string describing the status",
  "keystatus": "FRESH|RESTORING|ERROR_EXISTING|ERROR_METADATA"
}
```

### POST /key

TBD later, initiates a complicated key-change procedure, needs the original
key/root key to auth. `Not implemented yet!`

