/* requires replaceAll() */


function TreeBuilder( config ) {


  var CONFIG = {
    templates: {
      li: null,
      ul: null
    },
    files: null,
    stream: ''
  };
  $.extend(true, CONFIG, config);


  function init(callback_func) {
    return retrieveInfo(function(response) {
      if ( response ) {
        buildFoldersTree(function(response) {
          if ( callback_func ) {
            callback_func(response);
          }
        });
      } else {
        return false;
      }
    });
  }



  function retrieveInfo(callback_func) {
    //retrieving ul
    //get.template(CONFIG.templates.addresses.ul, successUl, fail);
    //retrieving li
    //get.template(CONFIG.templates.addresses.li, successLi, fail);
    //retrieving files/folders json
    //get.filesAndFolders(successFiles, fail);


    function successUl(response) {
      CONFIG.templates.ul = response;
      success();
    }
    function successLi(response) {
      CONFIG.templates.li = response;
      success();
    }
    function successFiles(response) {
      CONFIG.files = response;
      success();
    }
    var count = 0;
    function success() {
      count++;
      if (count == 3 && callback_func) {
        callback_func(true);
      }
    }
    function fail() {
      if (callback_func) {
        callback_func(false);
      }
    }
  }



  function buildFoldersTree(callback_func) {
    for ( var n = 0; n < CONFIG.files.length; n++ ) {
      if ( CONFIG.files[n].type == 'volume' ) {
        buildVolume( CONFIG.files[n] );
      } else if ( CONFIG.files[n].type == 'folder' ) {
        buildFolder( CONFIG.files[n] );
      } else if ( CONFIG.files[n].type == 'file' ) {
        buildFile( CONFIG.files[n] );
      }
    }
    sort(callback_func);
  }

  function buildVolume( obj ) {
    var outputUl = CONFIG.templates.ul;
    outputUl = replaceAll(outputUl, '{{id}}', obj.id);
    outputUl = replaceAll(outputUl, '{{parent}}', obj.parent);
    outputUl = replaceAll(outputUl, '{{slug}}', obj.slug);
    var outputLi = CONFIG.templates.li;
    outputLi = replaceAll(outputLi, '{{type}}', obj.type);
    outputLi = replaceAll(outputLi, '{{status}}', obj.status);
    outputLi = replaceAll(outputLi, '{{id}}', obj.id);
    outputLi = replaceAll(outputLi, '{{parent}}', obj.parent);
    outputLi = replaceAll(outputLi, '{{slug}}', obj.slug);
    outputLi = replaceAll(outputLi, '{{encrypted}}', obj.encrypted);
    outputLi = replaceAll(outputLi, '{{icon}}', obj.icon);
    outputLi = replaceAll(outputLi, '{{name}}', obj.name);

    CONFIG.stream += outputLi + outputUl;
  }
  function buildFolder( obj ) {
    var outputUl = CONFIG.templates.ul;
    outputUl = replaceAll(outputUl, '{{id}}', obj.id);
    outputUl = replaceAll(outputUl, '{{parent}}', obj.parent);
    outputUl = replaceAll(outputUl, '{{slug}}', obj.slug);
    var outputLi = CONFIG.templates.li;
    outputLi = replaceAll(outputLi, '{{type}}', obj.type);
    outputLi = replaceAll(outputLi, '{{status}}', obj.status);
    outputLi = replaceAll(outputLi, '{{id}}', obj.id);
    outputLi = replaceAll(outputLi, '{{parent}}', obj.parent);
    outputLi = replaceAll(outputLi, '{{slug}}', obj.slug);
    outputLi = replaceAll(outputLi, '{{encrypted}}', obj.encrypted);
    outputLi = replaceAll(outputLi, '{{icon}}', obj.icon);
    outputLi = replaceAll(outputLi, '{{name}}', obj.name);

    CONFIG.stream += outputLi + outputUl;
  }
  function buildFile( obj ) {
    var output = CONFIG.templates.li;
    output = replaceAll(output, '{{type}}', obj.type);
    output = replaceAll(output, '{{status}}', obj.status);
    output = replaceAll(output, '{{id}}', obj.id);
    output = replaceAll(output, '{{parent}}', obj.parent);
    output = replaceAll(output, '{{slug}}', obj.slug);
    output = replaceAll(output, '{{encrypted}}', obj.encrypted);
    output = replaceAll(output, '{{icon}}', obj.icon);
    output = replaceAll(output, '{{name}}', obj.name);
    CONFIG.stream += output;
  }
  function sort(callback_func) {
    //create temp DOM element
    $('body').append('<ul id="tree_temp_element" style="display:none;"></ul>');
    //printing stream within temp element
    $('#tree_temp_element').html( CONFIG.stream );
    //storing elements
    var $files = $('#tree_temp_element .file');
    var $inner = $('#tree_temp_element .folder-inner');
    var $folders = $('#tree_temp_element .folder');
    var $volumes = $('#tree_temp_element .volume');
    var $containers = $('#tree_temp_element .volume, #tree_temp_element .folder');

    //putting elements within its parents
    //putting files within inner
    var $parent = null;
    for ( var n = 0; n < $files.length; n++ ) {
      $parent = findParent( $inner, $($files[n]).attr('data-parent') );
      $($files[n]).appendTo( $parent );
    }
    //putting inner within folders and volumes ($containers)
    for ( var n = 0; n < $inner.length; n++ ) {
      $parent = findParent( $containers, $($inner[n]).attr('data-id') ); //li and ul match id's at this point
      $($inner[n]).appendTo( $parent );
    }
    //putting folders within volumes
    for ( var n = 0; n < $folders.length; n++ ) {
      $parent = findParent( $volumes, $($folders[n]).attr('data-parent') );
      $($folders[n]).appendTo( $parent.children('.folder-inner') );
    }

    //function to find parent element according to parentID (data-parent)
    function findParent($elements, id) {
      for ( var n = 0; n < $elements.length; n++ ) {
        if ( $($elements[n]).attr('data-id') == id ) {
          return $($elements[n]);
        }
      }
    }

    //saving contents back into the stream
    CONFIG.stream = $('#tree_temp_element').html();

    //removing temp container
    $('#tree_temp_element').remove();
    //calling to callback_func when everything is ready*/
    if ( callback_func ) {
      callback_func( CONFIG.stream );
    }

  }



  return {
    init: init
  }

}
