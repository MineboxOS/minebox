function WalletOperatives() {

  function sendForm( obj ) {
    function successCallback( response ) {
      $('#tab-send .form').fadeOut(100, function() {
        $('#tab-send .response').fadeIn(0);
        $('#tab-send .response-200').fadeIn(100, function() {
          $(window).trigger('resize');
        });
      });
    }
    function errorCallback( response ) {
      $('#tab-send .form').fadeOut(100, function() {
        $('#tab-send .response').fadeIn(0);
        $('#tab-send .response-' + response.status).fadeIn(100, function() {
          $(window).trigger('resize');
        });
      });
    }
    get.walletOperatives.send( obj, successCallback, errorCallback );
  }

  function resetSendForm() {
    $('#tab-send input').val('');
    $('#tab-send .response').fadeOut(100, function() {
      $('#tab-send .form').fadeIn(100);
      $('#tab-send .response>*[class^="response-"]').fadeOut(0);
    });
  }


  function receiveForm( obj ) {
    function successCallback( response ) {
      $('#tab-receive .form').fadeOut(100, function() {
        $('#tab-receive .response').fadeIn(0);
        $('#tab-receive .response-200').html('<img src="' + response.qr + '" />').fadeIn(100, function() {
          $(window).trigger('resize');
        });
      });
    }
    function errorCallback( response ) {
      $('#tab-receive .form').fadeOut(100, function() {
        $('#tab-receive .response').fadeIn(0);
        $('#tab-receive .response-' + response.status).fadeIn(100, function() {
          $(window).trigger('resize');
        });
      });
    }
    get.walletOperatives.receive( obj, successCallback, errorCallback );
  }

  function resetReceiveForm() {
    $('#tab-receive input').val('');
    $('#tab-receive .response').fadeOut(100, function() {
      $('#tab-receive .form').fadeIn(100);
      $('#tab-receive .response>*[class^="response-"]').fadeOut(0);
    });
  }

  return {
    send: {
      do: sendForm,
      reset: resetSendForm
    },
    receive: {
      do: receiveForm,
      reset: resetReceiveForm
    }
  }
}
