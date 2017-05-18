function Setup() {
	var register = {};



	//tav navigation
	var tabNavigatorData = {
		buttons: $('.navigation-button'),
		tabs: $('.navigation-tab'),
		defaultTab: 'welcome',
		haltingOn: ['progress']
	};
	var tabNavigator = new TabNavigator(tabNavigatorData);





	//generate private key
	(function encryptionKeyGenerator() {
		var words = ["beautiful","knee","stupid","question","flashy","tub","curvy","cheat","screw","testy","electric","bath","behavior","abiding","tall","royal","hurt","door","kindly","bent","pin","vanish","mindless","defeated","admire","argument","keen","tickle","box","ready","wish","ambitious","yarn","sable","spiffy","busy","snore","guarantee","north","jumbled","selection","bag","sweet","scribble","brash","merciful","miss","dead","number","married","dime","insidious","vulgar","overconfident","achiever","mushy","pointless","sniff","wail","nerv"],
			$encryptionField = $('.register-section .encryption-word'),
			$encryptionKeyStringInput = $('#encryption-key-string');

		function gen() {
			var encryptionKeyString = '';
			var word;
			for ( var n = 0; n < $encryptionField.length; n++ ) {
				word = words[ getRandomInt(0, words.length - 1) ];
				$($encryptionField[n]).val( word );
				encryptionKeyString += word + ' ';
			}
			$encryptionKeyStringInput.val(encryptionKeyString);
		}

		$('body').on('click', '.key-generator', gen);

		$(document).ready(gen);
	})();





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





	//go to scan qr code button
	$('body').on('click', '.go-to-scan-button', function() {
		$('html, body').animate({
				scrollTop: $('.capture-qr-code-encryption-key').offset().top - 40,
		}, 300);
	});



	//qr code reader
	$('body').on('click', '.webcam-access-button', function() {
		instascanManager.show();
		instascanManager.scan(function(data) {
			$('html, body').animate({ scrollTop: 0 }, 100);
			var qrcodewords = data.split(' ');
			var $encryptionWordInputs = $('.recover-section .encryption-word');
			var n = 0;
			var interval = setInterval(function() {

				$($encryptionWordInputs[n]).val( qrcodewords[n] );

				n++;
				if ( n >= $encryptionWordInputs.length ) {
					clearInterval(interval);
				}
			}, 150);
		});
	});
	$('body').on('click', '#close-instascan-button', function() {
		instascanManager.hide();
	});


	//print qr code
	$('body').on('click', '#print-encryption-key-qr-code', function() {
		window.open('setup/print-qr-code.html');

		register.hostname = $('#setup-page .register-section .register-hostname').val()
		register.seed = $('#encryption-key-string').val();
	});



	(function loadingSpace() {
		var $loadingSpace = $('#loading-space');
		var $stars;

		function generateStars() {
			var count = getRandomInt(10,20);
			for ( var n = 0; n < count; n++ ) {
				$loadingSpace.append( '<span class="star" id="' + getRandomString(10) + '"></span>' );
			}
			$stars = $('.star');
		}

		function init() {
			generateStars();
			for ( var n = 0; n < $stars.length; n++ ) {
				animateStars( $($stars[n]) );
			}
		}

		function animateStars( $element ) {
			var props = {};
			var timeout;
			var choice;
			//invert direction
			if ( $element.attr('data-position') == 'left' ) {
				//move to right
				props.left = '1000%';
				$element.attr('data-position', 'right');
			} else if ( $element.attr('data-position') == 'right' ) {
				//move to left
				props.left = '-1000%';
				$element.attr('data-position', 'left');
			} else {
				//left has not been set yet, moving to random
				choice = getRandomInt(0,1);

				if ( !choice ) {
					//move to left
					props.left = '-1000%';
					$element.attr('data-position', 'left');
				} else {
					//move to right
					props.left = '1000%';
					$element.attr('data-position', 'right');
				}
			}

			props.top = getRandomInt(10, 90) + '%';
			props.width = getRandomInt(80,200);
			props.height = props.width / getRandomInt(20,30); //a fraction of its width so it is stylized horizontally
			props.duration = getRandomInt(8,50) / 10;

			$element.css({
				'width': props.width,
				'height': props.height,
				'top': props.top,
				'transform': 'translateX(' + props.left + ')',
				'transition': 'transform ' + props.duration + 's ease, margin-left ' + props.duration + 's ease'
			});

			timeout = setTimeout(function() {
				reanimateStars( $element );
			}, (props.duration * 1000) + 10);

		}

		function reanimateStars( $element ) {
			animateStars($element);
		}


		init();

	})();
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




	