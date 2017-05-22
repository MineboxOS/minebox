
function WidgetLayoutManager( config ) {

  var pckry;

  var CONFIG = {};
  $.extend(true, CONFIG, config);

  function init() {
    pckry = $(CONFIG.containerId).packery(CONFIG.packery);
  }
  function append( stream ) {
    var $item = $(stream);
    resetLayout();
    pckry
      .append( $item )
      .packery( 'appended', $item );
    return $item;
  }
  function prepend( stream ) {
    var $item = $(stream);
    resetLayout();
    pckry
      .prepend( $item )
      .packery( 'prepended', $item );
    return $item;
  }
  function remove( $target ) {
    console.log( $target );
    pckry
      .packery( 'remove', $target );
    resetLayout();
  }
  function resetLayout() {
    pckry.packery();
  }

  return {
    init: init,
    append: append,
    prepend: prepend,
    remove: remove,
    layout: resetLayout
  }
}
