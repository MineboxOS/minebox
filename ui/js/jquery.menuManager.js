(function() {

  var $menu = $('#menu-box');
  var $menuButton = $('#mobile-menu .mobile-menu');
  var $menuElements = $('#menu li');

  var menuToggler = MenuToggler();
  function MenuToggler() {

    function open() {
      $menu.addClass('active');
      $menuButton.addClass('active');
    }
    function close() {
      $menu.removeClass('active');
      $menuButton.removeClass('active');
    }
    function handle() {
      if ( $menuButton.hasClass('active') ) {
        close();
      } else {
        open();
      }
    }
    return {
      open: open,
      close: close,
      handle: handle
    }
  }

  $menuButton.on('click', function() {
    menuToggler.handle();
  });

  $menuElements.on('click', function() {
    menuToggler.close();
  });


})();
