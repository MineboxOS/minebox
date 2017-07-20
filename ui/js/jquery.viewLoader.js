/* Requires urlInfo.js to work */

function ViewLoader( config ) {



  var CONFIG = {
    interval: 200
  };
  $.extend( true, CONFIG, config );
  var lastView, //holds last loaded view (even if it fails, it will hold it)
      interval, //holds the setInterval function (maybe one day we want to make it stop at some point)
      current, //holds the obj for current url
      func_map = {}, //will be collecting all the callback functions for each content
      ajax_call_state = 'idle'; //status of ajax call in order to prevent several content-loadings simultaneously


  function buttonStateHandler( id ) {
    $(CONFIG.buttons.selector).removeClass(CONFIG.buttons.class);
    $(CONFIG.buttons.selector + '[' + CONFIG.buttons.attribute + '=' + id + ']').addClass(CONFIG.buttons.class);
  }



  function loop() {
    interval = setInterval(function() {
      current = urlInfo.get();
      if ( current.h != lastView && ajax_call_state == 'idle' ) {
        ajax_call_state = 'busy';
        lastView = current.h;
        loadContent( current.h, func_map[current.h] );
      }
    }, CONFIG.interval);
  }



  function loadContent( viewID, callback_func ) {
    function success(data) {
      //hide container while displaying the content
      $('#'+CONFIG.containerID).fadeOut(100,function(){
        //fill container with data and show it after
        $('#'+CONFIG.containerID).html( data ).fadeIn(150, function() {
          //handle activation witnesses in buttons
          buttonStateHandler( viewID );
          //exec callback_func if provided
          if ( callback_func ) {
            callback_func();
          }
        });
      });
    }
    function fail() {
      $('#'+CONFIG.containerID).fadeOut(100, function() {
        //removing /active-class/ from buttons after failing load
        buttonStateHandler( undefined );
        //display message error
        $('#'+CONFIG.containerID)
          .html('<div class="container-fluid" style="padding-top:18px"><p>'+CONFIG.messages.fail+'</p></div>')
          .fadeIn(150);
      });
    }
    if ( CONFIG.map[viewID] ) {
      //placing the ajax call
      $.get( CONFIG.path + CONFIG.map[ viewID ] )
      .done(function( data ) {
        success(data);
      })
      .fail(function() {
        fail();
      })
      .always(function() {
        //setting this var to original status
        ajax_call_state = 'idle';
      });
    } else {
      //setting this var to original status
      ajax_call_state = 'idle';
    }
  }



  function add( key, func ) {
    func_map[key] = func;
  }



  return {
    loop: loop,
    add: add
  }
}
