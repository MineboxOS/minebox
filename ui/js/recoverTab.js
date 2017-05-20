//handle recover functions
function recoverTab() {

	//handle visibility of fieldset.restore-encryption-key
	var $restoreEncryptionKey = $('.recover-section fieldset.restore-encryption-key'),
		$typeEncryptionKeyButton = $('#type-encryption-key');

	function handleEncryptionKeyInputsVisibility() {
		if ( $restoreEncryptionKey.is(':visible') ) {
			$restoreEncryptionKey.fadeOut(300);
		} else {
			showEncryptionKeyInputs();
		}
	}

	function showEncryptionKeyInputs() {
		$restoreEncryptionKey.fadeIn(300);
	}

	$typeEncryptionKeyButton.on('click', handleEncryptionKeyInputsVisibility);





	//validates 12 words of private key
	var $encryptionKeyInputs = $('.recover-section input.encryption-word');
	var $restoreMineboxButton = $('#restore-minebox-button');

	function validateInputs() {
		//this function validates that the twelve inputs contains something in order to enable de "continue" button
		for ( var n = 0; n < $encryptionKeyInputs.length; n++ ) {
			if ( $($encryptionKeyInputs[n]).val() == '' ) {
				return false;
			}
		}
		return true;
	}

	//when user is typing its private key
	$encryptionKeyInputs.on('keyup', function() {
		if ( validateInputs() ) {
			$restoreMineboxButton.removeAttr('disabled');
		}
	});




	//webcam manager
	//requires instascan, $encryptionKeyInputs, showEncryptionKeyInputs()
	var $webcamButton = $('.webcam-access-button'),
		$closeWebcamButton = $('#close-instascan-button');
	//qr code reader
	$webcamButton.on('click', function() {
		//show camera window
		instascanManager.show();
		//init camera
		instascanManager.scan(function(data) {
			//when scans a QR code returns data
			//scroll to the top
			$('html, body').animate({ scrollTop: 0 }, 100);
			//forcing display encryption key fields
			showEncryptionKeyInputs();
			//filling inputs with a interval time in between
			var qrcodewords = data.split(' ');
			var n = 0;
			var interval = setInterval(function() {
				//filling input with current word
				$($encryptionKeyInputs[n]).val( qrcodewords[n] );
				n++;
				if ( n >= $encryptionKeyInputs.length ) {
					//all fields filled
					//trigger keyup in the first input to activate validation process
					$($encryptionKeyInputs[0]).trigger('keyup');
					//stopping interval
					clearInterval(interval);
				}
			}, 150);
		});
	});

	//close webcam view
	$closeWebcamButton.on('click', function() {
		instascanManager.hide();
	});






	//when user clicks on "continue button"
	$restoreMineboxButton.on('click', function() {
		privateKeyValidation(function() {
			//once the server has validated the words and they are correct
			//take the user to the progress screen
			progressScreen.open();
		});
	});

	function privateKeyValidation(cb) {
		//this function sends the twelve words to the server so they can be validated
		//currently faking the result with "true" with a timeout
		//when the server returns true, execute the callback cb();
		setTimeout(function() {
			if ( true && cb ) {
				cb();
			}
		}, 10000);
	}

}