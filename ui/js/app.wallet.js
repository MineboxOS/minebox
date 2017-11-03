var r = null;
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
			},
			transactions: {
				url: config.mug.url + 'wallet/transactions',
				requester: new Requester()
			},
			settings: {
				url: config.mug.url + 'settings',
				requester: new Requester()
			},
			sia: {
				url: 'https://api.coinmarketcap.com/v1/ticker/siacoin/?convert=',
				requester: new Requester()
			}
		},
		templates: {
			transaction: '<div class="transaction {{transaction-type}}" data-transaction-type="{{transaction-type}}"><div class="container"><div class="transaction-main"><div class="transaction-main-element transaction-icon-box"><i class="ic ic-arrow-down transaction-icon icon-received"></i><i class="ic ic-arrow-up transaction-icon icon-sent"></i><i class="ic ic-sync transaction-icon icon-exchanged"></i></div><div class="transaction-main-element transaction-title"><span class="title">{{transaction-type-capitalize}}</span><span class="amount-original">{{transaction-amount}} SC</span><span class="amount-conversion">({{transaction-amount-converted}}&nbsp;{{user-currency}})</span></div><div class="transaction-main-element transaction-status {{transaction-confirmed}}">{{transaction-confirmed-capitalize}}</div><div class="transaction-main-element transaction-date">{{transaction-date}}</div></div><div class="transaction-details"><p class="transaction-block-height">Block height: {{transaction-block-height}}</p><p class="transaction-id">Transaction ID: {{transaction-id}}</p></div></div></div>'
		},
		settings: null,
		wallet: {},
		transactions: {},
		events: {
			walletStatusLoaded: 'walletStatusLoaded',
			walletAddressLoaded: 'walletAddressLoaded'
		},
		siaPrice: null,
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
			},
			sendValidation: {
				notEnoughAmount: 'You don\'t have that many coins.'
			},
			transactions: {
				fail: 'Couldn\'t retrieve transactions history.'
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






	/* Calculates current sia price (given by coinmarketcap) */
	(function calcSiaPrice() {
		//start loader
		loadingManager.add('Sia price');
		//snowball
		userSettings();

		function userSettings() {
			CONFIG.api.settings.requester.setURL( CONFIG.api.settings.url );
			CONFIG.api.settings.requester.setMethod('GET');
			CONFIG.api.settings.requester.setCredentials(true);
			CONFIG.api.settings.requester.setCache(false);
			CONFIG.api.settings.requester.run(function(response) {
				CONFIG.settings = response;
				//hard coding EUR until Robert creates the setting on the backend
				CONFIG.settings.currency = 'EUR';
				siaPrice();
			}, function(error) {
				//nothing
			});
		}

		function siaPrice() {
			CONFIG.api.sia.requester.setURL( CONFIG.api.sia.url + CONFIG.settings.currency );
			CONFIG.api.sia.requester.setMethod('GET');
			CONFIG.api.sia.requester.setCache(false);
			CONFIG.api.sia.requester.run(function(response) {
				//storing data
				CONFIG.siaPrice = response[0]['price_' + CONFIG.settings.currency.toLowerCase()];
				//start loader
				loadingManager.remove('Sia price');
			}, function(error) {
				//start loader
				loadingManager.remove('Sia price');
			});
		}

	}());






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
			$('.available-amount').html( CONFIG.wallet.status.confirmedsiacoinbalance_sc );

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

				var notify = new Notify({ message: CONFIG.messages.walletAddressLoaded.fail });
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






	/* Amount Validation */
	/* This function checks that inserted amount is less or equal than available */
	function amountValidation( $amountInput ) {
		if ( $amountInput.val() > CONFIG.wallet.status.confirmedsiacoinbalance_sc ) {
			return false;
		} else {
			return true;
		}
	}






	/* Handles send function */
	(function Send() {

		//init vars
		var $sendButton = $('#send-button'),
			$amountToSend = $('#amount-to-send'),
			$addressToSend = $('#send-to-address');

		function collectData() {
			return {
				amount_sc: $amountToSend.val(),
				destination: $addressToSend.val()
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

				//saving details
				var details = response;

				//print notification
				var notify = new Notify({
					message: CONFIG.messages.walletSend.success,
					cancelText: 'Details',
					onCancel: function() {
						//adding response's message to notification message
						var message = details.message + '<br />';
						//adding transaction ids
						message += 'Transaction IDs:<br />';
						for ( var n = 0; n < details.transactionids.length; n++ ) {
							message += '<small>' + details.transactionids[n] + '</small><br />';
						}
						var notifyDetails = new Notify({message: message});
						notifyDetails.print();
					}
				});
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






	/* Handles user amount-to-send input */
	(function SendFormValidator() {

		//init vars
		var $amountInput = $('#send-tab .amount-to-send'),
			$addressInput = $('#send-tab .send-to-address'),
			$sendButton = $('#send-button');

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
				if ( amountValidation( $amountInput ) ) {
					printAmountFeedbackMessage( CONFIG.sendValidation.messages.notEnoughAmount );
				} else {
					hideAmountFeedback();
				}
				return amountValidation( $amountInput );
			}

				function printAmountFeedbackMessage( message ) {
					$('.amount-feedback:visible')
						.html( message )
						.fadeIn(60);
				}

				function hideAmountFeedback() {
					$('.amount-feedback:visible')
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






	/* Functions for shapshift form */
	(function shapeShiftFormFunctions() {
		(function destinationCoinListManager() {
			var $destinationCoinList = $('#destination-coin-list'),
				$unfoldListButton = $('#unfold-destination-coin-list'),
				$fakeInput = $('#shapeshift-tab .destination-coin-witness'),
				$destinationInput = $('#destination-coin'),
				$options = null;

			var destinationCoinListTemplate = '<span class="coin" data-symbol="{{coin_symbol}}"><div class="coin-icon-box"><img class="coin-icon" src="{{coin_image}}" /></div>{{coin_name}}</span>';

			function toggleListVisibility() {
				if ( $destinationCoinList.is(':visible') ) {
					hideList();
				} else {
					showList();
				}
			}

			function showList() {
				$destinationCoinList.fadeIn();
			}

			function hideList() {
				$destinationCoinList.fadeOut();
			}

			function setDestination( $clicked ) {
				$fakeInput.html( $clicked.html() );
				$destinationInput.val( $clicked.attr('data-symbol') );
				//trigger change event
				$destinationInput.trigger('change');

				hideList();
			}

			(function fillDestinationCoinList() {

				//start loading
				loadingManager.add('Accepted coins');

				shapeshift.get.supportedCoins(function(response) {

					var keys = objectKeys(response);
					var html = '';
					//iterating through coins object
					for ( var n = 0; n < objectLength(response); n++ ) {
						if ( response[keys[n]].status == 'available' && response[keys[n]].symbol != 'SC' ) {
							//if coin is available and is not sia (it doesn't make sense to shapeshift sia into sia...):
							html = destinationCoinListTemplate;
							html = replaceAll( html, '{{coin_symbol}}', response[keys[n]].symbol );
							html = replaceAll( html, '{{coin_image}}', response[keys[n]].image );
							html = replaceAll( html, '{{coin_name}}', response[keys[n]].name );
							$destinationCoinList.append( html );
						}
					}

					//stop loading
					loadingManager.remove('Accepted coins');
				}, function(error) {
					//stop loading
					loadingManager.remove('Accepted coins');
				});
			}());

			$unfoldListButton.on('click', toggleListVisibility);
			$fakeInput.on('click', toggleListVisibility);

			$('body').on('click', '#destination-coin-list .coin', function() {
				setDestination( $(this) );
			});
		}());

		(function shapeshiftSend() {
			var $sendButton = $('#shapeshift-process-button');

			function send() {
				//sending
			}

			$sendButton.on('click', send);
		}());

	}());






	/* Handles user input for shapshift form */
	(function shapeShiftFormValidator() {
		//checks that all the required fields are properly filled
		//init vars
		var $amountInput = $('#shapeshift-tab .amount-to-send'),
			$addressInput = $('#shapeshift-tab .send-to-address'),
			$addressVerificationWitness = $('#shapeshift-tab .address-verification-witness'),
			$targetCoinInput = $('#destination-coin'),
			$sendButton = $('#shapeshift-process-button');

		$amountInput.on('keyup', function() {
			validateShapeshiftForm();
		});

		$amountInput.on('change', function() {
			validateShapeshiftForm();
		});

		$amountInput.on('paste', function() {
			validateShapeshiftForm();
		});

		$addressInput.on('keyup', function() {
			console.log('keyup!');
			validateShapeshiftForm(validateDestinationAddress);
		});

		$addressInput.on('change', function() {
			console.log('chage!');
			validateShapeshiftForm(validateDestinationAddress);
		});

		$addressInput.on('paste', function() {
			console.log('paste!');
			setTimeout(function() {
				console.log('timeout!');
				$addressInput.trigger('keyup');
			}, 150);
		});

		$targetCoinInput.on('change', function() {
			validateShapeshiftForm(validateDestinationAddress);
		});

		function validateShapeshiftForm(callbackFunc) {
			//cleaning address validation witness
			$addressVerificationWitness.html('');

			//validate inputs are not empty
			if ( !$amountInput.val().length ) {
				//if amount input is empty
				disableSendButton();
				return false; //cut execution
			}

			if ( !$targetCoinInput.val().length ) {
				//if target coin input is empty
				disableSendButton();
				return false; //cut execution
			}

			if ( !$addressInput.val().length ) {
				console.log('address input is empty mozo');
				//if address input is empty
				disableSendButton();
				return false; //cut execution
			}

			//exec callback if any
			if ( callbackFunc ) {
				callbackFunc();
			}
		}

		function validateDestinationAddress() {
			var address = $addressInput.val(),
				symbol = $targetCoinInput.val();

			$addressVerificationWitness.html('Validating address...');

			shapeshift.validateAddress(address, symbol, function(response) {
				if ( response.isvalid ) {
					$addressVerificationWitness.html('Address is valid :)');
					//enable send button
					enableSendButton();
				} else {
					$addressVerificationWitness.html( response.error );
					//disable send button
					disableSendButton();
				}
			}, function(error) {
				$addressVerificationWitness.html( 'Error validating address...' );
				//disable send button
				disableSendButton();
			});
			}

			function checkIfEnoughAmount() {
				if ( amountValidation( $amountInput ) ) {
					printAmountFeedbackMessage( CONFIG.sendValidation.messages.notEnoughAmount );
				} else {
					hideAmountFeedback();
				}
				return amountValidation( $amountInput );
			}

				function printAmountFeedbackMessage( message ) {
					$('#shapeshift-tab .amount-feedback')
						.html( message )
						.fadeIn(60);
				}

				function hideAmountFeedback() {
					$('#shapeshift-tab .amount-feedback')
						.fadeOut(60, function() {
							$(this).html(''); //emptying as a callback
						});
				}

			function isTargetCoinSelected() {
				if ( $targetCoinInput.val() ) {
					return true;
				} else {
					return false;
				}
			}


			function disableSendButton() {
				$sendButton.attr('disabled', 'disabled');
			}

			function enableSendButton() {
				$sendButton.removeAttr('disabled');
			}

		//execute validation on document ready
		$(document).ready(validateShapeshiftForm);

	}());






	/* Copies wallet address to clipboard */
	/* It requires jquery.copyToClipboard.js */
	(function walletAddressCopyOnClick() {

		var $userWalletAddress = $('.user-wallet-address');

		$userWalletAddress.on('click', function() {
			copyToClipboard( $(this), function() {
				//as a callback, print a message
				$('.user-wallet-address-feedback:visible').fadeIn(100, function() {
					$(this).delay(1000).fadeOut(1000);
				});
			});
		});

	}());






	/* Handles webcam QR code reader */
	/* Requires instascan.min.js & instascan.js (InstascanManager()) */
	(function WebcamReaderManager() {

		var $webcamButton = $('.scan-address-button'),
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
			$('.send-to-address:visible').val( address );
			$('.amount-to-send:visible')
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
			$qrCodeShowButton = $('.show-qr-code-button'),
			$qrCodeCloseButton = $('#hide-qr-code-button');

		var $addressInput = $('.user-wallet-address'),
			$amountInput = $('.amount-to-receive');

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






	/* Transaction history manager */
	(function transactionHistoryManager() {

		var $transactionsBox = $('#transaction-history');

		getTransactions();

		function getTransactions() {
			//start loader
			loadingManager.add('Wallet transactions history');
			CONFIG.api.transactions.requester.setURL( CONFIG.api.transactions.url );
			CONFIG.api.transactions.requester.setMethod( 'GET' );
			CONFIG.api.transactions.requester.setCache(false);
			CONFIG.api.transactions.requester.setCredentials(true);
			CONFIG.api.transactions.requester.run(function(response) {
				//storing data
				CONFIG.transactions = response;
				fill(response);
				//remove loader
				loadingManager.remove('Wallet transactions history');
			}, function(error) {
				//remove loader
				loadingManager.remove('Wallet transactions history');
				//print error
				var n = new Notify({message: CONFIG.messages.transactions.fail});
				n.print();
			});
		}

		function fill( transactions ) {
			var html = null,
				data = {};
			for ( var n = 0; n < transactions.length; n++ ) {
				//clean copy of the template
				html = CONFIG.templates.transaction;
				//setting data
					//setting transaction_type
					if ( transactions[n].change < 0 ) {
						data.transaction_type = 'sent';
						//converting change_sc value to positive
						transactions[n].change_sc = transactions[n].change_sc * -1;
					} else {
						data.transaction_type = 'received';
					}
					//setting transaction_amount_converted
					data.transaction_amount_converted = (CONFIG.siaPrice * transactions[n].change_sc).toFixed(2);
					//setting transaction date
					data.transaction_date = formatDate( new Date( parseInt(transactions[n].timestamp) * 1000 ), 'HH:mm dd/MM/yyyy' );
					//setting transaction confirmed
					if ( transactions[n].confirmed ) {
						data.transaction_confirmed = 'confirmed';
					} else {
						data.transaction_confirmed = 'unconfirmed';
					}

				html = replaceAll( html, '{{transaction-type}}', data.transaction_type);
				html = replaceAll( html, '{{transaction-type-capitalize}}', capitalizeFirstLetter( data.transaction_type) );
				html = replaceAll( html, '{{transaction-amount}}', transactions[n].change_sc );
				html = replaceAll( html, '{{transaction-amount-converted}}', data.transaction_amount_converted);
				html = replaceAll( html, '{{user-currency}}', CONFIG.settings.currency);
				html = replaceAll( html, '{{transaction-confirmed}}', data.transaction_confirmed );
				html = replaceAll( html, '{{transaction-confirmed-capitalize}}', capitalizeFirstLetter( data.transaction_confirmed ) );
				html = replaceAll( html, '{{transaction-date}}', data.transaction_date);
				html = replaceAll( html, '{{transaction-block-height}}', transactions[n].height);
				html = replaceAll( html, '{{transaction-id}}', transactions[n].transactionid);

				$transactionsBox.prepend(html);
			}
		}


		function toggleTransactionVisibility( $transaction ) {
			if ( $transaction.find('.transaction-details').is(':visible') ) {
				hideDetails( $transaction.find('.transaction-details') );
			} else {
				showDetails( $transaction.find('.transaction-details') );
			}
		}
			function showDetails( $element ) {
				$element.fadeIn(300);
			}

			function hideDetails( $element ) {
				$element.fadeOut(100);
			}


		$('body').on('click', '#transaction-history .transaction-main', function() {
			toggleTransactionVisibility( $(this).parents('.transaction') );
		});

	}());






	$(document).ready(function() {
		walletStatusManager.load();
	});






	$('#receive-tab').bind('show', function() {
		walletAddressManager.load();
	});


}