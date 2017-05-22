/* jsing dashboard */
viewLoader.add('wallet', wallet);

function wallet() {

  var $container = $('#wallet'),
      $detailsContainer = $('#wallet-details');/*,
      $pattern = $('#unlock-wallet'),
      pattern = new PatternLock('#' + $pattern.attr('id'), {
        onDraw: function() {
          $pattern.fadeOut(300, init);
        }
      });*/


  function init() {
    walletInfo();
  }


  function walletInfo() {
    get.template('templates/wallet/iteration.html', function(response) {
      success({template: response});
    }, fail);

    function success(response) {
      $detailsContainer.html( response.template );
    }

    function fail() {
      $container.html('Wallet info was unable to recover. Please refresh your browser.');
    }
  }


  function revealer( $revealer ) {
    //button data-reveal wallet
    speed = 300;
    reveal = $revealer.data('reveal');
    $walletBox = $revealer.parents('.wallet');
    $key = $revealer.find('.key');
    $container = $walletBox.find('.' + reveal + '-container');

    function hideAll( doNotHide, cb ) {
      //init vars
      $containers = $walletBox.find('.reveal-container');
      //start iteration
      for ( var n = 0; n < $containers.length; n++ ) {
        //if data-container matches doNotHide and current $container is visible
        if ( $($containers[n]).data('container') != doNotHide && $($containers[n]).hasClass('revealed') ) {
          //hidding container
          hideContainer( $($containers[n]) );
          //writting show on buttons
          key = $($containers[n]).data('container');
          writeShow( $walletBox.find('.revealer[data-reveal="' + key + '"] .key') );
        }
      }

      //exec callback if exists
      if ( cb ) {
        setTimeout(cb, speed * 1.2);
      }
    }

    function hideContainer( $containerElement ) {
      $containerElement.removeClass( 'revealed' );
    }

    function showContainer( $containerElement ) {
      $containerElement.addClass( 'revealed' );
    }

    function writeShow( $keyElement ) {
      $keyElement.html('Show').parents('.revealer').removeClass('active');
    }

    function writeHide( $keyElement ) {
      $keyElement.html('Hide').parents('.revealer').addClass('active');
    }


    //logic

    //hide all except reveal
    hideAll( reveal, function() {
      if ( $container.hasClass('revealed') ) {
        //$container is visible, hide it and writeShow on $key
        writeShow( $key );
        hideContainer( $container );
      } else {
        //$container is not visible, show it and writeHide on $key
        writeHide( $key );
        showContainer( $container );
      }
    });

  }


  $('body').on('click', '.revealer', function() {
    revealer( $(this) );
  });


}
