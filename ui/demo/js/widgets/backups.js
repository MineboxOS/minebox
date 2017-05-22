$a = null;

(function() {
  widgetsUpdating.add(updateBackupsUpload);

  var $widget = $('#backups-widget');
  var $backupValue = $widget.find('.resource-monitor.backup .usage .value');
  var $backupBar = $widget.find('.resource-monitor.backup .fill');

  $a = $backupBar;

  console.log($backupBar);

  function updateBackupsUpload() {
    var current = parseFloat($backupValue.html());
    current = parseFloat( current * 100 );
    current += 3;

    $backupValue.html( current / 100 );
    $backupBar.css( 'width', (current / 100) + '%' );

  }

  updateBackupsUpload();

})();
