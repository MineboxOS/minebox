function Setup() {

	//tav navigation
	var tabNavigatorData = {
		buttons: $('.navigation-button'),
		tabs: $('.navigation-tab'),
		defaultTab: 'welcome'
	};
	var tabNavigator = new TabNavigator(tabNavigatorData);









	//handle recover functions
	function recover() {

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
	//executing the function when the tab is opened for first time
	$('.recover-section.navigation-tab').bind('tabShown', function() {
		if ( $(this).attr('data-services') != 'on' ) {
			recover();
			$(this).attr('data-services', 'on');
		}
	});










	function register() {
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
	//executing the function when the tab is opened for first time
	$('.register-section.navigation-tab').bind('tabShown', function() {
		if ( $(this).attr('data-services') != 'on' ) {
			register();
			$(this).attr('data-services', 'on');
		}
	});









	//requires tabNavigator & hashManager
	function ProgressScreen() {

		var $progressScreenElement = $('#progress-screen');
		var loadingSpace = LoadingSpace();

		function open() {
			//init loading space animation
			loadingSpace.init();
			//change hash
			hashManager.write('running');
			//hide all views
			tabNavigator.hideAll(function() {
				//disable tabNavigator 
				tabNavigator.disable();
				//display progress screen
				$progressScreenElement.fadeIn(300);
			});
		}


		return {
			open: open
		}

	}
	var progressScreen = ProgressScreen();

	
}

	


	/*
	var setupsFlowNavigator = SetupsFlowNavigator();
	function SetupsFlowNavigator() {

		var buttonsSelector = '.setup-flow-button',
			tabsSelector = '#setup-page>section',
			$tabs = $(tabsSelector),
			time = 300;



		//on click
		$('body').on('click', buttonsSelector, function() {
			tab = $(this).attr('data-setup-flow');

			if ( $(tabsSelector + '.' + tab + '-section') && $(tabsSelector + '.' + tab + '-section').is(':hidden') ) {
				//if exists and is hidden
				showSetupSection(tab);
			}
		});


		//on init
		$(document).ready(function() {
			init();
		});

		//funcs
		function hideAll() {
			$('html, body').animate({
				scrollTop: 0,
			}, time/2, function() {
				$tabs.fadeOut(0);
				window.location.hash = '';
			});
		}

		function showSetupSection( section ) {
			hideAll();
			setTimeout(function() {
				$(tabsSelector + '.' + section + '-section').fadeIn(time);
				window.location.hash = section;
			}, time + 10);
		}

		function init() {
			if ( !window.location.hash ) {
				$($tabs[0]).fadeIn(time);
			} else {
				hash = window.location.hash;
				hash = hash.substr(1, hash.length);

				if ( $(tabsSelector + '.' + hash + '-selection') ) {
					//if exists
					showSetupSection(hash);
				}
			}
		}

		return {
			init: init,
			show: showSetupSection,
			hide: hideAll
		}

	}*/




	