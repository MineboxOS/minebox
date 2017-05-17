var register = {};



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

}




var encryptionKeyGenerator = EncryptionKeyGenerator();
function EncryptionKeyGenerator() {
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


	return {
		generate: gen
	}

}



(function passwordStrenghtMeasurement() {
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