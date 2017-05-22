function FileSelectionManager() {

  var fileSystemRowClass = 'filesystem-row',
      fileSystemRowActiveClass = 'selected',
      $fileSystemTree = $('#filesystem-tree');

  var keysDown = {}, //collecting pressed keys
      aKeyIsDown = false,
      ctrlKey = 17,
      cmdKey = 91,
      shiftKey = 16,
      userHasClicked = false,
      $firstSelected = null; //contains the "last" first selected. Everytime the user clicks or ctrl + click on a filesystem-row element, this var gets updated






  function unselectAll() {
    //unselects all fileSystemRowsClass elements
    $('.'+fileSystemRowClass).removeClass(fileSystemRowActiveClass);
  }







  function selectByElement( $element ) {
    //adds active class to $element received as a parameter
    $element.addClass(fileSystemRowActiveClass);
  }







  function unselectByElement( $element ) {
    //removes active class to $element received as a parameter
    $element.removeClass(fileSystemRowActiveClass);
  }







  function isElementSelected( $element ) {
    //tells if element is selected or not
    return $element.hasClass(fileSystemRowActiveClass);
  }







  function selectByCoordinates( coords ) {
    //get element by coordinates
    var $coordinatesClicked = getElementByCoordinates( coords );
    //if any element is retrieved
    if ( $coordinatesClicked ) {
      if ( isElementSelected($coordinatesClicked) ) {
        unselectByElement( $coordinatesClicked );
      } else {
        selectByElement( $coordinatesClicked );
      }
    }
  }







  function selectUntilCoordinates( coords ) {
    //receives coords obj as param. selects/unselects from the last/first selected element until matching coords
    var $selected = getSelectedElements();
    //getting a list of available elements
    var $elements = $('.' + fileSystemRowClass);
    if ( !$firstSelected ) {
      //if no firstSelected, assuming first of all the elements
      $firstSelected = $elements[0];
    }
    //getting what's was the intended end-of-selection element, if any
    var $coordinatesIntended = getElementByCoordinates(coords);
    //if clicked out of an element do nothing, otherwise select everything from $firstSelected until $coordinatesIntended
    if ( $coordinatesIntended ) {
      //getting startIndexSelection and finishIndexSelection
      var startIndexSelection = null;
      var finishIndexSelection = null;
      for ( var n = 0; n < $elements.length; n++ ) {
        if ( $($elements[n]).attr('data-id') == $firstSelected.attr('data-id') ) {
          startIndexSelection = n;
        }
        if ( $($elements[n]).attr('data-id') == $coordinatesIntended.attr('data-id') ) {
          finishIndexSelection = n;
        }
      }

      //selecting elements
      for ( var n = startIndexSelection; n <= finishIndexSelection; n++ ) {
        selectByElement( $($elements[n]) );
      }
    }
    delete $selected, $elements, $coordinatesIntended;
  }







  function getElementByCoordinates( coords ) {
    //getting a list of available elements
    var $elements = $('.' + fileSystemRowClass);
    //receives coords obj as param. selects/unselects the element that matches coords
    var elementSize = {};
    for ( var n = 0; n < $elements.length; n++ ) {
      elementSize.iniX = $($elements[n]).offset().left;
      elementSize.endX = $($elements[n]).offset().left + $($elements[n]).width() + parseInt($($elements[n]).css('padding-left')) + parseInt($($elements[n]).css('padding-right')) + parseInt($($elements[n]).css('border-left-width')) + parseInt($($elements[n]).css('border-right-width'));
      elementSize.iniY = $($elements[n]).offset().top;
      elementSize.endY = $($elements[n]).offset().top + $($elements[n]).height() + parseInt($($elements[n]).css('padding-top')) + parseInt($($elements[n]).css('padding-bottom')) + parseInt($($elements[n]).css('border-top-width')) + parseInt($($elements[n]).css('border-bottom-width'));
      if ( elementSize.iniX <= coords.x && elementSize.endX >= coords.x && elementSize.iniY <= coords.y && elementSize.endY >= coords.y ) {
        return $($elements[n]);
      }
    }
    return false;
  }







  function getSelectedElements() {
    //returns array of selected elements
    var $ret = [];
    var $elements = $('.' + fileSystemRowClass);
    for ( var n = 0; n < $elements.length; n++ ) {
      if ( $($elements[n]).hasClass(fileSystemRowActiveClass) ) {
        $ret.push( $($elements[n]) );
      }
    }
    return $ret;
  }







  function keysCombinationListener() {

    //if shift + click
    if ( keysDown[shiftKey] && keysDown['click'] ) {
      selectUntilCoordinates( keysDown['click'] );
    }

    //if cmd or ctrl + click
    if ( (keysDown[cmdKey] || keysDown[ctrlKey]) && keysDown['click'] ) {
      selectByCoordinates( keysDown['click'] );
    }

  }









  //select/unselect by click
  $('body').on('click', function() {
    if ( !aKeyIsDown ) {
      unselectAll();
    }
  });
  $('body').on('click', '.filesystem-row', function() {
    //we are tracking if any key is down to not override key combinations listener functions
    if ( !aKeyIsDown ) {
      //storing $(this)
      var $fileSystemClicked = $(this);
      //only mark this as a $firstSelected if no key is down!
      $firstSelected = $fileSystemClicked;
      //do not remove this timeout, we need the actual selection to happen a lil bit later than the unselectAll() from the very previous event binding.
      setTimeout(function() {
        selectByElement( $fileSystemClicked );
      }, 1);
    }
  });








  //multiple selections //http://stackoverflow.com/questions/10655202/detect-multiple-keys-on-single-keypress-event-in-jquery/10655316#10655316

  /* ************
    tracing click
  ************ */

  $('body').on('click', function(e) {
    //if left button click, storing coords in keysDown['click']
    if ( e.button != 2 ) {
      keysDown['click'] = {
        x: e.pageX,
        y: e.pageY
      };
    }
    //set this global var to true
    userHasClicked = true;
    //exec function
    keysCombinationListener();
  });


  /* ***********
    tracing keys
  *********** */
  $(document).keydown(function(e) {
    //setting this global var to true
    aKeyIsDown = true;
    //setting this global var to false so we can track if user has performed a click from now until key release
    userHasClicked = false;
    //storing keyCode of pressed key
    keysDown[e.keyCode] = true;
  }).keyup(function(e) {
    //setting this global var to false
    aKeyIsDown = false;
    //exec function if user has not clicked (since it is already executed right on the click)
    if ( !userHasClicked ) { keysCombinationListener(); }
    //clearing keysDown
    keysDown = {};
  });

}
