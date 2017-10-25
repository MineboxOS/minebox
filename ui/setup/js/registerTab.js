function registerTab() {

	//register validation object
	//requires objectLength and objectKeys functions
	var registerValidation = RegisterValidationManager();
	function RegisterValidationManager() {

		var CONFIG = {
			events: {
				update: {
					target: '.register-section',
					name: 'validationStatusUpdated'
				},
				validated: {
					target: '.register-section',
					name: 'validated'
				},
				notValidated: {
					target: '.register-section',
					name: 'notValidated'
				}
			}
		};

		var validation = {
			hostname: false,
			password: false,
			required: false
		};

		function status(key) {
			if (key) {
				return validation[key];
			} else {
				return validation;
			}
		}

		function update(obj) {
			//updating validation obj
			$.extend(validation, obj, true);
			//rising updated event
			$(CONFIG.events.update.target).trigger(CONFIG.events.update.name);
			//checking if everything is validated or not and rise an event
			validationControl();
		}

		function validationControl() {
			//checks everything within the validation object and rises the proper event
			var result = true;
			var keys = objectKeys(validation);
			for ( var n = 0; n < objectLength(validation); n++ ) {
				if (! validation[ keys[n] ] ) {
					result = false;
				}
			}
			//rising events
			if ( result ) {
				$(CONFIG.events.validated.target).trigger(CONFIG.events.validated.name);
			} else {
				$(CONFIG.events.notValidated.target).trigger(CONFIG.events.notValidated.name);
			}
		}

		return {
			status: status,
			update: update
		}
	}




	//validate hostname
	var hostnameValidationConfig = {
			requirements: {
				min: 4,
				max: false,
				numbers: false,
				capitals: false,
				specialChars: true, //is a false true, special chars are banned
				strength: false
			},
			messages: {
				min: 'Your hostname needs to be longer than 4 characters.',
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

		//updating validation object to false until server says otherwise
		registerValidation.update({hostname: false});

		//making sure it meets the requirements before asking for availability
		hostnameValidation = hostnameValidator.validate( $hostnameInput.val() );

		//hostname is too short
		if ( !hostnameValidation.min.validated ) {
			//hidding validated display
			$hostnameValidationWitness.find('.validated').fadeOut(50);
			//showing not validated display
			$hostnameValidationWitness.find('.not-validated').fadeIn(50);
			//printing error in hostname response
			$hostnameResponse.html( hostnameValidation.min.message );
			//updatevalidation object
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
			//updatevalidation object
			return false;
		}

		//if the requirements are met, proceed
		//hidding validated display
		$hostnameValidationWitness.find('.validated').fadeOut(50);
		//showing not validated display
		$hostnameValidationWitness.find('.not-validated').fadeIn(50);
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
				//updating global object
				register.hostname = $hostnameInput.val();
				//update validation object
				registerValidation.update({hostname: true});
			} else {
				//hidding validated display
				$hostnameValidationWitness.find('.validated').fadeOut(50);
				//showing not validated display
				$hostnameValidationWitness.find('.not-validated').fadeIn(50);
				//printing result in hostname response
				$hostnameResponse.html('This hostname is not available. Try another.');
				//update validation object
				registerValidation.update({hostname: false});
			}
		});
	}

	//asks the server if the hostname is available
	function hostnameAvailability(cb) {
		//loading witness
		loadingWitness.start();

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

			//loading witness
			loadingWitness.start();

			//asking the server
			//hostnameRequester.setMethod....

			//temporal timeout. modify when MUG actually checks hostname availability
			setTimeout(function() {
				//loading witness
				loadingWitness.stop();
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
				//loading witness
				loadingWitness.stop();
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



	//validate all the required fields are filled
	var $requiredFields = $('.register-section [required]');

	function checkRequiredFields() {
		//any time the user types on a required field check if there is any empty and update register validation object
		for ( var n = 0; n < $requiredFields.length; n++ ) {
			if ( !$($requiredFields[n]).val().length ) {
				registerValidation.update({required: false});
				return false;
			}
		}
		//if we arrived here, everything is filled
		registerValidation.update({required: true});
	}

	$requiredFields.bind('keyup', checkRequiredFields);



	//generate private key
	(function encryptionKeyGenerator() {
		var $encryptionField = $('.register-section .encryption-word'),
			$encryptionKeyStringInput = $('#encryption-key-string');

		var wordsRequester = new Requester();

		function print(array) {

			//print words
			for ( var n = 0; n < $encryptionField.length; n++ ) {
				//filling inputs
				$($encryptionField[n]).val( array[n] );
			}
			//writting sentence
			$encryptionKeyStringInput.val( array.join(' ') );

			//updating global object
			register.seed = $encryptionKeyStringInput.val();

			//forcing register validation to know that this fields are filled
			//since this function was originally listening for "keyup" events and this generator
			//can not fake the keyup event, we are calling the function straight
			checkRequiredFields();

		}

		function getWords() {
			//loading witness
			loadingWitness.start();
			wordsRequester.setURL( config.mug.url + 'key/generate' );
			wordsRequester.setMethod('GET');
			wordsRequester.run(function( response ) {
				print(response);
				//loading witness
				loadingWitness.stop();
			}, function( error ) {
				//error;
				//loading witness
				loadingWitness.stop();
			});
		}

		$('body').on('click', '.key-generator', getWords);

		$(document).ready(getWords);
	})();




	//password validation
	(function passwordValidation() {
		var $passwordInput = $('.register-section .register-password'),
			$passwordRepeatInput = $('.register-section .register-password-repeat'),
			$passwordStrength = $('.register-section .password-strength'),
			$passwordStrengthBar = $('.register-section .password-strength-bar')
			$passwordValidationChecker = $('.password-validation-checker.password-input'),
			$repeatPasswordValidationChecker = $('.password-validation-checker.repeat-password-input'),
			$passwordResumeBox = $('.password-resume');

		//instancing password checker function
		var data = {
			requirements: {
				min: false,
				max: false,
				capitals: false,
				numbers: false,
				specialChars: false,
				strength: 1
			}
		};
		var passwordChecker = new PasswordCheck(data);

		//on user typing
		$passwordInput.on('keyup', function() {
			//setting validation object password to false until script finishes and says otherwise
			registerValidation.update({password: false});
			//checking if password meets the requirements while user types it
			//update strength bar
			strengthWitnessHandler();
			//checking if password matches
			doesPasswordMatch();
			//displaying password resume
			$passwordResumeBox.show(600);
		});

		$passwordRepeatInput.on('keyup', function() {
			//setting validation object password to false until script finishes and says otherwise
			registerValidation.update({password: false});
			//does the passwords match?
			doesPasswordMatch();
		});

		//eval passwords matching and handle witnesses
		function doesPasswordMatch() {
			//this function is executed anytime the user types either password-repeat or password inputs
			//we have to make sure beforehands that there is something written in both inputs
			if ( $passwordInput.val().length && $passwordRepeatInput.val().length ) {
				//there is text written in both inputs
				if ( passwordChecker.match( $passwordInput.val(), $passwordRepeatInput.val() ) ) {
					//the texts match
					//hide unmatch notification
					//show matching notification
					$repeatPasswordValidationChecker.find('.validated').fadeIn(50);
					$repeatPasswordValidationChecker.find('.not-validated').fadeOut(50);
					//updating validation object
					registerValidation.update({password: true});

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
				//there is, at least, on of the inputs empty or password provided is not valid. hiding all notifications
				//hide unmatch notification
				//hide matching notification
				$repeatPasswordValidationChecker.find('.not-validated').fadeOut(50);
				$repeatPasswordValidationChecker.find('.validated').fadeOut(50);
			}

			//returning result
			return false;

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
		//opening the new window
		window.open('print-qr-code.html');
	});


	//submit button enabler/disabler listening to the events
	$('.register-section').bind('validated', function() {
		$('#register-minebox-button').removeAttr('data-disabled');
	});
	$('.register-section').bind('notValidated', function() {
		$('#register-minebox-button').attr('data-disabled', 'disabled');
	});


	//if clicking in submit without being everything validated
	//display an error
	$('#register-minebox-button').on('click', function() {
		//emptying error
		$(this).siblings('.error').html('');
		//if hostname is not valid
		if ( !registerValidation.status('hostname') ) {
			$(this).siblings('.error').append('Your hostname is not valid<br />');
		}
		//if passwords are not valid
		if ( !registerValidation.status('password') ) {
			$(this).siblings('.error').append('Passwords are not valid<br />');
		}
		//if not every required fields are filled
		if ( !registerValidation.status('required') ) {
			$(this).siblings('.error').append('Check again you have properly filled all the fields.<br />');
		}
		if (!$(this).attr('data-go')) {
				progressScreen.open('register');
		}
	});

}