/* jsing dashboard */
viewLoader.add('dashboard', Dashboard);


// requires
	//requester.js
	//loadingManager.js
	//replaceAll();
	//getRandomString();
	//getRandomInt();
function Dashboard() {
  

	var CONFIG = {
		api: {
			mineboxStatus: {
				url: config.mug.url + 'status',
				requester: new Requester()
			},
			consensusStatus: {
				url: config.mug.url + 'consensus',
				requester: new Requester()
			},
			siaStatus: {
				url: config.mug.url + 'sia/status',
				requester: new Requester()
			}
		},
		events: {
			mineboxStatus: {
				updated: 'mineboxStatusUpdated',
				failed: 'mineboxStatusFailed'
			},
			networkStatus: {
				updated: 'networkStatusUpdated',
				failed: 'networkStatusFailed'
			},
			siaStatus: {
				updated: 'siaStatusUpdated',
				failed: 'siaStatusFailed'
			}
		},
		LEDColors: {
			loading: 'loading',
			good: 'good',
			bad: 'bad',
			check: 'check'
		},
		loop: {
			func: null, //setInterval handler
			time: 3000 //the time for interval
		},
		errorHandler: {
			template: '<div style="display:none;" id="{{id}}" class="{{type}} dashboard-notification"><div class="icon-box"><i class="ic ic-cross"></i><i class="ic ic-checkmark"></i><i class="ic ic-warning"></i></div><div class="notification-content"><h3 class="notification-title" style="display:{{title-visibility}}">{{title}}</h3><p class="notification-text">{{message}}</p></div></div>',
			fadeSpeed: 300,
			timeout: 3000
		},
		messages: {
			mineboxStatusFailed: 'Minebox Status not updated. Re-trying in several seconds.',
			consensusStatusFailed: 'Consensus Status not updated. Re-trying in several seconds.',
			siaStatusFailed: 'Sia Status not updated. Re-trying in several seconds.',
			mineBDFailed: 'Looks like Minebox is not running well. Try rebooting your system. If the problem persist, please reach us at minebox.io/support.',
			consensusNotSynced: 'Minebox is currently syncing with consensus. This operation may take up to several hours depending on your Internet connection.',
			walletLockedOrNotEncrypted: 'Minebox wallet is not ready now. If this state remains for more than a few hours, please reach us for support at minebox.io/support'
		}
	};

	//global object for last updated status
	var STATUS = {
		mineboxStatus: {},
		consensusStatus: {},
		siaStatus: {}
	};



	//witness elements
	var $minebd_running_witness = $('#minebd_running_witness'),
		$minebd_encrypted_witness = $('#minebd_encrypted_witness'),
		$minebd_storage_mounted_witness = $('#minebd_storage_mounted_witness'),
		$consensus_synced_witness = $('#consensus_synced_witness'),
		$consensus_height_witness = $('#consensus_height_witness'),
		$contracts_witness = $('#contracts_witness'),
		$wallet_unlocked_witness = $('#wallet_unlocked_witness'),
		$wallet_encrypted_witness = $('#wallet_encrypted_witness'),
		$wallet_balance_witness = $('#wallet_balance_witness'),
		$wallet_unconfirmed_balance_witness = $('#wallet_unconfirmed_balance_witness');


	//LED elements
	var $mineboxStatusLED = $('#status-widget .widget-led-status'),
		$networkStatusLED = $('#network-widget .widget-led-status'),
		$walletStatusLED = $('#wallet-widget .widget-led-status');




	//getters
	function getMineboxStatus() {
		//requesting status/
		CONFIG.api.mineboxStatus.requester.setURL( CONFIG.api.mineboxStatus.url );
		CONFIG.api.mineboxStatus.requester.setCache(false);
		CONFIG.api.mineboxStatus.requester.setCredentials(true);
		CONFIG.api.mineboxStatus.requester.run(function(response) {

			//update global object
			STATUS.mineboxStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.mineboxStatus.updated);

		}, function(error) {

			var data = {
				type: 'error',
				message: CONFIG.messages.mineboxStatusFailed
			};

			//display error
			dashboardErrorHandler.print(data);
			//update global object (with empty data)
			STATUS.mineboxStatus = {};
			//rise event
			$('body').trigger(CONFIG.events.mineboxStatus.failed);

		});
	}





	function getConsensusStatus() {
		//requesting consensus/
		CONFIG.api.consensusStatus.requester.setURL( CONFIG.api.consensusStatus.url );
		CONFIG.api.consensusStatus.requester.setCache(false);
		CONFIG.api.consensusStatus.requester.setCredentials(true);
		CONFIG.api.consensusStatus.requester.run(function(response) {

			//update global object
			STATUS.consensusStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.networkStatus.updated);

		}, function(error) {

			var data = {
				type: 'error',
				message: CONFIG.messages.consensusStatusFailed
			};

			//display error
			dashboardErrorHandler.print(data);
			//update global object (with empty data)
			STATUS.consensusStatus = {};
			//rise event
			$('body').trigger(CONFIG.events.networkStatus.failed);

		});
	}





	function getSiaStatus() {
		//requesting sia/status/
		CONFIG.api.siaStatus.requester.setURL( CONFIG.api.siaStatus.url );
		CONFIG.api.siaStatus.requester.setCache(false);
		CONFIG.api.siaStatus.requester.setCredentials(true);
		CONFIG.api.siaStatus.requester.run(function(response) {

			//update global object
			STATUS.siaStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.siaStatus.updated);

		}, function(error) {

			var data = {
				type: 'error',
				message: CONFIG.messages.siaStatusFailed
			};

			//display error
			dashboardErrorHandler.print(data);
			//update global object (with empty data)
			STATUS.siaStatus = {};
			//rise event
			$('body').trigger(CONFIG.events.siaStatus.failed);

		});
	}







	//fillers
	//those functions will be executed on events
	function fillMineboxStatus() {
		//fill in all the fields relative to ajax call: getMineboxStatus()
		$minebd_running_witness.html( STATUS.mineboxStatus.minebd_running );
		$minebd_encrypted_witness.html( STATUS.mineboxStatus.minebd_encrypted );
		$minebd_storage_mounted_witness.html( STATUS.mineboxStatus.minebd_storage_mounted );
	}




	function fillConsensusStatus() {
		//fill in all the fields relative to ajax call: getConsensusStatus()
		$consensus_synced_witness.html( STATUS.consensusStatus.synced );
		$consensus_height_witness.html( STATUS.consensusStatus.height );
	}




	function fillSiaStatus() {
		//fill in all the fields relative to ajax call: getSiaStatus()
		$contracts_witness.html( STATUS.siaStatus.renting.contracts );
		$wallet_unlocked_witness.html( STATUS.siaStatus.wallet.unlocked );
		$wallet_encrypted_witness.html( STATUS.siaStatus.wallet.encrypted );
		$wallet_balance_witness.html( STATUS.siaStatus.wallet.confirmed_balance_sc + ' SC' );
		$wallet_unconfirmed_balance_witness.html( STATUS.siaStatus.wallet.unconfirmed_delta_sc + ' SC' );
	}







	//those functions indicate the LED color for each of the widgets
	function mineboxLEDColor() {
		if ( !$.isEmptyObject( STATUS.mineboxStatus ) ) {
			//there is data within mineboxStatus

			if ( !STATUS.mineboxStatus.minebd_running || !STATUS.mineboxStatus.sia_daemon_running ) {
				//A reboot may help, but otherwise, Minebox support is needed.
				//changing LED color to red
				$mineboxStatusLED.attr('data-led', CONFIG.LEDColors.bad);
				//print error
				var data = {
					type: 'error',
					message: CONFIG.messages.mineBDFailed
				};
				dashboardErrorHandler.print(data);

			} else {
				//changing LED color to green
				$mineboxStatusLED.attr('data-led', CONFIG.LEDColors.good);

			}

		} else {
			//change to loading
			$mineboxStatusLED.attr('data-led', CONFIG.LEDColors.loading);
		}
	}



	function networkLEDColor() {
		if ( !$.isEmptyObject( STATUS.consensusStatus ) && !$.isEmptyObject( STATUS.siaStatus ) ) {
			//there is data within consensusStatus

			if ( !STATUS.consensusStatus.synced ) {
				//changing LED color to YELLOW
				$networkStatusLED.attr('data-led', CONFIG.LEDColors.check);
				//print error
				var data = {
					type: 'warning',
					message: CONFIG.messages.consensusNotSynced
				};
				dashboardErrorHandler.print(data);
			} else {
				//changing LED color to green
				$networkStatusLED.attr('data-led', CONFIG.LEDColors.good);

			}

		} else {
			//change to loading
			$networkStatusLED.attr('data-led', CONFIG.LEDColors.loading);
		}
	}




	function siaLEDColor() {

		if ( !$.isEmptyObject( STATUS.siaStatus ) ) {
			//there is data within siaStatus

			if ( !STATUS.siaStatus.wallet.unlocked || !STATUS.siaStatus.wallet.encrypted ) {
				//change to YELLOW
				$walletStatusLED.attr('data-led', CONFIG.LEDColors.check);
				//print error
				var data = {
					type: 'warning',
					message: CONFIG.messages.walletLockedOrNotEncrypted
				};
				dashboardErrorHandler.print(data);


			} else {
				//change to good
				$walletStatusLED.attr('data-led', CONFIG.LEDColors.good);



			}

		} else {
			//change to loading
			$walletStatusLED.attr('data-led', CONFIG.LEDColors.loading);
		}
	}







	//events that trigger the functions above
	$('body').bind( CONFIG.events.mineboxStatus.updated, function() {
		fillMineboxStatus();
		mineboxLEDColor();
	});
	$('body').bind( CONFIG.events.networkStatus.updated, function() {
		fillConsensusStatus();
		networkLEDColor();
	});
	$('body').bind( CONFIG.events.siaStatus.updated, function() {
		fillSiaStatus();
		siaLEDColor();
		networkLEDColor(); //LED color also depends on siaStatus object because of contracts number
	});





	//error handler
	function DashboardErrorHandler( cfg ) {

		var $dashboardNotificationBox = $('#dashboard-notification-box');
		var CONFIG = {
			template: '<div style="display:none;" id="{{id}}" class="{{type}} dashboard-notification"><div class="icon-box"><i class="ic ic-cross"></i><i class="ic ic-checkmark"></i><i class="ic ic-warning"></i></div><div class="notification-content"><h3 class="notification-title" style="display:{{title-visibility}}">{{title}}</h3><p class="notification-text">{{message}}</p></div></div>',
			fadeSpeed: 300,
			timeout: 3000
		};
		//overriding CONFIG
		$.extend( CONFIG, cfg, true );


		function print( data ) {
			/*
				data: {
					type: 'error', //ERROR || NOTIFICATION || WARNING //required
					title: '', //STRING containing title to display //optional
					message: '' //STRING containing message to display //required
				}
			*/

			if ( !data.type || !data.message ) {
				return false;
			}

			var randomID = getRandomString(10);
			var print = CONFIG.template;
			//printing id
			print = replaceAll( print, '{{id}}', randomID );
			//printing type
			print = replaceAll( print, '{{type}}', data.type );
			//printing message
			print = replaceAll( print, '{{message}}', data.message );
			//printing title if any
			if ( print.title && print.title != '' ) {
				print = replaceAll( print, '{{title-visibility}}', 'block' );
				print = replaceAll( print, '{{title}}', data.title );
			} else {
				print = replaceAll( print, '{{title-visibility}}', 'none' );
			}
			
			//appending it to $dashboardNotificationBox
			$dashboardNotificationBox.append( print );

			//fading it in
			$('#' + randomID).fadeIn( CONFIG.fadeSpeed );

			//set Timeout to auto-hide
			setTimeout(function() {
				close( $('#' + randomID) );
			}, CONFIG.timeout);
		}


		function close( $element ) {
			$element.fadeOut( CONFIG.fadeSpeed, function() {
				$element.remove();
			})
		}


		$dashboardNotificationBox.on('click', '.dashboard-notification', function() {
			close( $(this) );
		});

		return {
			print: print
		}

	}
	var dashboardErrorHandler = DashboardErrorHandler( CONFIG.errorHandler );





	//dashboard loop
	function loop() {
		//this function is being executed every CONFIG.loop.timeout and if all calls are 
		getMineboxStatus();
		getConsensusStatus();
		getSiaStatus();
	}


	//setting up interval and executing the loop for first time
	/*CONFIG.loop.func = setInterval(function() {
		loop();
	}, CONFIG.loop.time);*/
	//first time
	loop();

}