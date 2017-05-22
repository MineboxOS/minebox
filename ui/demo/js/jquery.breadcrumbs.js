function breadcrumbs( currentID ) {

  var currentElement = null;
  var parentElement = null;
  var crumbs = '';


  function makeCrumbs(id, name) {
    crumbs += '<span data-id="'+id+'">'+name+'</span>';
  }

  function loop( currentElement ) {
    if ( currentElement.parentID ) {
      parentElement = directoryManager.get( currentElement.parentID );
      parentElement = parentElement[0]; //is an array of only one item length
      loop( parentElement );
    }
    makeCrumbs( currentElement.id, currentElement.name );
  }


  currentElement = directoryManager.get( currentID );
  currentElement = currentElement[0]; //is an array of only one length

  loop( currentElement );


  return crumbs;
}
