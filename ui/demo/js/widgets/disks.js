(function() {
  widgetsUpdating.add(updateDisksWidget);

  function updateDisksWidget() {

    var $widget = $('#disks-widget');
    var $HDD1Value = $widget.find('.resource-monitor.hdd-1 .usage .value');
    var $HDD1Bar = $widget.find('.resource-monitor.hdd-1 .fill');
    var $HDD2Value = $widget.find('.resource-monitor.hdd-2 .usage .value');
    var $HDD2Bar = $widget.find('.resource-monitor.hdd-2 .fill');

    function getRandomValue( current ) {
      //init
      var val;
      current = parseInt(current);
      //random value
      val = getRandomInt(0,5);
      //adding or substracting
      if ( getRandomInt(0,10) ) {
        val = current - val;
      } else {
        val = val + current;
      }

      if ( val <= 0 ) {
        return 0;
      } else if ( val >= 100 ) {
        return 100;
      } else {
        return val;
      }
    }

    function updateHDD() {
      var value = getRandomValue( parseInt( $HDD1Value.html() ) );
      var value2 = getRandomValue( parseInt( $HDD2Value.html() ) );
      $HDD1Value.html(value);
      $HDD1Bar.css('width', value + '%');
      $HDD2Value.html(value2);
      $HDD2Bar.css('width', value2 + '%');
    }

    updateHDD();
  }
})();
