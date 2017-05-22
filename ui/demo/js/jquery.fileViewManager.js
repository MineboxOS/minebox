function FileViewManager() {
  var $button = $('#view-manager-button .view-manager-button'),
      $fileSystemTree = $('#filesystem-tree'),
      selectedClass = 'active',
      listViewClass = 'list-view',
      gridViewClass = 'grid-view';

  $button.on('click', function() {
    if ( !$(this).hasClass(selectedClass) ) {
      //remove selected class in all buttons
      $button.removeClass(selectedClass);
      //add selected class in current
      $(this).addClass(selectedClass);
      //activate view
      if ( $(this).data('button') == 'list' ) {
        $fileSystemTree.removeClass(gridViewClass).addClass(listViewClass).attr('data-view', listViewClass);
      } else if ( $(this).data('button') == 'grid' ) {
        $fileSystemTree.addClass(gridViewClass).removeClass(listViewClass).attr('data-view', gridViewClass);
      }
    }
  });
}
