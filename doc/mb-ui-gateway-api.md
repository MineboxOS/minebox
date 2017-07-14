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

###GET /backup/list
Returns a list of snapshots which have been made by the system
```json
[
  "1493807150",
  "1493807151",
  "1493807152"
]
```


###GET /backup/1493807150/status
returns the status of a single snapshot

```json
{
  "progress": 44.3,
  "status": "UPLOADING|PENDING|FINISHED|DAMAGED|ERROR",
  "metadata": "UPLOADING|PENDING|FINISHED|ERROR",
  "numFiles": 23
}
```
###GET /backup/all/status
bulk operation for listing all statuses
```
[{same as status but a list of items},...]
```

###GET /key/status
returns the current key status
```json
{
"pubkey": "0123456789ABC", //ec pubkey/address or checksum
"metadata": "BACKED_UP|NOT_FOUND",
""
}
```
###GET /key/generate
just randomly generates a set of keys
```json
[
  "randomly",
  "generated",
  "list", "of", "words"
]
```
###POST /key/verify
verifies a key. debatable if its worthwhile to do it via client-side computed hash?
```json
[
  "randomly",
  "generated",
  "list", "of", "words"
]
```
returns
```json
{"keystatus": "INVALID|MATCH|NO_MATCH"}
```
 
###PUT /key
body like /key/generate
returns the acceptance status depending on:
 * was there already a key
 * was there metadata
 * could the metadata be retrieved
```json
{"keystatus": "FRESH|RESTORING|ERROR_EXISTING|ERROR_METADATA"}
```
 
 
###POST /key 
TBD later, initiates a complicated key-change procedure, needs the original key/root key to auth