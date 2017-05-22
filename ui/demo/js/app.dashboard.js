/* jsing dashboard */
viewLoader.add('dashboard', dashboard);

var widgetsUpdating;
function dashboard() {
  var widgetManager;
  var widgetLayoutManager;

  //declaring widgetManager
  widgetManager = WidgetManager( config.widgets );
  widgetManager.init();
  //declaring & init widgetLayoutManager
  widgetLayoutManager = WidgetLayoutManager( config.widgets.layout );
  widgetLayoutManager.init();
  //declaring & init widgetsUpdating
  widgetsUpdating = WidgetsUpdating( config.widgets.updating );
  widgetsUpdating.start();


  //loading new widgets
  $('#sidebar li').on('click', function() {
    //storing dataLoad for writting it in spawned widget
    var dataLoad = $(this).attr('data-load');
    if ( !$(this).hasClass('active') ) {
      //adding class active
      $(this).addClass('active');
      //@params STREAM is the HTML content returned from AJAX call. will be needed for re-layout widget's
      widgetManager.load( dataLoad, function( stream ) {
        //triggering a resize to make #sidebar grow as much as #content
        $(window).trigger('resize');
        //sending @param: appendedItemId to layout manager
        //returns selected DOM element of appended stream
        //var $item = widgetLayoutManager.append( stream );
        var $item = widgetLayoutManager.prepend( stream );
        $item.attr( config.widgets.attr.widget, dataLoad );
      });
    } else {
      //TODO: make the button closes corresponding widget at this point
    }
  });


  //enabling all the widgets
  var $sidebarButtons = $('#sidebar li');
  var n = 0;
  var enabling = setInterval(function() {
    $($sidebarButtons[n]).trigger('click');
    n++;
    if ( n >= $sidebarButtons.length ) {
      clearInterval(enabling);
    }
  }, 300);



  //removing existing widget
  $('body').on('click', '.close-widget-button', function() {
    var $clicked = $(this);
    widgetManager.close( $(this).parents('.widget'), function( dataButton ) {
      //removing widget from #widgets-box
      widgetLayoutManager.remove( $clicked.parents('.widget') );
      //deactivating button
      $('#sidebar li['+config.widgets.attr.button+'='+dataButton+']').removeClass('active');
    });
  });



  //expanding/collapsing widget
  $('body').on('click', '.expand-widget-button', function() {
    widgetManager.resize( $(this).parents('.widget'), function() {
      widgetLayoutManager.layout();
    });
  });




  //full height
  $('#sidebar, #content').fullHeight( config.fullHeight );
}
