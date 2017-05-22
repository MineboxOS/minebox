/* jsing dashboard */
viewLoader.add('plugins', plugins);

function plugins() {

  var plugins = null,
      $pluginsListSidebar = $('#plugins-list-sidebar'),
      $pluginsBox = $('#plugins-box'),
      $plugins, $pluginButtons, $settingsButton;

  function init() {
    getPluginsInfo(function() {
      build(function() {
        //do smth when everything is loaded, built and executed
        loaded();
        $('#sidebar, #content').fullHeight( config.fullHeight );
      });
    });
  }



  init();



  function getPluginsInfo(callback_func) {
    get.plugins.info(function(response) {
      plugins = response;
      if (callback_func) {
        callback_func();
      }
    });
  }



  function build(callback_func) {
    function buildSidebar() {
      get.template('templates/plugins/plugin-sidebar.html', function(template) {
        var output = '';
        for (var n = 0; n < plugins.length; n++) {
          output += template;
          output = replaceAll(output, '{{id}}', plugins[n].id);
          output = replaceAll(output, '{{name}}', plugins[n].name);
          output = replaceAll(output, '{{status}}', plugins[n].status);
        }
        $pluginsListSidebar.html(output);
        completed();
      }, function() {
        fail();
      });
    }
    function buildContent() {
      get.template('templates/plugins/plugin.html', function(template) {
        var output = '';
        for (var n = 0; n < plugins.length; n++) {
          output += template;
          output = replaceAll(output, '{{status}}', plugins[n].status);
          output = replaceAll(output, '{{image}}', plugins[n].image);
          output = replaceAll(output, '{{id}}', plugins[n].id);
          output = replaceAll(output, '{{name}}', plugins[n].name);
          output = replaceAll(output, '{{description}}', plugins[n].description);
        }
        $pluginsBox.html(output);
        completed();
      }, function() {
        fail();
      });
    }
    buildSidebar();
    buildContent();

    var counter = 0;
    function completed() {
      counter++;
      if (counter==2 && callback_func) {
        callback_func();
      }
    }
  }



  function fail() {
    $pluginsBox.html('<p>Something happened, refresh your browser and try again.</p>');
  }




  function loaded() {
    $plugins = $('#plugins-box .plugin');
    $pluginButtons = $('#sidebar .plugin-button');
    $settingsButtons = $('#plugins-box .settings');
    var settingsPressed = false;

    function stateHandler($clicked) {
      var id = $clicked.attr('data-id');
      if ($clicked.hasClass('active')) {
        unactive(id);
      } else {
        active(id);
      }
    }
    function active(id) {
      $('#plugins-box .plugin[data-id="' + id + '"]').removeClass('unactive').addClass('active');
      $('#sidebar .plugin-button[data-id="' + id + '"]').removeClass('unactive').addClass('active');
    }
    function unactive(id) {
      $('#plugins-box .plugin[data-id="' + id + '"]').removeClass('active').addClass('unactive');
      $('#sidebar .plugin-button[data-id="' + id + '"]').removeClass('active').addClass('unactive');
    }


    $settingsButtons.on('click', function() {
      settingsPressed = true;
      setTimeout(function() {
        settingsPressed = false;
      }, 100);
    });

    $plugins.on('click', function() {
      if (!settingsPressed) { stateHandler( $(this) ) }
    });

    $pluginButtons.on('click', function() {
      stateHandler( $(this) );
    });
  }

};
