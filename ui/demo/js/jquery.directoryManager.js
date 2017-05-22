function DirectoryManager() {

  var DIR = [];
  var requester = new Requester();
  var clipBoard = null; //it will keep the files/folders copied or cutted




  function init(cb) {
    requester.setMethod('GET');
    requester.setURL('json/directory.json');
    //requester.setCache(false);
    requester.run(function(response) {
      DIR = response;
      if (cb) {cb();}
    }, function(err) {
      alert('Impossible to retrieve DIR');
    });
  }



  /*
    BASE FUNCTIONS
    The following are the recursive functions
  */

  function getDir( id ) {
    if ( !id ) {
      return DIR;
    } else {
      function getByID( array ) {
        for ( var n = 0; n < array.length; n++ ) {
          if ( array[n].id == id ) { //eval current
            ret = [array[n]];
          }
          if ( array[n].contents.length ) { //re-run if volume or folder with contents on it
            getByID( array[n].contents );
          }
        }
      }

      getByID(DIR);
      return ret;
    }
  }




  function add( parentID, newElement ) {
    if ( !parentID || !newElement ) {
      //validating input
      //generating new (updated) Date
      var d = new Date();
      //force new id
      newElement.id = getRandomString(12) + d.getTime();
      //if no name return alert
      if ( !newElement.name ) {
        alert('No name is provided');
        return false;
      } else {
        //if name, generating slug
        newElement.slug = safeString(newElement.name)
      }
      //if no kind display error
      if ( !newElement.kind ) {
        alert('No kind is provided');
        return false;
      }
      //if no size display error
      if ( !newElement.size ) {
        alert('No size is provided');
        return false;
      }
      //other values
      newElement.status = "syncing";
      newElement.deleted = 0;
      newElement.extension = null;
      newElement.contract = "oewesLn2CDKD";
      newElement.permissions = "rw-r--r--";
      newElement.parentID = parentID;
      //if no creation date make new one. otherwise keep it (from cut/move/copy)
      if ( !newElement.creationDate ) {
        newElement.creationDate = d.getTime();
      }
      //if no modification date make new one. otherwise keep it (from cut/move/copy)
      if ( !newElement.modificationDate ) {
        newElement.modificationDate = d.getTime();
      }
      //if no lastOpen date make new one. otherwise keep it (from cut/move/copy)
      if ( !newElement.lastOpen ) {
        newElement.lastOpen = d.getTime();
      }
      //if no contents generate empty array
      if ( !newElement.contents ) {
        newElement.contents = [];
      }

      //inserting within parent's array
      findParentArray( getDir() );
      function findParentArray( haystack ) {
        for ( var n = 0; n < haystack.length; n++ ) {
          if ( haystack[n].id == parentID ) { //eval current
            haystack[n].contents.push( newElement );
          }
          if ( haystack[n].contents.length ) { //re-run if volume or folder with contents on it
            findParentArray( haystack[n].contents );
          }
        }
      }
    } else {
      alert('parentID & newElement are required');
      return false;
    }
  }




  function remove( id ) {

    if ( !id ) {
      //init vars
      var dir = getDir();
      var parentArray = dir[0].contents; //init parent as first array
      var parentIndex = 0;

      //functions
      function findParentArray( haystack ) {
        for ( var n = 0; n < haystack.length; n++ ) {

          if ( haystack[n].id == id ) { //eval current
            ret = spliceArray();
            ret = ret[0]; //returning pure contents. we know we are removing only 1 iterance
          }

          if ( haystack[n].contents.length ) { //re-run if volume or folder with contents on it
            //temporary storing last array
            parentArray = haystack[n];
            parentIndex = n;
            return findParentArray( haystack[n].contents );
          }

        }
      }

      function spliceArray() {
        return parentArray.contents.splice( parentIndex, 1 );
      }

      //exec
      findParentArray( dir );
      return ret;
    } else {
      alert('id is required');
      return false;
    }

  }



  /*
    SHORTENING FUNCTIONS
    The following are the functions that combine the others above in order to get
    extra functionalities
  */

  function cut( id ) {

    //making sure from is not a volume
    fromElement = getDir(from);
    if ( fromElement.kind != 'volume' ) {
      clipBoard = getDir(id);
      remove(id);
    }

  }



  function copy( id ) {

    //making sure from is not a volume
    fromElement = getDir(id);
    if ( fromElement.kind != 'volume' ) {
      clipBoard = getDir(id);
    }

  }



  function move( from, id, to ) {
    //either from and to are ID's of parent folders

    //making sure FROM is not a volume and TO is not a file
    fromElement = getDir(from);
    toElement = getDir(to);
    if ( fromElement.kind != 'volume' && toElement.kind != 'file' ) {
      //inner clipBoard
      var innerClipBoard = getDir(id);
      remove(from);
      add(to, innerClipBoard);
    }
  }



  function paste( to ) {
    //is there anything in clipboard?
    if ( clipBoard ) {

      //if nothing is selected, changing TO to first volume's ID
      if ( !to ) {
        dir = getDir();
        to = dir[0].id; //getting id of first volume
      } else {
        //if TO is a file ID, changing it to its parent's ID
        toElement = getDir(to);
        if ( toElement.kind == 'file' ) {
          to = toElement.parentID;
        }
      }

      //adding clipBoard contents to "to"
      add( to, clipBoard );
    }
  }



  function createFolder( to, name ) {
    //if nothing is selected, changing TO to first volume's ID
    if ( !to ) {
      dir = getDir();
      to = dir[0].id; //getting id of first volume
    } else {
      //if TO is a file ID, changing it to its parent's ID
      toElement = getDir(to);
      if ( toElement.kind == 'file' ) {
        to = toElement.parentID;
      }
    }

    //creating folder props
    folder = {
      name: name,
      kind: 'folder',
      size: 0
    };

    //if folder.name == '' making it undefined
    if ( folder.name == '' || folder.name == ' ' || folder.name == '&nbsp;' ) {
      folder.name = 'undefined';
    }

    //adding clipBoard contents to "to"
    add( to, folder );
  }



  return {
    init: init,
    get: getDir,
    add: add,
    remove: remove,
    copy: copy,
    move: move,
    cut: cut,
    paste: paste,
    createFolder: createFolder
  }

}
var directoryManager = DirectoryManager();
