function copyToClipboard( $element, cb ) {
  //selecting contents
  $element.focus();
  $element.select();
  document.execCommand('copy');
  $element.blur();
  if ( cb ) {
    //exec a callback after execCommand is executed
    cb();
  }
}
