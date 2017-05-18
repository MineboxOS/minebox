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

			$('body').on('click', '.key-generator', gen);

			$(document).ready(function() {
				var r = new Requester();
				r.setURL( config.mug.url + 'key/generate' );
				r.setMethod('GET');
				r.run(function( response ) {
					gen(response);
				}, function( error ) {
					//error;
				});
			});
		})();




		//password validation
		(function passwordValidation() {
			var $passwordInput = $('.register-section .register-password'),
				$passwordStrength = $('.register-section .password-strength'),
				$passwordStrengthBar = $('.register-section .password-strength-bar');

			$passwordInput.on('keyup', function() {
				var strength = measurePasswordStrength()

				if ( !strength ) {
					$passwordStrength.html('Low');
				} else if ( strength == 1 ) {
					$passwordStrength.html('Medium');
				} else {
					$passwordStrength.html('High');
				}
				$passwordStrengthBar.css('width', ($passwordInput.val().length * 10) + '%');
			});

			function measurePasswordStrength() {
				var pw = $passwordInput.val();

				if ( pw.length <= 3 ) {
					return 0;
				} else if ( pw.length <= 8 ) {
					return 1;
				} else {
					return 2;
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




	