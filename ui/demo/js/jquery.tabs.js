function TabManager( config ) {

  var CONFIG = {};
  $.extend( true, CONFIG, config );

  function init() {
    //adding click-event listener
    $(CONFIG.button.selector).on('click', function() {
      if ( !$(this).hasClass(CONFIG.activeClass) ) {
        //preventing a button with activeClass to trigger open()
        open( $(this).attr(CONFIG.button.attr) )
      }
    });
    //adding resize-event listener
    $(window).resize(resizeContainer);
  }

  function open( target ) {
    //removing activeClass to buttons and tabs
    $(CONFIG.tab.selector+','+CONFIG.button.selector).removeClass(CONFIG.activeClass);

    //adding activeClass to matching button
    $(CONFIG.button.selector+'['+CONFIG.button.attr+'="'+target+'"]').addClass(CONFIG.activeClass);

    //hidding all tabs and displaying requested
    var $visibles = $(CONFIG.tab.selector+':visible');
    if ( $visibles.length ) {
      //when another tab is already open
      $(CONFIG.tab.selector+':visible').fadeOut(300, function() {
        showTarget( target );
      });
    } else {
      //opening first tab of the collection
      showTarget( target );
    }

    function showTarget( target ) {
      $('#'+CONFIG.tab.prefix+'-'+target)
        .addClass(CONFIG.activeClass)
        .fadeIn(0, function() {
        resizeContainer();
      });
    }
  }

  function resizeContainer() {
    var h = $(CONFIG.tab.selector+'.'+CONFIG.activeClass).height();
    $(CONFIG.tab.containerSelector).height( h );
    //$(window).trigger('resize');
  }

  return {
    init: init,
    open: open
  }
}
