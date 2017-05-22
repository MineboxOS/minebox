var viewLoader = new ViewLoader( config.views );
var urlInfo = new urlInfo();
var nav = new Nav();
var get = new AjaxCalls();
var tabManager = new TabManager( config.tabs );
var format = new Format(); //func.lib.js func to format time and date

$(document).ready(function() {
  //starts the loop for navigator
  viewLoader.loop();

  //bind #menu li buttons to nav func
  $(config.views.buttons.selector).on('click', function() {
    nav.go( $(this).attr('data-go') );
  });

  //displaying do smth (dummy message) [index.html]
  //setTimeout(function() {$('#dummy-message').fadeIn(100);},500);
  //Loading dashboard instead:
  if ( !window.location.hash ) {
    nav.go('dashboard');
  }
});
