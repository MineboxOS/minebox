/* jsing dashboard */
viewLoader.add('wallet', Wallet);

function Wallet() {


	/* Handle funds load, format and printing */
	(function FundsManager() {

		var availableFunds = null;

		function loadFunds() {
			//calls to the server and gets the funds
			//$.ajax...
				//faking received number
				var funds = 8486435843.5468456843;
				//on callback;
					//assigning funds to variable
					availableFunds = funds;
					//printing results
					printFunds();
					//maybe rising an event?
		}

		function formatFunds(number) {
			//format number
			return number;
		}

		function printFunds() {
			//printing wallet balance
			$('#wallet-balance').html( formatFunds( availableFunds ) );
			//printing you have available:
			$('#available-amount').html( availableFunds );
		}


		$(document).ready(function() {
			loadFunds();
		});

	})();





	/* Handles tabs loading */
	(function WalletTabsHandler() {

		//init vars
		var $tabs = $('#wallet-tabs .wallet-tab'),
			$buttons = $('#wallet-tabs-buttons .button'),
			speed = 300,
			activeClass = 'active';

		//show tab function
		function showTab( targetID ) {
			hideTabs();
			$('#' + targetID)
				.addClass( activeClass )
				.fadeIn( speed );
		}

		//hide all tabs function
		function hideTabs() {
			$tabs
				.fadeOut( speed )
				.removeClass( activeClass );
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

	})();








}