//requires notify.js
//requires tabNavigator

function proveTab() {

	//show a notification if register is empty on ready
	var notify = new Notify({
		message: 'Oops! Lost your track?<br />Seems that you have arrived here refreshing your browser or by magic.<br/>You need to have a valid and verified hostname and private key. Validate them again and provide username and password.<br />Press OK to proceed.',
		onAccept: function() {
			tabNavigator.go('register');
		},
		onClose: function() {
			tabNavigator.go('register');
		}
	});

	if ( !register.seed || !register.hostname ) {
		notify.print();
	}



	//print qr code
	$('body').on('click', '#print-encryption-key-qr-code-reminder', function() {
		window.open('print-qr-code.html');
	});




	var $restoreEncryptionKey = $('.prove-section .restore-encryption-key'),
		$encryptionKeyInputs = $('.prove-section .encryption-word'),
		$typeEncryptionKeyButton = $('.type-encryption-key-button');



	//scan qr code
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
			fillEncryptionKeyInputs(data);
		});
	});

	//close webcam view
	$closeWebcamButton.on('click', function() {
		instascanManager.hide();
	});

	$typeEncryptionKeyButton.on('click', showEncryptionKeyInputs);



	//type qr code
	function fillEncryptionKeyInputs(words) {
		//scroll to the top
		$('html, body').animate({ scrollTop: 0 }, 100);
		//forcing display encryption key fields
		showEncryptionKeyInputs();
		//filling inputs with a interval time in between
		var qrcodewords = words.split(' ');
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
	}


	function showEncryptionKeyInputs() {
		$restoreEncryptionKey.fadeIn(300);
	}


	$encryptionKeyInputs.on('keyup', checkRequiredFields);

	//validate all the required fields are filled
	var $requiredFields = $('.prove-section [required]');

	function checkRequiredFields() {
		//any time the user types on a required field check if there is any empty and update register validation object
		for ( var n = 0; n < $requiredFields.length; n++ ) {
			if ( !$($requiredFields[n]).val().length ) {
				submitButtonEnabler(false);
				return false;
			}
		}
		//if we arrived here, everything is filled
		submitButtonEnabler(true);
	}



	//submit button enabler/disabler
	var $submitButton = $('#proved-private-key-button');
	function submitButtonEnabler(enable) {
		if ( enable ) {
			$submitButton.removeAttr('data-disabled');
		} else {
			$submitButton.attr('data-disabled', 'disabled');
		}
	}

	$submitButton.on('click', function() {
		validateEncryptionKey();
	});



	var encryptionKeyRequester = new Requester(),
		encryptionKeyArray = [],
		encryptionKeyString = '';

	function validateEncryptionKey() {
		//encryption key to array
		for ( var n = 0; n < $encryptionKeyInputs.length; n++ ) {
			encryptionKeyArray.push( $($encryptionKeyInputs[n]).val() );
		}
		//storing it also in string
		encryptionKeyString = encryptionKeyArray.join(' ');

		//faking server call here
		//encryptionKeyRequester.setMethod()...

		setTimeout(function() {
			var response = true;

			$submitButton.siblings('.error').html('');

			if ( response ) {
				progressScreen.open();
			} else {
				$submitButton.siblings('.error').html('The key is not valid. Please check you have written everything correctly.');
			}
		}, 1000);


	}




}