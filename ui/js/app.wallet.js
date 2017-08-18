/* jsing dashboard */
viewLoader.add('wallet', Wallet);

function Wallet() {


	var CONFIG = {
		api: {
			address: {
				url: config.mug.url + 'wallet/address',
				requester: new Requester()
			},
			send: {
				url: config.mug.url + 'wallet/send',
				//url: 'api/send/',
				requester: new Requester()
			},
			status: {
				url: config.mug.url + 'wallet/status',
				requester: new Requester()
			}
		},
		wallet: {},
		events: {
			walletStatusLoaded: 'walletStatusLoaded',
			walletAddressLoaded: 'walletAddressLoaded'
		},
		messages: {
			walletStatusLoaded: {
				fail: 'Couldn\'t load wallet status. Try again in a few minutes.'
			},
			walletAddressLoaded: {
				fail: 'Couldn\'t retrieve your wallet address. Please try again in a few minutes.'
			},
			walletSend: {
				fail: 'Funds failed to send. Try again in a few minutes.',
				success: 'Your funds were sent successfully.'
			}
		}
	};




	/* Handles wallet status call */
	function WalletStatusManager() {

		function load() {
			//start loader
			loadingManager.add('Wallet status');
			//calls to the server and gets the funds
			CONFIG.api.status.requester.setURL( CONFIG.api.status.url );
			CONFIG.api.status.requester.setMethod('GET');
			CONFIG.api.status.requester.setCredentials(true);
			CONFIG.api.status.requester.setCache(false);
			CONFIG.api.status.requester.run(function(response) {

				//storing data
				CONFIG.wallet.status = response;

				//rising event
				$('body').trigger( CONFIG.events.walletStatusLoaded );

				//removing loader
				loadingManager.remove('Wallet status');
			}, function(error) {

				//removing loader
				loadingManager.remove('Wallet status');

				var notify = new Notify({ message: CONFIG.messages.walletStatusLoaded.fail });
				notify.print();
			});
		}

		return {
			load: load
		}

	}
	var walletStatusManager = WalletStatusManager();




	/* Handle funds load, format and printing */
	(function FundsManager() {

		$('body').bind( CONFIG.events.walletStatusLoaded, function() {
			//feels the event and prints funds
			printFunds();
		});

		function formatFunds(number) {
			//format number
			return number;
		}

		function printFunds() {
			//printing wallet balance
			$('#wallet-sia .currency-value').html( formatFunds( CONFIG.wallet.status.confirmedsiacoinbalance_sc ) );
			//printing you have available:
			$('#available-amount').html( CONFIG.wallet.status.confirmedsiacoinbalance_sc );

			if ( CONFIG.wallet.status.unconfirmedincominsiacoins_sc ) {
				//printing wallet balance
				$('#wallet-balance-box .unconfirmed-incoming-funds')
					.html( formatFunds( CONFIG.wallet.status.confirmedsiacoinbalance_sc ) )
					.fadeIn(300);
			} else {
				//printing wallet balance
				$('#wallet-balance-box .unconfirmed-incoming-funds')
					.html( 'No funds incoming' )
					.fadeOut(100);
			}
		}

	}());




	/* Handles loading of wallet address */
	function WalletAddressManager() {

		var $walletAddressInput = $('#user-wallet-address');

		function loadWalletAddress() {

			//starting loader
			$walletAddressInput.val('Loading wallet address...');

			CONFIG.api.address.requester.setURL( CONFIG.api.address.url );
			CONFIG.api.address.requester.setMethod( 'GET' );
			CONFIG.api.address.requester.setCache( false );
			CONFIG.api.address.requester.setCredentials( true );
			CONFIG.api.address.requester.run(function(response) {

				//saving response
				CONFIG.wallet.address = response.address;
				//rising an event
				$('body').trigger( CONFIG.events.walletAddressLoaded );
				
			}, function(error) {

				var notify = new Notify({ message: CONFIG.messages.walletAddressLoaded });
				notify.print();
			});
		}

		$('body').bind( CONFIG.events.walletAddressLoaded, function() {
			printWalletAddress();
		});

		function printWalletAddress() {
			$walletAddressInput.val( CONFIG.wallet.address );
		}

		return {
			load: loadWalletAddress
		}
	}
	var walletAddressManager = WalletAddressManager();





	/* Handles send function */
	(function Send() {

		//init vars
		var $sendButton = $('#send-button'),
			$amountToSend = $('#amount-to-send'),
			$addressToSend = $('#send-to-address');

		function collectData() {
			return {
				amount_sc: $amountToSend.val(),
				address: $addressToSend.val()
			}
		}

		function sendFunc() {

			//start loading
			loadingManager.add('Sending funds');

			//collecting form data
			var data = collectData();

			//sending
			CONFIG.api.send.requester.setURL( CONFIG.api.send.url );
			CONFIG.api.send.requester.setMethod( 'POST' );
			CONFIG.api.send.requester.setCache( false );
			CONFIG.api.send.requester.setCredentials( true );
			CONFIG.api.send.requester.setData( data );
			CONFIG.api.send.requester.run(function(response) {

				//stop loading
				loadingManager.remove('Sending funds');

				//re-load wallet funds
				walletStatusManager.load();

				console.log(response);

				//print notification
				var notify = new Notify({message: CONFIG.messages.walletSend.success});
				notify.print();

			}, function(error) {

				//stop loading
				loadingManager.remove('Sending funds');

				//print notification
				var notify = new Notify({message: CONFIG.messages.walletSend.fail});
				notify.print();

			});
		}

		$sendButton.on('click', sendFunc);

	}());





	/* Handles tabs loading */
	(function WalletTabsHandler() {

		//init vars
		var $container = $('#wallet-tabs'),
			$tabs = $container.find('.wallet-tab'),
			$buttons = $('#wallet-tabs-buttons .button'),
			$closeButtons = $container.find('.close-wallet-tab-button'),
			speed = 300,
			activeClass = 'active';

		//active #wallet-tabs
		function showWalletTabs() {
			$container
				.fadeIn( speed )
				.addClass( activeClass );
		}

		//deactive #wallet-tabs
		function hideWalletTabs() {
			$container
				.fadeOut( speed )
				.removeClass( activeClass );
		}

		//show tab function
		function showTab( targetID ) {
			hideTabs();
			showWalletTabs();
			$('#' + targetID)
				.trigger('show')
				.addClass( activeClass )
				.fadeIn( speed );
		}

		//hide all tabs function
		function hideTabs() {
			$tabs
				.fadeOut( speed )
				.removeClass( activeClass );
		}

		//close one specific tab
		function closeTab( targetID ) {
			hideWalletTabs();
			$('#' + targetID)
				.removeClass( activeClass )
				.fadeOut( speed );
		}

		//deactivate all buttons function
		function deactivateButtons() {
			$buttons.removeClass( activeClass );
		}

		//feel the click on the button
		$buttons.on('click', function() {
			//if clicked button is not active already
			if ( !$(this).hasClass( activeClass ) ) {
				//removing activeClass from all the buttons
				deactivateButtons();
				//adding activeClass to clicked button
				$(this).addClass( activeClass );
				//show wished tab
				showTab( $(this).attr('data-tab') );
			}
		});

		//closing wallet tabs
		$closeButtons.on('click', function() {
			//removing activeClass from all the buttons
			deactivateButtons();
			//closing tab
			closeTab( $(this).parents('.wallet-tab').attr('id') );
		});

	}());




	/* Handles user amount-to-send input */
	(function SendFormValidator() {

		//init vars
		var $amountInput = $('#amount-to-send'),
			$addressInput = $('#send-to-address'),
			$sendButton = $('#send-button'),
			$amountFeedback = $('#amount-feedback');

		var errorMessages = {
			notEnoughAmount: 'You don\'t have that many coins.'
		};

		$amountInput.on('keyup', function() {
			validateSendForm();
		});

		$amountInput.on('change', function() {
			validateSendForm();
		});

		$addressInput.on('keyup', function() {
			validateSendForm();
		});

		$addressInput.on('change', function() {
			validateSendForm();
		});

		function validateSendForm() {
			if ( !$addressInput.val().length || !$amountInput.val().length || !checkIfEnoughAmount() ) {
				//if any of the inputs is empty or not enough amount
				disableSendButton();
				return false;
			} else {
				//if all above is correct
				enableSendButton();
			}
		}

			function checkIfEnoughAmount() {
				if ( $amountInput.val() > CONFIG.wallet.status.confirmedsiacoinbalance_sc ) {
					printAmountFeedbackMessage( errorMessages.notEnoughAmount );
					return false;
				} else {
					hideAmountFeedback();
					return true;
				}
			}

				function printAmountFeedbackMessage( message ) {
					$amountFeedback
						.html( message )
						.fadeIn(60);
				}

				function hideAmountFeedback() {
					$amountFeedback
						.fadeOut(60, function() {
							$(this).html(''); //emptying as a callback
						});
				}


			function disableSendButton() {
				$sendButton.attr('disabled', 'disabled');
			}

			function enableSendButton() {
				$sendButton.removeAttr('disabled');
			}


		//execute validation on document ready
		$(document).ready(validateSendForm);


	}());






	/* Copies wallet address to clipboard */
	/* It requires jquery.copyToClipboard.js */
	(function walletAddressCopyOnClick() {

		var $userWalletAddress = $('#user-wallet-address'),
			$userWalletAddressFeedback = $('#user-wallet-address-feedback');

		$userWalletAddress.on('click', function() {
			copyToClipboard( $(this), function() {
				//as a callback, print a message
				$userWalletAddressFeedback.fadeIn(100, function() {
					$(this).delay(1000).fadeOut(1000);
				});
			});
		});

	}());




	/* Handles webcam QR code reader */
	/* Requires instascan.min.js & instascan.js (InstascanManager()) */
	(function WebcamReaderManager() {

		var $webcamButton = $('#scan-address-button'),
			$addressInput = $('#send-to-address'),
			$amountInput = $('#amount-to-send'),
			$closeWebcamButton = $('#close-instascan-button');

		var instascanManager = InstascanManager();

		function printData( data ) {

			var address = null;
			var amount = null;

			//do we have an amount?
			if ( data.indexOf('?amount=') >= 0 ) {
				//splitting by ?amount=
				amount = data.split( '?amount=' );
				//storing amount
				amount = amount[1];
				//removing the amount from data
				data = data.split( '?amount=' );
				//storing address into data
				data = data[0];
			}

			//clean address
			//do we have a ":"?
			if ( data.indexOf(':') >= 0 ) {
				address = data.split(':');
				address = address[1];
			} else {
				address = data;
			}

			//printing
			$addressInput.val( address );
			$amountInput
				.val( amount )
				.trigger('change');


		}

		$webcamButton.on('click', function() {
			instascanManager.show();
			instascanManager.scan(function(data) {
				//content readed, received as data
				printData( data );
			});
		});

		$closeWebcamButton.on('click', function() {
			instascanManager.hide();
		});

	}());





	/* Handles QR code generation */
	/* It requires qrcode.min.js */
	(function QRCodeManager() {

		var qrCodePlugin = new QRCode('qr-code'); //qr-code is the id of the target element, it will be filled with img element

		var $qrCodeWindow = $('#receive-funds-qr-code'),
			$qrCodeShowButton = $('#show-qr-code-button'),
			$qrCodeCloseButton = $('#hide-qr-code-button');

		var $addressInput = $('#user-wallet-address'),
			$amountInput = $('#amount-to-receive');

		function generate() {
			var string = gatherInformation();
			qrCodePlugin.makeCode(string);
			display();
		}

		function display() {
			$qrCodeWindow.fadeIn(60);
		}

		function close() {
			$qrCodeWindow.fadeOut(60);
		}

		function gatherInformation() {
			var string = '';

			if ( $addressInput.val() ) {
				string += 'siacoin:' + $addressInput.val();
			} else {
				return false;
			}

			if ( $amountInput.val() ) {
				string += '?amount=' + $amountInput.val();
			}

			return string;
		}


		$qrCodeShowButton.on('click', function() {
			generate();
		});

		$qrCodeCloseButton.on('click', function() {
			close();
		});


	}());




	$(document).ready(function() {
		walletStatusManager.load();
	});

	$('#receive-tab').bind('show', function() {
		walletAddressManager.load();
	});


}