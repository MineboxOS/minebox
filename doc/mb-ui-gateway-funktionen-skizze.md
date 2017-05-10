# UI / UI-Rest-Api Draft

The UI layer will be implemented consists of three distinct frontends that will all work with roughly the same paradigm:
A set of static html/js/css files that access a single service (MB-UI-GW) via Rest Api. The MB-UI-GW service seldomly processes the request itself, but uses other services to do the job.

TBD: The MB-UI-GW needs to know if the user is authenticated via the Rockstor interface possibly via the cookie that is already set after login


 * The setup UI after the first boot
   * *must* Key management (create new / input existing / check data / metadata)
   * *must* Restore status
 * The customized Rockstor UI
   * *later* dashboard integration 
   * *later* adding/removing disks at runtime
 * The minebox-only interface
   * *optional* (geek factor only) show contracts (TBD if we really want to expose this)
   * *optional* wallet functions (send/receive/balance)
   * *must* list backups 
   * *must* upload status per backup (needs sila file upload status under the hood)

fyi: we differentiate the term **snapshot** and **backup**. 

 **snapshot** happens in multiple subvolumes on the **upper**
 
**backup** contains a group up snapshots of the **upper** and is built with the help of snapshots of **lower**  

 
therefore we can already define a list of calls:

##Api-Calls
Api calls should be kept simple.

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