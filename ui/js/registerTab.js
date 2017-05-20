function registerTab() {

	//validate hostname
	var hostnameValidationConfig = {
			requirements: {
				min: 4,
				max: 16,
				numbers: false,
				capitals: false,
				specialChars: true, //is a false true, special chars are banned
				strength: false
			},
			messages: {
				max: 'Your hostname needs to be shorter than 16 characters long.',
				min: 'Your hostname needs to be between 4 and 16 characters long.',
				specialChars: 'You\'ve provided invalid characters.'
			}
		},
		hostnameValidation = null,
		hostnameValidator = new PasswordCheck(hostnameValidationConfig),
		$hostnameInput = $('.register-hostname'),
		$hostnameValidationWitness = $('.hostname-validation'),
		$hostnameLoadingSpinner = $('.hostname-loading'),
		$hostnameResponse = $('.hostname-response'),
		hostnameRequester = new Requester(),
		hostnameRequestStatus = null,
		hostnameRequesterTimeout = null, //a timeout to prevent constantly calls to the server
		hostnameRequesterTimeoutDuration = 1500; //ms

	$hostnameInput.on('keyup', function() {
		validateHostname();
	});

	function validateHostname() {

		//making sure it meets the requirements before asking for availability
		hostnameValidation = hostnameValidator.validate( $hostnameInput.val() );
		console.log(hostnameValidation);

		//hostname is too short
		if ( !hostnameValidation.min.validated ) {
			//hidding validated display
			$hostnameValidationWitness.find('.validated').fadeOut(50);
			//showing not validated display
			$hostnameValidationWitness.find('.not-validated').fadeIn(50);
			//printing error in hostname response
			$hostnameResponse.html( hostnameValidation.min.message );
			//breaking function and returning false
			return false;
		}

		//hostname is too long
		if ( !hostnameValidation.max.validated ) {
			//hidding validated display
			$hostnameValidationWitness.find('.validated').fadeOut(50);
			//showing not validated display
			$hostnameValidationWitness.find('.not-validated').fadeIn(50);
			//printing error in hostname response
			$hostnameResponse.html( hostnameValidation.max.message );
			//breaking function and returning false
			return false;
		}

		//there are illegal character: ^ a-z/0-9/-_
		if ( hostnameValidation.specialChars.validated ) { //if there are illegal chars (validation returns true in case there are chars that are not a-z, 0-9 and - or _)
			//hidding validated display
			$hostnameValidationWitness.find('.validated').fadeOut(50);
			//showing not validated display
			$hostnameValidationWitness.find('.not-validated').fadeIn(50);
			//printing error in hostname response
			$hostnameResponse.html( hostnameValidationConfig.messages.specialChars );
			//breaking function and returning false
			return false;
		}

		//if the requirements are met, proceed

		//showing validated display
		$hostnameValidationWitness.find('.validated').fadeIn(50);
		//hidding not validated display
		$hostnameValidationWitness.find('.not-validated').fadeOut(50);
		//emptying in hostname response
		$hostnameResponse.html('');

		//if we arrived here, ask the server if the domain is available
		hostnameAvailability(function(response) {
			if ( response ) {
				//showing validated display
				$hostnameValidationWitness.find('.validated').fadeIn(50);
				//hidding not validated display
				$hostnameValidationWitness.find('.not-validated').fadeOut(50);
				//printing result in hostname response
				$hostnameResponse.html('Congratulations! This hostname is available for you.');
			} else {
				//hidding validated display
				$hostnameValidationWitness.find('.validated').fadeOut(50);
				//showing not validated display
				$hostnameValidationWitness.find('.not-validated').fadeIn(50);
				//printing result in hostname response
				$hostnameResponse.html('This hostname is not available. Try another.');
			}
		});
	}

	//asks the server if the hostname is available
	function hostnameAvailability(cb) {

		//init loading
		hostnameLoadingWitness('start');
		//it will end at the end of the request

		//checking if there is a timeout active and killing it
		if ( hostnameRequesterTimeout ) {
			//clearing timeout
			clearTimeout( hostnameRequesterTimeout );
			hostnameRequesterTimeout = null;
		}
		//(re)starting it
		hostnameRequesterTimeout = setTimeout(function() {
			//setting status to active
			hostnameRequestStatus = 'querying';

			//asking the server
			//hostnameRequester.setMethod....

			//temporal timeout. modify when MUG actually checks hostname availability
			setTimeout(function() {
				if ( !hostnameRequesterTimeout ) {
					//if there is another query on the way
					//DO NOT EXECUTE THE CALLBACK
					//a most updated result is on its way!
					//exec cb
					cb(true);
				}
				//updating status
				hostnameRequestStatus = null;
				//ending loading
				hostnameLoadingWitness('stop');
			}, 1000);
			//end of temporal fake function

			//clearing timeout
			clearTimeout( hostnameRequesterTimeout );
			hostnameRequesterTimeout = null;

		}, hostnameRequesterTimeoutDuration);
	}


	function hostnameLoadingWitness(action) {
		if ( action == 'start' ) {
			$hostnameLoadingSpinner.addClass('active');
		} else {
			$hostnameLoadingSpinner.removeClass('active');
		}
	}



	//generate private key
	(function encryptionKeyGenerator() {
		var words = ["beautiful","knee","stupid","question","flashy","tub","curvy","cheat","screw","testy","electric","bath","behavior","abiding","tall","royal","hurt","door","kindly","bent","pin","vanish","mindless","defeated","admire","argument","keen","tickle","box","ready","wish","ambitious","yarn","sable","spiffy","busy","snore","guarantee","north","jumbled","selection","bag","sweet","scribble","brash","merciful","miss","dead","number","married","dime","insidious","vulgar","overconfident","achiever","mushy","pointless","sniff","wail","nerv"],
			$encryptionField = $('.register-section .encryption-word'),
			$encryptionKeyStringInput = $('#encryption-key-string');

		function gen(array) {

			//if array is not passed by, creating one with random words
			if ( !array ) {
				array = [];
				for ( var n = 0; n < $encryptionField.length; n++ ) {
					array.push( words[ getRandomInt(0, words.length - 1) ] );
				}
			}

			//print words either created or passed by ajax request
			for ( var n = 0; n < $encryptionField.length; n++ ) {
				//filling inputs
				$($encryptionField[n]).val( array[n] );
			}
			//writting sentence
			$encryptionKeyStringInput.val( array.join(' ') );
		}

		function getWords() {
			var r = new Requester();
			r.setURL( config.mug.url + 'key/generate' );
			r.setMethod('GET');
			r.run(function( response ) {
				gen(response);
			}, function( error ) {
				//error;
			});
		}

		$('body').on('click', '.key-generator', gen);

		$(document).ready(function() {
			getWords();
		});
	})();




	//password validation
	(function passwordValidation() {
		var $passwordInput = $('.register-section .register-password'),
			$passwordRepeatInput = $('.register-section .register-password-repeat'),
			$passwordStrength = $('.register-section .password-strength'),
			$passwordStrengthBar = $('.register-section .password-strength-bar')
			$passwordValidationChecker = $('.password-validation-checker.password-input'),
			$repeatPasswordValidationChecker = $('.password-validation-checker.repeat-password-input');

		//password validation witnesses
		var $minValidation = $('.password-requirements-validation.min-validation'),
			$numbersValidation = $('.password-requirements-validation.number-validation'),
			$capitalValidation = $('.password-requirements-validation.capital-validation'),
			$strengthValidation = $('.password-requirements-validation.strength-validation');

		//instancing password checker function
		var data = {
			requirements: {
				min: 3,
				max: false,
				capitals: true,
				numbers: true,
				specialChars: false,
				strength: 80
			}
		};
		var passwordChecker = new PasswordCheck(data);

		//on user typing
		$passwordInput.on('keyup', function() {
			//checking if password meets the requirements while user types it
			//updating password requirements validation witnesses
			passwordRequirementsWitnessHandler();
			//update strength bar
			strengthWitnessHandler();
			//checking if password matches
			doesPasswordMatch();
		});

		$passwordRepeatInput.on('keyup', function() {
			//does the passwords match?
			doesPasswordMatch();
		});

		//eval passwords matching and handle witnesses
		function doesPasswordMatch() {
			//this function is executed anytime the user types either password-repeat or password inputs
			//we have to make sure before that there is something written in both inputs
			if ( $passwordInput.val().length && $passwordRepeatInput.val().length ) {
				if ( passwordChecker.match( $passwordInput.val(), $passwordRepeatInput.val() ) ) {
					//there is text written in both inputs and they match
					//hide unmatch notification
					//show matching notification
					$repeatPasswordValidationChecker.find('.validated').fadeIn(50);
					$repeatPasswordValidationChecker.find('.not-validated').fadeOut(50);

					//returning result
					return true;
				} else {
					//there is text written in both inputs but they do not match
					//show unmatch notification
					//hide matching notification
					$repeatPasswordValidationChecker.find('.not-validated').fadeIn(50);
					$repeatPasswordValidationChecker.find('.validated').fadeOut(50);
				}
			} else {
				//there is, at least, on of the inputs empty. hiding all notifications
				//hide unmatch notification
				//hide matching notification
				$repeatPasswordValidationChecker.find('.not-validated').fadeOut(50);
				$repeatPasswordValidationChecker.find('.validated').fadeOut(50);
			}

			//returning result
			return false;

		}

		//handle password requirements witnesses
		function passwordRequirementsWitnessHandler() {
			var validationResults = passwordChecker.validate( $passwordInput.val() );
			var everything = true;
			//handling visibility of minimum characters validation
			if ( validationResults.min.validated ) {
				$minValidation.find('.not-validated').fadeOut(50);
				$minValidation.find('.validated').fadeIn(50);
			} else {
				$minValidation.find('.validated').fadeOut(50);
				$minValidation.find('.not-validated').fadeIn(50);
				everything = false;
			}

			//handling visibility of capital characters validation
			if ( validationResults.capitals.validated ) {
				$capitalValidation.find('.not-validated').fadeOut(50);
				$capitalValidation.find('.validated').fadeIn(50);
			} else {
				$capitalValidation.find('.validated').fadeOut(50);
				$capitalValidation.find('.not-validated').fadeIn(50);
				everything = false;
			}

			//handling visibility of numbers validation
			if ( validationResults.numbers.validated ) {
				$numbersValidation.find('.not-validated').fadeOut(50);
				$numbersValidation.find('.validated').fadeIn(50);
			} else {
				$numbersValidation.find('.validated').fadeOut(50);
				$numbersValidation.find('.not-validated').fadeIn(50);
				everything = false;
			}

			//handling visibility of strength validation
			if ( validationResults.strength.validated ) {
				$strengthValidation.find('.not-validated').fadeOut(50);
				$strengthValidation.find('.validated').fadeIn(50);
			} else {
				$strengthValidation.find('.validated').fadeOut(50);
				$strengthValidation.find('.not-validated').fadeIn(50);
				everything = false;
			}

			//if some of the previous validations returns false it is collected
			//within "everything" variable. if true, display "valid password"
			//witness, otherwise display invalid pw.
			if ( everything ) {
				//the password is valid
				$passwordValidationChecker.find('.not-validated').fadeOut(50);
				$passwordValidationChecker.find('.validated').fadeIn(50);
			} else {
				//password is not valid
				$passwordValidationChecker.find('.validated').fadeOut(50);
				$passwordValidationChecker.find('.not-validated').fadeIn(50);
			}

			//returning total validation value
			return everything;
		}

		//handle strength bar witness
		function strengthWitnessHandler() {
			var validationResults = passwordChecker.validate( $passwordInput.val() );
			//handling bar
			$passwordStrengthBar.css('width', validationResults.strength.score + '%');
			//handling word value
			if ( validationResults.strength.score < 30 ) {
				$passwordStrength.html('Low');
			} else if ( validationResults.strength.score < 80 ) {
				$passwordStrength.html('Medium');
			} else {
				$passwordStrength.html('High');
			}
		}
	})();



	//print qr code
	$('body').on('click', '#print-encryption-key-qr-code', function() {
		window.open('print-qr-code.html');

		register.hostname = $('#setup-page .register-section .register-hostname').val()
		register.seed = $('#encryption-key-string').val();
	});
}