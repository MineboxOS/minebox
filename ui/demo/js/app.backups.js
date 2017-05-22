viewLoader.add('backups', backup);

function backup() {

  var CONFIG = config.backups;
  var templates = {loaded: false};
  var snapshots = {};
  var ready = {};
  var $rootElement = $('#backups');
  var $snapshotsBox = $('#snapshots');
  var snapshotsManager;

  //init
  function init(callback_func) {
    //defining funcs
    function getTemplates() {
      //setting up urls
      var globalStatusURL = CONFIG.templates.path + CONFIG.templates.files.globalStatus;
      var snapshotsIterationURL = CONFIG.templates.path + CONFIG.templates.files.snapshotsIteration;

      //placing ajax calls
      get.template( globalStatusURL, function( data ) {
        //success
        templateLoaded( {globalStatus: data} );
      }, function() {
        //fail
        templateLoadFailed( {globalStatus: 'There was a problem loading the status. Please refresh your browser.'} );
      });
      get.template( snapshotsIterationURL, function( data ) {
        //success
        templateLoaded( {snapshotsIteration: data} );
      }, function() {
        //fail
        templateLoadFailed( {snapshotsIteration: 'There was a problem loading your snapshots. Please refresh your browser'} );
      });

      //callbacks on ajax calls
      var data = {};
      function templateLoaded( obj ) {
        $.extend( true, data, obj );
        if ( objectLength(data) == 2 ) {
          //boths calls has been finished
          templates = data;
          templates.loaded = true;
          $rootElement.trigger('templatesLoaded');
        }
      }
      function templateLoadFailed( obj ) {
        if ( obj.globalStatus ) {
          $('#backup-global-status').html( obj.globalStatus ).fadeIn(100);
        } else if ( obj.snapshotsIteration ) {
          $('#backup-manager .backup-manager-inner').html( obj.snapshotsIteration );
        }
        templates.loaded = false;
      }
    }

    function getSnapshotList() {
      get.snapshot.list(function( data ) {
        //success
        snapshots = data;
        $rootElement.trigger('snapshotListLoaded')
      }, function() {
        //fail
        $('#backup-manager .backup-manager-inner').html( 'There was a problem loading your snapshots. Please refresh your browser.' );
      });
    }

    function nextSnapshotScheduled() {
      var cd = new Date();
      var timestamp = cd.getTime() + 3600000;
      var fd = new Date(timestamp);
      var time = make2Digit(fd.getDate()) + '/' + make2Digit((fd.getMonth() + 1)) + '/' + fd.getFullYear() + ' ' + fd.getHours() + ':' + fd.getMinutes();
      $('#next-snapshot-scheduled').html(time);
    }

    //get templates
    getTemplates();
    //get snapshot list
    getSnapshotList();
    //schedule for the next snapshot to be taken
    nextSnapshotScheduled();

    //listening to raised events
    $rootElement.bind('templatesLoaded', function() {
      ready.templates = true;
      $rootElement.trigger('ready');
    });
    $rootElement.bind('snapshotListLoaded', function() {
      ready.snapshots = true;
      $rootElement.trigger('ready');
    });
    $rootElement.bind('ready', function() {
      if ( objectLength(ready) == 2 ) {
        //we can start building
        callback_func();
      }
    });
    $('#take-snapshot-button').on('click', function() {
      snapshotsManager.take();
    });
  }


  //init everything
  init(function() { //when everything has been successfully loaded exec this callback
    snapshotsManager = new SnapshotsManager($snapshotsBox, templates.snapshotsIteration, snapshots, CONFIG.snapshots);
    setTimeout(function() {
      snapshotsManager.init();
    }, 500);
  });

}
