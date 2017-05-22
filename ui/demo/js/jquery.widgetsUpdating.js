function WidgetsUpdating( config ) {

  var interval = null;
  var CONFIG = {
    refreshRate: 1000 //ms to interval
  };
  $.extend(true, CONFIG, config);
  var fncs = [];

  function addWidget( fn ) {
    fncs.push( fn );
  }

  function removeWidget( fn ) {
    var index = fncs.indexOf( fn );
    fncs.slice( fn, 1 );
  }

  function start() {
    interval = setInterval(function() {
      for ( var n = 0; n < fncs.length; n++ ) {
        fncs[n]();
      }
    }, CONFIG.refreshRate);
  }

  function stop() {
    stopInterval(interval);
  }

  return {
    add: addWidget,
    remove: removeWidget,
    start: start,
    stop: stop
  }
}
