/*
  requires:
    getRandomString()
    replaceAll();

  params
    data: {
      message: Text to display on the error message **mandatory
      onClose: func() to exec when .close-btn button is clicked **optional
      onAccept: func() to exec when .accept button is clicked **optional
      onCancel: func() to exec when .cancel button is clicked **optional
      onDelete: func() to exec when .delete button is clicked **optional
  }
*/

function Notify(data) {

  var CONFIG = {
    acceptText: 'OK',
    cancelText: 'Cancel'
  };
  $.extend(true, CONFIG, data);

  var settings = {
    id: 'notify-' + getRandomString(5),
    class: 'notify-window',
    interval: 1000
  }
  settings.templates = {
    outer: '<div id="{{id}}" class="' + settings.class + '"><div class="obfuscation-layer"></div><div class="window"><img src="../assets/images/logo/minebox-logo-white.png" class="logo" />{{contents}}<div class="close-btn"></div><div class="buttons clearfix"><button class="accept">{{acceptText}}</button>{{buttons}}</div></div></div>',
    cancel: '<button class="cancel">{{cancelText}}</button>',
    text: '<div class="text">{{text}}</div>'
  }

  function checkAvailability(cb) {
    var interval = null;
    var l = null;
    checker(); //let the snowball start falling!

    function checker() {
      l = $('.' + settings.class).length;
      if ( l && !interval ) { //length is bigger than 0 and a interval has not been set
        intervalFunc('init');
      } else if ( l == 0 ) { //interval has been set and/or length equals to 0... we will know it here!
        intervalFunc('stop');
        cb();
      }
      /*
        if interval has been set and length is not 0, the world keeps turning,
        the birds keep singing and the interval keeps running until one of the
        above becomes true. Hopeful the second one, otherwise this function is
        not properly written!
      */
    }

    function intervalFunc(action) {
      if ( action == 'init' ) {
        interval = setInterval(function() {
          checker();
        }, settings.interval);
      } else if ( action == 'stop' ) {
        clearInterval(interval);
      }
    }
  }

  function print() {
    /*
      Calling to a function that is checking wether a "settings.class" element
      exists. If it does, an interval will be placed to wait until the last
      notification is extint to display current via callback function passed as
      parameter.
    */
    checkAvailability(printNotification);
    function printNotification() {
      var output = '';
      output += settings.templates.outer; //copying general template
      output = replaceAll(output, '{{id}}', settings.id);//replacing id with random generated string
      var text = settings.templates.text; //copying text template
      text = replaceAll(text, '{{text}}', data.message); //replacing {{text}} with incoming message within text var
      output = replaceAll(output, '{{contents}}', text); //including "text" within "output"
      output = replaceAll(output, '{{acceptText}}', CONFIG.acceptText); //replacing acceptText
      if ( data.onCancel ) {
        output = replaceAll(output, '{{buttons}}', settings.templates.cancel); //replacing {{buttons}} with "cancel template"
        output = replaceAll(output, '{{cancelText}}', CONFIG.cancelText); //replacing cancelText
      } else {
        output = replaceAll(output, '{{buttons}}', ''); //replacing {{buttons}} with nothingness
      }
      $('body').append(output);
      setTimeout(function() {
        $('#' + settings.id).fadeIn(300);
      }, 100);
    }
  }

  function close() {
    $('#' + settings.id).fadeOut(300, function() {
      deleteElement();
      if ( data.onClose ) { data.onClose() }
    });
  }

  function cancel() {
    close();
    if ( data.onCancel ) { data.onCancel() }
  }

  function accept() {
    close();
    if ( data.onAccept ) { data.onAccept() }
  }

  function deleteElement() {
    $('#' + settings.id).remove();
    if ( data.onDelete ) { data.onDelete() }
  }

  //handlers
  $('body').on('click', '#' + settings.id + ' .close-btn', function() {
    close();
  });

  $('body').on('click', '#' + settings.id + ' .accept', function() {
    accept();
  });

  $('body').on('click', '#' + settings.id + ' .cancel', function() {
    cancel();
  });

  $('body').on('click', '#' + settings.id + ' .obfuscation-layer', function() {
    close();
  });

  return {
    print: print
  }

}
