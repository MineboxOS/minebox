/* requires ajax.requester.js */

function AjaxCalls() {
  var requester = new Requester();
  var ret = {};

  //load templates
    function loadTemplate( url, success, fail ) {
      requester.setMethod('GET');
      requester.setType('text');
      requester.setURL(url);
      requester.run( success, fail );
    }
    ret.template = loadTemplate;

  //backups
    ret.snapshot = {};
    function getSnapshotList( success, fail ) {
      getSnapshots(function(response) {
        //taking id's out of objects from array
        var r = [];
        for (var n = 0; n < response.length; n++) {
          r.push(response[n].id);
        }
        success(r);
      });
    }
    ret.snapshot.list = getSnapshotList;
    function getSnapshotDetails( requestedID, success, fail ) {
      getSnapshots(function(response) {
        //getting info from requested snapshot
        var details;
        for (var n = 0; n < response.length; n++) {
          if (requestedID == response[n].id) {
            details = response[n];
          }
        }
        success(details);
      });
      //requester.setMethod('GET');
      //requester.setType('json');
      //requester.setData(data);
      //requester.setURL('json/snapshotDetails.json');
      //requester.run( success, fail );
    }
    ret.snapshot.details = getSnapshotDetails;
    function getSnapshotUploadProgress( id, success, fail) {
      snapshotUploadProgress(id, success, fail);
      //requester.setMethod('GET');
      //requester.setType('json');
      //requester.setData(data);
      //requester.setURL('json/snapshotUploadProgress.json');
      //requester.run( success, fail );
    }
    ret.snapshot.uploadProgress = getSnapshotUploadProgress;
    function getNewSnapshotID(success, fail) {
      createSnapshot(success);
      //requester.setMethod('GET');
      //requester.setType('json');
      //requester.setURL('snapshot/create');
      //requester.run( success, fail );
    }
    ret.snapshot.new = getNewSnapshotID;

  //contracts
    ret.contracts = {};
    function contractsList(success, fail) {
      getContracts(success);
    }
    ret.contracts.list = contractsList;
    function contractsDetails(id, success, fail) {
      getContract(id, success);
    }
    ret.contracts.details = contractsDetails;

  //plugins
    ret.plugins = {};
      function getPluginsInfo(success, fail) {
        requester.setMethod('GET');
        requester.setType('json');
        requester.setURL('json/plugins.json');
        requester.run( success, fail );
      }
      ret.plugins.info = getPluginsInfo;

  //filesystem
    function dir(success, fail, id) {
      requester.setMethod('GET');
      requester.setType('json');
      requester.setURL('json/directory.json');
      requester.run(function(response) {
        if ( !id ) {
          success(response);
        } else {
          function run( array ) {
            for ( var n = 0; n < array.length; n++ ) {
              if ( array[n].id == id ) { //eval current
                success( array[n] );
              }
              if ( array[n].contents.length ) { //re-run if volume or folder with contents on it
                run( array[n].contents );
              }
            }
          }
          run(response);
        }
      }, fail );
    }
    ret.directory = dir;



  return ret;
}
