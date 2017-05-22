(function() {
  widgetsUpdating.add(updateResourcesWidget);

  var $widget = $('#resources-widget');
  var $CPUValue = $widget.find('.resource-monitor.cpu .usage .value');
  var $CPUBar = $widget.find('.resource-monitor.cpu .fill');
  var $RAMValue = $widget.find('.resource-monitor.ram .usage .value');
  var $RAMBar = $widget.find('.resource-monitor.ram .fill');
  var $LANup = $widget.find('.resource-monitor.lan .up .value');
  var $LANdown = $widget.find('.resource-monitor.lan .down .value');

  function updateResourcesWidget() {

    function getRandomValue( current ) {
      //init
      var val;
      current = parseInt(current);
      //random value
      val = getRandomInt(0,5);
      //adding or substracting
      if ( getRandomInt(0,1) ) {
        val = current + val;
      } else {
        val = current - val;
      }

      if ( val <= 0 ) {
        return 0;
      } else if ( val >= 100 ) {
        return 100;
      } else {
        return val;
      }
    }

    function updateCPU() {
      var value = getRandomValue( parseInt( $CPUValue.html() ) );
      $CPUValue.html(value);
      $CPUBar.css('width', value + '%');
    }
    function updateRAM() {
      var value = getRandomValue( parseInt( $RAMValue.html() ) );
      $RAMValue.html(value);
      $RAMBar.css('width', value + '%');
    }
    function updateLAN() {
      var values = {
        up: getRandomInt(0,1500),
        down: getRandomInt(0,10000)
      }
      $LANup.html(values.up);
      $LANdown.html(values.down);
    }

    updateCPU();
    updateRAM();
    updateLAN();
  }
})();
