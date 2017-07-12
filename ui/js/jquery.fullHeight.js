(function ($) {
  $.fn.fullHeight = function( config ) {



    //elements which heights match
    var $elements = this;



    //default values
    var CONFIG = {
      resizeDelay: 200
    }
    //merging incoming config obj
    $.extend( true, CONFIG, config );



    function getExcludedElementsHeight() {
      var exclusionHeight = 0;
      for ( var n = 0; n < CONFIG.exclude.length; n++  ) {
        exclusionHeight += $(CONFIG.exclude[n]).height();
      }
      return exclusionHeight;
    }



    function getTallestElementHeight() {
      var maxHeight = 0; //setting maxHeight to minimum
      var calcHeight;
      $elements.css('height', 'auto'); //restoring height to auto
      for ( var n = 0; n < $elements.length; n++ ) { //getting highest height
        calcHeight = $($elements[n]).height() + parseInt($($elements[n]).css('padding-top')) + parseInt($($elements[n]).css('padding-bottom'));
        if ( calcHeight > maxHeight ) {
          maxHeight = calcHeight;
        }
      }
      return maxHeight;
    }



    function getHeight() {
      if ( window.innerHeight > (getTallestElementHeight() + getExcludedElementsHeight()) ) {
        return window.innerHeight - getExcludedElementsHeight();
      } else {
        return getTallestElementHeight();
      }
    }



    function applyHeight( h ) {
      $elements.css('min-height', h);
    }



    function init() {
      resize();
    }



    function resize() {
      applyHeight( getHeight( $elements ) );
    }



    var resizing;
    $(window).resize(function() {
      if ( resizing ) {
        clearTimeout( resizing );
      }
      resizing = setTimeout(function() {
        resize();
      }, CONFIG.resizeDelay);
    });



    //autoexec function on plugin's load
    init();



  };
})(jQuery);
