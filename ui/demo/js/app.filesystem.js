viewLoader.add('filesystem', fileSystem);

function fileSystem() {

  var $directoryBox = $('#filesystem-tree');
  var $breadcrumbs = $('#breadcrumbs');
  var $loading = $('#loading');
  var dateFormat = Format();
  var CONFIG = {
    templates: {
      li: '<li class="{{kind}} {{status}} clearfix filesystem-row" data-kind="{{kind}}" data-id="{{id}}" data-parent="{{parentID}}" data-slug="{{slug}}" data-size="{{size}}" data-status="{{status}}" data-extension="{{extension}}" data-contract="{{contract}}" data-permissions="{{permissions}}" data-creation="{{creationDate}}" data-modification="{{modificationDate}}" data-last-open="{{lastOpen}}"><span class="inner"><span class="icon"><i class="ic ic-{{icon}}"></i></span><span class="name">{{name}}</span><span class="size">{{size}}</span><span class="permissions">{{permissions}}</span><span class="creation">{{creationDate}}</span><span class="modification">{{modificationDate}}</span><span class="last-open">{{lastOpen}}</span></span></li>'
    }
  };

  //init services
  directoryManager.init(init);
  function init() {
    print();
    $loading.fadeOut(300);
  }




  function print(id) {
    stream = '';
    DIR = directoryManager.get(id);
    DIR = DIR[0].contents;

    //loading breadcrumbs
    $breadcrumbs.html( breadcrumbs(id) );

    //exec
    run( DIR );

    function run( dir ) {

      for ( var n = 0; n < dir.length; n++ ) {
        //if !deleted
        if ( !dir[n].deleted ) {
          template = CONFIG.templates.li;

          if ( dir[n].kind == 'volume' ) {
            template = replaceAll( template, '{{icon}}', 'icomoon-stack' );
          } else if ( dir[n].kind == 'folder' ) {
            template = replaceAll( template, '{{icon}}', 'icomoon-folder' );
          } else if ( dir[n].kind == 'file' ) {
            template = replaceAll( template, '{{icon}}', 'icomoon-file' );
          }

          template = replaceAll( template, '{{id}}', dir[n].id );
          template = replaceAll( template, '{{name}}', dir[n].name );
          template = replaceAll( template, '{{slug}}', dir[n].slug );
          template = replaceAll( template, '{{kind}}', dir[n].kind );
          template = replaceAll( template, '{{size}}', dir[n].size );
          template = replaceAll( template, '{{status}}', dir[n].status );
          template = replaceAll( template, '{{extension}}', dir[n].extension );
          template = replaceAll( template, '{{contract}}', dir[n].contract );
          template = replaceAll( template, '{{permissions}}', dir[n].permissions );
          template = replaceAll( template, '{{parentID}}', dir[n].parentID );
          template = replaceAll( template, '{{creationDate}}', dateFormat.date( dir[n].creationDate ) + ' ' + dateFormat.time( dir[n].creationDate ) );
          template = replaceAll( template, '{{modificationDate}}', dateFormat.date( dir[n].modificationDate ) + ' ' + dateFormat.time( dir[n].modificationDate ) );
          template = replaceAll( template, '{{lastOpen}}', dateFormat.date( dir[n].lastOpen ) + ' ' + dateFormat.time( dir[n].lastOpen ) );
          stream += template;
        }
      }

      $directoryBox.html(stream);
    }

  }



  /*
    EVENTS
  */



  $(document).ready(function() {
    FileInteractionManager();
    FileUploadManager();
    ContextualMenu();
    FileViewManager();
  });


  $('body').on('dblclick', '#filesystem-tree li', function() {
    if ( $(this).attr('data-kind') != 'file' ) {
      print( $(this).attr('data-id') );
    }
  });


  $('body').on('click', '#breadcrumbs span', function() {
    print( $(this).attr('data-id') );
  });









  /*var $container = $('#filesystem-tree');
  var $loading = $('#loading');


  //init treeBuilder
  //passing init() as callback to treeBuilder
  var treeBuilder = new TreeBuilder( config.filesystem );
  treeBuilder.init(function(response) {
    if ( response ) {
      //the tree has been built
      $loading.fadeOut(400, function() {
        $container.html(response);
        init();
      });
    } else {
      //there was an error retrieving templates
      var response = '<p>There was some problems loading filesystem. Refresh your browser please.</p>';
      $container.html(response);
    }
  });




  //init filesystem when treeBuilder has finished
  function init() {
    //when content is loaded
    FileInteractionManager();
    FileUploadManager();
    ContextualMenu();

  }*/

};
