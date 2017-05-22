function ContextualMenu() {

  //handling context menu
  $('#filesystem-tree .inner').bind('contextmenu', function(e) {
    e.preventDefault();
    var coords = {
      top: e.pageY,
      left: e.pageX
    }
    openContextMenu( $(this).parent().attr('data-kind'), coords );
    $(this).parent().trigger('click');
  });


  //closing context menu when clicking out of #filesystem-tree
  $('html, body').on('click', function(e) {
    e.stopPropagation();
    closeContextMenu();
  });


  //closing context menu when clicking (but not right-click) on $('#filesystem-tree li') (li .inner) elements
  $('#filesystem-tree li').bind('mousedown', function(e) {
    if ( e.button != 2 ) {
      closeContextMenu();
    }
  });


  //prevent right-click over #filesystem
  $(document).bind('contextmenu', function(e) {
    $filesystem = $('#filesystem');
    if ( $filesystem.length ) {
      e.preventDefault();
      return false;
    }
  });



  function openContextMenu( which, coords ) {
    closeContextMenu();
    $('.context-menu.'+which).css({
      'top': coords.top,
      'left': coords.left
    }).show(150);
  }


  function closeContextMenu() {
    $('.context-menu').hide(0);
  }

}
