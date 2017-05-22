function SnapshotsManager($container, template, snapshotsID, config) {


  var CONFIG = config;
  var snapshotIndex = snapshotsID.length;
  var snapshotsIDToLoad = snapshotsID;
  var owlData;
  var rendering = null;
  var loading = new SnapshotsLoadingWitnessManager();
  var loadingError = false;



  function startCarousel( config ) {
    config.afterInit = renderActive;
    config.afterMove = function() {
      goBackInTime();
      renderActive();
    }
    owlData = $container.owlCarousel( config );
  }



  function SnapshotsLoadingWitnessManager() {
    function on() {
      if (!$container.hasClass('loading')) {
        $container.addClass('loading');
      }
    }
    function off() {
      setTimeout(function() {
        $container.removeClass('loading');
      }, 1000);
    }
    return {
      on: on,
      off: off
    }
  }



  function newSnapshot(callback_func) {
    //place an ajax call, gets new snapshots' ID
    var newSnapshotID;
    get.snapshot.new(function(data) {
      newSnapshotID = data.id;
      if ( callback_func ) {
        callback_func(newSnapshotID);
      }
    }, function() {
      //error goes here
    });
  }



  function getFollowingSnapshotID() {
    //decides if we need to load or not an older snapshot
    if (snapshotsIDToLoad.length) {
      //return first snapshots from the list (following 'newest')
      return snapshotsIDToLoad[0];
    } else {
      //return false (no more snapshots to load)
      return false;
    }
  }



  function getDetails(id, success, fail) {
    //temporarily removing loaded ID from arrays list
      //NOTE: since asynchronous calls are being called, we need to take this ID out of "loading-pending" array in order to say: hey is being loaded or loaded
      //in case that the loading fails, we will inject it back into the array
    var i = snapshotsIDToLoad.indexOf(id);
    var loading = snapshotsIDToLoad.splice(i, 1); //here we collect the removed ID

    //init vars for success. They must be declared before requests otherwise there will be "undefined" within the first execution of callSuccess
    var data = {}, counter = 0;

    //requests details with given ID
    get.snapshot.details(id, callSuccess, callFail);
    getSnapshotUploadingProgress(id, callSuccess, callFail);

    //collecting results and exec success when both calls finished
    function callSuccess(obj) {
      $.extend( true, data, obj);
      counter++;
      if ( counter == 2 ) {
        //if success we do nothing regarding snapshotsIDToLoad since we already removed the ID
        success(data);
      }
    }
    function callFail() {
      //if the load fails we will need to restore the ID into the array
      snapshotsIDToLoad.splice(i,0,loading[0]); //[0] since there was only one value on the slice
      fail();
    }
  }



  function getSnapshotUploadingProgress(id, success, fail) {

    get.snapshot.uploadProgress(id, function(progress) {
      success({progress:progress});
      if (progress<100) {
        keepUpdating(id);
      }
    }, fail);

    function keepUpdating(id) {
      var requesting = false;
      loop(id);

      function loop(id) {
        if (!requesting) {
          setTimeout(function() {
            requesting = true;
            get.snapshot.uploadProgress(id, function(progress) {
              succeeded(id,progress);
            }, function() {
              failed();
            });
          }, 1000);
        }
      }

      function succeeded(id,progress) {
        requesting = false;
        //setting div's height according to uploaded percent
        $('#'+id).find('.progress').css('height', progress + '%');
        //setting value on witness input
        $('#'+id).find('.upload-progress').val(progress);
        if (progress<100) {loop(id);}
      }
      function failed() {
        return false;
      }
    }
    /*
    var uploadProgressInterval = setInterval(function() {
      var progress = get.snapshot.uploadProgress( id );
      if (progress>=100) {
        clearInterval(uploadProgressInterval);
        success({progress:100});
      }
    }, 1000);*/
  }



  function processTemplate(details) {
    //processes the template with given details and returns a stream
    var output = template;
    output = replaceAll( output, '{{snapshot_id}}', details.id );
    output = replaceAll( output, '{{snapshot_size}}', details.size );
    output = replaceAll( output, '{{snapshot_date}}', format.date( details.date ) );
    output = replaceAll( output, '{{snapshot_time}}', format.time( details.date ) );
    output = replaceAll( output, '{{snapshot_time_zone}}', 'CET' );
    output = replaceAll( output, '{{snapshot_created_files}}', details.files );
    output = replaceAll( output, '{{snapshot_deleted_files}}', details.files );
    output = replaceAll( output, '{{snapshot_created_bytes}}', details.files );
    output = replaceAll( output, '{{snapshot_deleted_bytes}}', details.files );
    output = replaceAll( output, '{{snapshot_upload_progress}}', details.progress );
    output = replaceAll( output, '{{snapshot_index}}', snapshotIndex );
    snapshotIndex--;
    return output;
  }



  function appendSnapshot(stream) {
    //adds the new stream to the end of the DOM elements
    $container.data('owlCarousel').addItem(stream, $container.find('.snapshot').length);
  }



  function prependSnapshot(stream) {
    //adds the new stream to the beginning of the DOM elements
    $container.data('owlCarousel').addItem(stream, 0);
  }



  function renderActive() {
    //animates all active snapshots
    loading.on();
    var $active = $('.owl-item.active');
    var highest = 0;
    var ratio, percent;
    function findHighest() {
      for ( var n = 0; n < $active.length; n++ ) {
        $($active).find('.snapshot');
        if ( parseInt( $($active[n]).find('.created-bytes').val() ) > highest ) {
          highest = $($active[n]).find('.created-bytes').val();
        }
        if ( parseInt( $($active[n]).find('.deleted-bytes').val() ) > highest ) {
          highest = $($active[n]).find('.deleted-bytes').val();
        }
      }
    }
    function assignSizeValues() {
      setTimeout(function() {
        for ( var n = 0; n < $active.length; n++ ) {
          //created bytes
          ratio = highest / $($active[n]).find('.created-bytes').val();
          percent = 100 / ratio;
          $($active[n]).find('.split-created .size').css('height', percent + '%');
          //deleted bytes
          ratio = highest / $($active[n]).find('.deleted-bytes').val();
          percent = 100 / ratio;
          $($active[n]).find('.split-deleted .size').css('height', percent + '%');
        }
      }, 300);
    }
    function assignProgressValues() {
      var value;
      setTimeout(function() {
        for ( var n = 0; n < $active.length; n++ ) {
          value = $($active[n]).find('.upload-progress').val();
          $($active[n]).find('.progress').css('height', value + '%' );
        }
      }, 500);
    }

    if ( rendering ) {
      clearTimeout(rendering);
    }
    //adding a timeout to prevent multiple calls consecutive
    //as in example when owl.reinit calls to renderActive();
    rendering = setTimeout(function() {
      findHighest();
      assignSizeValues();
      assignProgressValues();
      loading.off();
    }, 200);
  }



  function goBackInTime() {
    if ($('.owl-item:last-child').hasClass('active')) {
      var number;
      if (snapshotsIDToLoad.length<CONFIG.olderLoad) {
        number = snapshotsIDToLoad.length;
      } else {
        number = CONFIG.olderLoad;
      }
      for ( var n = 0; n < number; n++ ) {
        loadOlderSnapshot();
      }
      if (!number) {
        alert('No older snapshots!');
      }
    }
  }






  function init() {
    startCarousel(CONFIG.carousel);
    //setting items to load on init
    var number = null;
    if (snapshotsID.length>CONFIG.firstLoad) {
      number = CONFIG.firstLoad;
    } else {
      number = snapshotsID.length;
    }
    for ( var n = 0; n < number; n++ ) {
      loadOlderSnapshot();
    }
  }
  function loadOlderSnapshot() {
    var id = getFollowingSnapshotID();
    if ( id ) { //id will be false if there are no older snapshots
      getDetails( id, function(data) {
        appendSnapshot( processTemplate(data) );
        renderActive();
      }, function() {
        //failed getting details
        console.log('failed getting details');
      });
    } else {
      alert('No older snapshots!');
    }
  }
  function takeNewSnapshot() {
    newSnapshot(function(id) {
      getDetails(id, function(details) {
        var stream = processTemplate(details);
        prependSnapshot(stream);
      }, function() {
        alert('Impossible to take the snapshot! Refresh the browser and try again.');
      });
    });
  }


  return {
    init: init,
    take: takeNewSnapshot
  }
}
