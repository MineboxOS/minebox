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
			},
			backupStatus: {
				url: config.mug.url + 'backup/latest/status',
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
			},
			backupStatus: {
				updated: 'backupStatusUpdated',
				failed: 'backupStatusFailed'
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
			time: 10000 //the time for interval
		},
		errorHandler: {
			template: '<div style="display:none;" id="{{id}}" class="{{type}} dashboard-notification"><div class="icon-box"><i class="ic ic-cross"></i><i class="ic ic-checkmark"></i><i class="ic ic-warning"></i></div><div class="notification-content"><h3 class="notification-title" style="display:{{title-visibility}}">{{title}}</h3><p class="notification-text">{{message}}</p></div></div>',
			fadeSpeed: 300,
			timeout: 5000
		},
		messages: {
			mineboxStatusFailed: 'Minebox Status not updated or request timedout. Retrying in several seconds.',
			consensusStatusFailed: 'Consensus Status not updated or request timedout. Retrying in several seconds.',
			siaStatusFailed: 'Sia Status not updated or request timedout. Retrying in several seconds.',
			mineBDFailed: 'Looks like Minebox is not running well. Try rebooting your system. If the problem persist, please reach us at minebox.io/support.',
			consensusNotSynced: 'Minebox is currently syncing with consensus. This operation may take up to several hours depending on your Internet connection.',
			walletLockedOrNotEncrypted: 'Minebox wallet is not ready now. If this state remains for more than a few hours, please reach us for support at minebox.io/support',
			backupStatusFailed: 'Latest backup not updated or request timedout. Retrying in several seconds.',
			backupPending: 'You backup will start uploading soon. Please be patient.',
			backupDamaged: 'Last backup seems to be damaged. Proceed ------------------------- ',
			backupError: 'There\'s an error with your last backup. Proceed--------------------',
			backupUnknown: 'We\'re getting an unknown status for your backup. Retrying in several seconds.'
		}
	};

	//global object for last updated status
	var STATUS = {
		mineboxStatus: {},
		consensusStatus: {},
		siaStatus: {},
		backupStatus: {}
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
		$wallet_unconfirmed_balance_witness = $('#wallet_unconfirmed_balance_witness'),
		$backup_widget = $('#backup-widget'),
		$backup_name = $('#backup_name'),
		$backup_progress_bar = $('#backup-widget .widget-progress-bar .fill'),
		$backup_progress_bar_value = $('#backup-widget .widget-progress-bar .percent-witness .value'),
		$backup_timestamp_witness = $('#backup_timestamp_witness'),
		$backup_status_witness = $('#backup_status_witness'),
		$backup_metadata_witness = $('#backup_metadata_witness'),
		$backup_files_number_witness = $('#backup_files_number_witness'),
		$backup_size_witness = $('#backup_size_witness'),
		$backup_relative_size_witness = $('#backup_relative_size_witness'),
		$backup_progress_witness = $('#backup_progress_witness'),
		$backup_relative_progress_witness = $('#backup_relative_progress_witness');



	//LED elements
	var $mineboxStatusLED = $('#status-widget .widget-led-status'),
		$networkStatusLED = $('#network-widget .widget-led-status'),
		$walletStatusLED = $('#wallet-widget .widget-led-status'),
		$backupStatusLED = $('#backup-widget .widget-led-status');




	//getters
	function getMineboxStatus() {
		//adding witness to loop.activeRequests
		loop.addActiveRequest('minebox');

		//requesting status/
		CONFIG.api.mineboxStatus.requester.setURL( CONFIG.api.mineboxStatus.url );
		CONFIG.api.mineboxStatus.requester.setCache(false);
		CONFIG.api.mineboxStatus.requester.setCredentials(true);
		CONFIG.api.mineboxStatus.requester.setTimeoutFunc(function() {
			//changing led color to loading
			mineboxLEDColor( CONFIG.LEDColors.loading );
			//not printing any error because time expiration executes also FAIL function
		});
		CONFIG.api.mineboxStatus.requester.run(function(response) {

			//update global object
			STATUS.mineboxStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.mineboxStatus.updated);
			//remove witness from loop
			loop.removeActiveRequest('minebox');

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
			//remove witness from loop
			loop.removeActiveRequest('minebox');

		});
	}





	function getConsensusStatus() {
		//adding witness to loop.activeRequests
		loop.addActiveRequest('consensus');

		//requesting consensus/
		CONFIG.api.consensusStatus.requester.setURL( CONFIG.api.consensusStatus.url );
		CONFIG.api.consensusStatus.requester.setCache(false);
		CONFIG.api.consensusStatus.requester.setCredentials(true);
		CONFIG.api.consensusStatus.requester.setTimeoutFunc(function() {
			//changing led color to loading
			networkLEDColor( CONFIG.LEDColors.loading );
			//not printing any error because time expiration executes also FAIL function
		});
		CONFIG.api.consensusStatus.requester.run(function(response) {

			//update global object
			STATUS.consensusStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.networkStatus.updated);
			//remove witness from loop
			loop.removeActiveRequest('consensus');

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
			//remove witness from loop
			loop.removeActiveRequest('consensus');

		});
	}





	function getSiaStatus() {
		//adding witness to loop.activeRequests
		loop.addActiveRequest('sia');

		//requesting sia/status/
		CONFIG.api.siaStatus.requester.setURL( CONFIG.api.siaStatus.url );
		CONFIG.api.siaStatus.requester.setCache(false);
		CONFIG.api.siaStatus.requester.setCredentials(true);
		CONFIG.api.siaStatus.requester.setTimeoutFunc(function() {
			//changing led color to loading
			mineboxLEDColor( CONFIG.LEDColors.loading );
			//not printing any error because time expiration executes also FAIL function
		});
		CONFIG.api.siaStatus.requester.run(function(response) {

			//update global object
			STATUS.siaStatus = response;
			//rise event
			$('body').trigger(CONFIG.events.siaStatus.updated);
			//remove witness from loop
			loop.removeActiveRequest('sia');

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
			//remove witness from loop
			loop.removeActiveRequest('sia');

		});
	}





	function getBackupStatus() {
		//adding witness to loop.activeRequests
		loop.addActiveRequest('backup');

		//requesting backup/latest/status/
		CONFIG.api.backupStatus.requester.setURL( CONFIG.api.backupStatus.url );
		CONFIG.api.backupStatus.requester.setMethod( 'GET' );
		CONFIG.api.backupStatus.requester.setCache(false);
		CONFIG.api.backupStatus.requester.setCredentials(true);
		CONFIG.api.backupStatus.requester.setTimeoutFunc(function() {
			//changing led color to loading
			backupLEDColor( CONFIG.LEDColors.loading );
			//not printing any error because time expiration executes also FAIL function
		});
		CONFIG.api.backupStatus.requester.run(function(response) {

			//update global object
			STATUS.backupStatus = response;
			/*
			//use the following object to test different responses
			STATUS.backupStatus = {
			  metadata: "PENDING", 
			  name: "1503494102", 
			  numFiles: 200, 
			  progress: 60.77108501803187, 
			  relative_progress: 50.0, 
			  relative_size: 6733983744, 
			  size: 8264986624, 
			  status: "PENDING", 
			  time_snapshot: 1503494102
			}*/
			//rise event
			$('body').trigger(CONFIG.events.backupStatus.updated);
			//remove witness from loop
			loop.removeActiveRequest('backup');

		}, function(error) {

			var data = {
				type: 'error',
				message: CONFIG.messages.backupStatusFailed
			};

			//display error
			dashboardErrorHandler.print(data);
			//update global object (with empty data)
			STATUS.backupStatus = {};
			//rise event
			$('body').trigger(CONFIG.events.backupStatus.failed);
			//remove witness from loop
			loop.removeActiveRequest('backup');

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




	function fillBackupStatus() {
		//fill in all the fields relative to ajax call: getBackupStatus()
		$backup_name.html( STATUS.backupStatus.name );
		$backup_progress_bar.width( STATUS.backupStatus.progress.toFixed(2) + '%');
		$backup_progress_bar_value.html( STATUS.backupStatus.progress.toFixed(2) );
		$backup_timestamp_witness.html( STATUS.backupStatus.time_snapshot );
		$backup_status_witness.html( STATUS.backupStatus.status );
		$backup_metadata_witness.html( STATUS.backupStatus.metadata );
		$backup_files_number_witness.html( STATUS.backupStatus.numFiles );
		$backup_size_witness.html( STATUS.backupStatus.size );
		$backup_relative_size_witness.html( STATUS.backupStatus.relative_size );
		$backup_progress_witness.html( STATUS.backupStatus.progress.toFixed(2) + '%' );
		$backup_relative_progress_witness.html( STATUS.backupStatus.relative_progress.toFixed(2) + '%' );
	}







	//those functions indicate the LED color for each of the widgets
	function mineboxLEDColor( LEDColor ) {
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

		if ( LEDColor ) {
			$mineboxStatusLED.attr('data-led', LEDColor);
		}
	}



	function networkLEDColor( LEDColor ) {
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

		if ( LEDColor ) {
			$networkStatusLED.attr('data-led', LEDColor);
		}
	}




	function siaLEDColor( LEDColor ) {

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

		if ( LEDColor ) {
			$siaStatusLED.attr('data-led', LEDColor);
		}
	}




	function backupLEDColor( LEDColor ) {

		if ( !$.isEmptyObject( STATUS.backupStatus ) ) {
			//there is data within backupStatus

			if ( STATUS.backupStatus.status == 'FINISHED' ) {
				//change to green
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.good);


			} else if ( STATUS.backupStatus.status == 'UPLOADING' ) {
				//change to good
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.good);


			} else if ( STATUS.backupStatus.status == 'PENDING' ) {
				//change to yellow
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.check);
				//print error
				var data = {
					type: 'notification',
					message: CONFIG.messages.backupPending
				};
				dashboardErrorHandler.print(data);

			} else if ( STATUS.backupStatus.status == 'DAMAGED' ) {
				//change to yellow
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.check);
				//print error
				var data = {
					type: 'warning',
					message: CONFIG.messages.backupDamaged
				};
				dashboardErrorHandler.print(data);

			} else if ( STATUS.backupStatus.status == 'ERROR' ) {
				//change to red
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.bad);
				//print error
				var data = {
					type: 'error',
					message: CONFIG.messages.backupError
				};
				dashboardErrorHandler.print(data);

			} else {
				//change to loading
				$backupStatusLED.attr('data-led', CONFIG.LEDColors.loading);
				//print error
				var data = {
					type: 'warning',
					message: CONFIG.messages.backupUnknown
				};
				dashboardErrorHandler.print(data);
			}

		} else {
			//change to loading
			$backupStatusLED.attr('data-led', CONFIG.LEDColors.loading);
		}

		if ( LEDColor ) {
			$backupStatusLED.attr('data-led', LEDColor);
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
	$('body').bind( CONFIG.events.backupStatus.updated, function() {
		handleBackupStatusWidget();
		fillBackupStatus();
		backupLEDColor();
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





	//handle backup status widget
	function handleBackupStatusWidget() {
		//this function will be executed everytime backupStatus is updated (listening to event CONFIG.events.backupStatus.updated)

		//show or hide backup contents
		if ( STATUS.backupStatus.status != 'FINISHED' ) {
			//backup is not finished, so showing progress-tab
			$backup_widget.find('.done-tab').fadeOut(100, function() {
				$backup_widget.find('.progress-tab').fadeIn(300);
			});
		} else {
			//backup is finished, so showing done-tab
			$backup_widget.find('.progress-tab').fadeOut(100, function() {
				$backup_widget.find('.done-tab').fadeIn(300);
			});
		}
	}





	//dashboard loop
	function Loop() {

		var activeRequests = [];

		function theLoop() {
			//this function is being executed every CONFIG.loop.timeout and if all calls are 
			getMineboxStatus();
			getConsensusStatus();
			getSiaStatus();
			getBackupStatus();
		}

		function addActiveRequest( name ) {
			if ( activeRequests.indexOf(name) < 0 ) {
				//if no active requests matches this name, add it
				activeRequests.push(name);
				return true;
			} else {
				//current name already exists into array
				return false;
			}
		}

		function removeActiveRequest( name ) {
			if ( activeRequests.indexOf(name) < 0 ) {
				//no active requests matching this name
				return false;
			} else {
				//current name exist in active requests
				var index = activeRequests.indexOf(name);
				//removing array's matching index
				activeRequests.splice(index, 1);
				return true;
			}
		}

		function getActiveRequests() {
			return activeRequests.length;
		}

		function init() {
			//exec loop for first time
			theLoop();
			//init the loop and asigning it to global CONFIG var
			CONFIG.loop.func = setInterval(function() {
				//execting theLoop() on each iteration only if activeRequests is empty
				if ( !getActiveRequests() ) {
					theLoop();
				}
			}, CONFIG.loop.time);
		}

		return {
			init: init,
			addActiveRequest: addActiveRequest,
			removeActiveRequest: removeActiveRequest
		}

	}
	var loop = Loop();

	$(document).ready(function() {
		loop.init();
	});

}