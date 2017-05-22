//
// TODO:
// X Prevent same widget to be created twice.
// X Widgets must be able to expand/collapse
// X When loading a widget, it must activate its corresponding side-menu button
// X When closing a widget, it must deactivate its corresponding side-menu button
// - Drag & drop sorting
// X Prepend new widget instead of append (loading it the first rather than the last)
// - Being able to close a widget by clicking its corresponding #sidebar menu button
//
//
//

function WidgetManager( config ) {


  var CONFIG = {
    isolatedClass: 'isolated',
    isolatedSelector: '.isolated',
    loaded: []
  };
  $.extend(true, CONFIG, config);



  function isWidgetLoaded( target ) {
    for ( var n = 0; n < CONFIG.loaded.length; n++ ) {
      if ( CONFIG.loaded[n] == target ) {
        return true;
      }
    }
    return false;
  }



  function init() {
    $(CONFIG.containerId).append('<div class="' + CONFIG.isolatedClass + '" style="display:none;"></div>');
  }



  function load( target, callback_func ) {
    $.get( CONFIG.rootFolder + CONFIG.map[target].file, function(data) {

      if ( !isWidgetLoaded( target ) ) {
        //adding target to loaded array
        CONFIG.loaded.push( target );

        //next line won't be executed since is widgetLayoutManager and Packery whose are actually writting the content
        //this function is just controlling if current target is not created already
        //$(CONFIG.containerId).prepend( data );

        //executing callback_func
        if ( callback_func ) {
          callback_func( data );
        }
      } else {
        alert(target + 'widget already loaded!')
      }
    });
  }



  function close( $clicked, callback_func ) {
    //removing id from CONFIG.loaded[]
    var dataButton = $clicked.attr( CONFIG.attr.widget );
    var index = CONFIG.loaded.indexOf( dataButton );
    CONFIG.loaded.splice( index, 1 );
    //exec callback_func
    if ( callback_func ) {
      callback_func( dataButton );
    }
  }



  function resize( $target, callback_func ) {
    function expand( $target ) {
      $target.addClass('expanded');
    }
    function collapse( $target ) {
      $target.removeClass('expanded');
    }
    if ( $target.hasClass('expanded') ) {
      collapse( $target );
    } else {
      expand( $target );
    }
    if ( callback_func ) {
      callback_func();
    }
  }



  function refresh() {

  }



  return {
    init: init,
    load: load,
    close: close,
    resize: resize
  }
}
