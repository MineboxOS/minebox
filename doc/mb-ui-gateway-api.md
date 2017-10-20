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
  "relative_progress": 87.5,
  "min_redundancy": 2.9,
  "earliest_expiration": 149884,
  "earliest_expiration_esttime": 1522056589
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

### GET /consensus

Return Sia consensus status. Without `synched` being `true`, a lot of Sia
activities do not work.  
Also see [Sia API /consensus documentation](https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#consensus-get).

```json
{
  "synced": true,
  "height": 62248,
  "currentblock": "00000000000008a84884ba827bdc868a17ba9c14011de33ff763bd95779a9cf1",
  "target": [0,0,0,0,0,0,11,48,125,79,116,89,136,74,42,27,5,14,10,31,23,53,226,238,202,219,5,204,38,32,59,165],
  "difficulty": "1234"
}
```

### GET /contracts

Return a list of active Sia renter contracts.

```json
[
  {
    "id": "1234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "host": "12.34.56.78:9",
    "funds_remaining_sc": 10.4562,
    "funds_spent_sc": 0.01234,
    "fees_spent_sc": 5.2345,
    "data_size": 1000000000,
    "height_end": 149884,
    "esttime_end": 1522056589
  },
  ...
]
```

### PUT /key

Sets a new key. Only works on a system that has no key set yet.  
This can be called *without a Rockstor login*.  
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
```

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

### GET /key/generate

Just randomly generates a key, transcribed into a set of 12 words.

```json
[
  "randomly",
  "generated",
  "list", "of", "twelve", "words"
]
```

### GET /key/status

Returns the current key status. `Not implemented yet!`

```json
{
  "pubkey": "0123456789ABC", //ec pubkey/address or checksum
  "metadata": "BACKED_UP|NOT_FOUND",
  ""
}
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

### GET /sia/status

Return the current status of the Sia subsystem.  
The *wallet* has a confirmed balance (in siacoins) and an uncomfirmed delta of
transactions that have been messaged to the network but not confirmed in a Sia
block yet (like a contract in the real world that has been agreed between the
parties but not signed yet).  
Some fields (like wallet contents) will not be reported (i.e. contain a `null`
value) when there is no `user` `logged_in` via the Rockstor UI, but the base
system status `true`/`false` fields will also be available in this case.


```json
{
  "sia_daemon_running": true,
  "consensus": {
    "height": 62248,
    "synced": true,
    "sync_progress": 100
  },
  "wallet": {
    "unlocked": true,
    "encrypted": true,
    "confirmed_balance_sc": 2154.5678,
    "unconfirmed_delta_sc": -78.987
  },
  "renting": {
    "contracts": 74,
    "allowance_funds_sc": 1000,
    "allowance_months": 6,
    "siacoins_spent": 1029.6530237321947,
    "siacoins_unspent": 591.5453739038509,
    "uploaded_files": 55,
    "uploaded_size": 581088460.8
  },
  "hosting": {
    "enabled": false,
    "contracts": 0,
    "connectabilitystatus": "not connectable",
    "workingstatus": "not working",
    "netaddress": "",
    "collateral_sc": 100,
    "collateralbudget_sc": 1000000,
    "maxcollateral_sc": 5000,
    "maxduration_months": 6,
    "mincontractprice_sc": 5,
    "mindownloadbandwidthprice_sc": 250,
    "minstorageprice_sc": 750,
    "minuploadbandwidthprice_sc": 10,
    "collateral_locked_sc": 0,
    "collateral_lost_sc": 0,
    "collateral_risked_sc": 0,
    "revenue_sc": 0
  }
}
```

### GET /status

Return the current status of the Minebox system, showing the most important
indicators.  
If the Minebox setup is fully done and the box is active,
`minebd_storage_mounted` and `users_created` are both `true`. To be fully
usable, `wallet_encrypted` (setting up the wallet) and `wallet_unlocked` need to
be `true` as well, which the Minebox should all do automatically.  
If `minebd_storage_mounted` is `true` and `users_created` is `false`, the
Minebox storage was set up, but we did not create any users in Rockstor yet.  
If `minebd_running` or `sia_daemon_running` is `false`, we have an internal
issue with the box, we never should run into this. A reboot may help, but
otherwise, Minebox support is needed.  
If `minebd_encrypted` is `true` but `minebd_storage_mounted` is not, MineBD (or
btrfs formatting etc.) is probably still in the process of setting up the
storage, but the key is set.  
If `minebd_encrypted` is `false`, the Minebox has not be set up at all yet.  
See /sia/status for wallet metric descriptions.  
Some fields (like wallet contents) will not be reported (i.e. contain a `null`
value) when there is no `user` `logged_in` via the Rockstor UI, but the base
system status `true`/`false` fields will also be available in this case.


```json
{
  "logged_in": true,
  "user": "myuser",
  "minebd_running": true,
  "minebd_encrypted": true,
  "minebd_storage_mounted": true,
  "restore_running": true,
  "restore_progress": 100,
  "users_created": true,
  "backup_type": "sia",
  "sia_daemon_running": true,
  "consensus_height": 62248,
  "consensus_synced": true,
  "wallet_unlocked": true,
  "wallet_encrypted": true,
  "wallet_confirmed_balance_sc": 2154.5678,
  "wallet_unconfirmed_delta_sc": -78.987
}
```

### GET /wallet/address

Generate a new address for the wallet, usually to receive currency into. Also
see [ Sia API /wallet/address documentation](https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#walletaddress-get),
which this exactly forwards to.

```json
{
  "address": "1234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789ab"
}
```

### POST /wallet/send

Sends siacoins to an address. Takes an `amount` in hastings (as a string to not
run afoul of JavaScript's limits of numerical values) and a `destination`
wallet address to receive the siacoins, both as urlencoded form values. If no
`amount` is given, an `amount_sc` in siacoins (numeric, en-US-style decimal
value) is supported.

Also see [ Sia API /wallet/siacoins documentation](https://github.com/NebulousLabs/Sia/blob/master/doc/API.md#walletsiacoins-post),
which is used behind the scenes.


```json
{
  "transactionids": [
    "1234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  ]
}
```

### GET /wallet/status

Return the status of the Sia wallet. For the currency values, both an
unpostfixed entry with a string of a hastings value is reported and a numeric
value with a `_sc` postfix containing a siacoin amount.


```json
{
  "encrypted": true,
  "unlocked": true,
  "confirmedsiacoinbalance": 2154567800000000000000000000,
  "confirmedsiacoinbalance_sc": 2154.5678,
  "unconfirmedincomingsiacoins": 1345000000000000000000000,
  "unconfirmedincomingsiacoins_sc": 1.345,
  "unconfirmedoutgoingsiacoins": 77642000000000000000000000,
  "unconfirmedoutgoingsiacoins_sc": 77.642,
  "siacoinclaimbalance": 0,
  "siacoinclaimbalance_sc": 0,
  "siafundbalance": 0,
  "siafundbalance_sc": 0,
}
```

### GET /wallet/transactions

Return a list of all transactions this wallet has seen in its existence.
By default, only transactions that actually change the balance are shown. If
even zero-sum "split" transactions should be shown, add a `showsplits` url
parameter with a true-ish (e.g. 1, "true", or "t") value.
Also, by default, unconfirmed transactions will be listed, with `height` and
`timestamp` fields of really high values so they sort as "in the future" - but
they should not be shown to the user in this case.
If unconfirmed transactions should not be shown, ass a url parameter
`onlyconfirmed` with a true-ish value.


```json
[
  "change": "47000000000000000000000000",
  "change_sc": 47,
  "confirmed": true,
  "fundschange": "0",
  "height": 125006,
  "incoming": {
    "siacoin output": "47000000000000000000000000"
  },
  "incoming_sc": {
    "siacoin output": 47
  },
  "outgoing": {},
  "outgoing_sc": {},
  "timestamp": 1506627478,
  "transactionid": "3beb72939143b333dd7f3263afe2470bb49d3b50c1fed5be90009dbffd788dda"
],
[
  "change": "-16464093916300282534935134",
  "change_sc": -16.464093916300282,
  "confirmed": true,
  "fundschange": "0",
  "height": 125021,
  "incoming": {},
  "incoming_sc": {},
  "outgoing: {
    "siacoin input	"16464093916300282534935134"
  },
  "outgoing_sc: {
    "siacoin input	16.464093916300282
  },
  "timestamp": 1506635708,
  "transactionid": "242934fc0caa72079ec252b5d3618d6a0851f0336619b320016e79dd205294ef"
]
```
