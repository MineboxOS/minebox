viewLoader.add('settings', Settings);

function Settings() {

	var CONFIG = {
		api: {
			settings_get: {
				requester: new Requester(),
				url: config.mug.url + 'settings'
			},
			settings_set: {
				requester: new Requester(),
				url: config.mug.url + 'settings'
			},
		},
		messages: {
			loadSettings: {
				fail: 'We couldn\t retrieve your settings. Try again in a few minutes.'
			},
			sendSettings: {
				fail: 'Settings failed to be saved. Try again in a few minutes.',
				success: 'Your settings were saved and applied successfully.'
			}
		},
		settings: []
	};

	//init vars
	var $bandwidthBox = $('.bandwidth-box'),
		$uploadLimit = $('#sia-upload-limit'),
		$currency = $('#currency'),
//		$downloadLimit = $('#download-limit'),
//		$notificationsBox = $('.notifications-box'),
//		$receiveNotifications = $('#receive-notifications'),
//		$notificationsEmail = $('#user-email'),
		$saveSettingsButton = $('#save-settings');

	var loadingWitness = false; //hard coded


	//handle settings read/write/load/save
	(function handleSettings() {
		//check settings in mug
		function loadSettings(cb) {

			//init loading witness
			loadingManager.add('Settings');

			//calls to the server and gets the settings
			CONFIG.api.settings_get.requester.setURL( CONFIG.api.settings_get.url );
			CONFIG.api.settings_get.requester.setMethod('GET');
			CONFIG.api.settings_get.requester.setCredentials(true);
			CONFIG.api.settings_get.requester.setCache(false);
			CONFIG.api.settings_get.requester.run(function(response) {

				//storing data
				CONFIG.settings = response;

				if (cb) {
					cb(CONFIG.settings);
				}

				//removing loader
				loadingManager.remove('Settings');
			}, function(error) {

				//removing loader
				loadingManager.remove('Settings');

				var notify = new Notify({ message: CONFIG.messages.loadSettings.fail });
				notify.print();
			});
		}


		//send settings to mug
		function sendSettings(settings, cb) {
			//init loading witness
			loadingWitness = true; //hard coded

			//start loading
			loadingManager.add('Saving settings');

			//sending
			CONFIG.api.settings_set.requester.setURL( CONFIG.api.settings_set.url );
			CONFIG.api.settings_set.requester.setMethod( 'POST' );
			CONFIG.api.settings_set.requester.setCache( false );
			CONFIG.api.settings_set.requester.setCredentials( true );
			CONFIG.api.settings_set.requester.setData( settings );
			CONFIG.api.settings_set.requester.run(function(response) {

				//stop loading
				loadingManager.remove('Saving settings');

				//re-load settings
				loadSettings(writeSettings);

				//saving details
				var details = response;

				//print notification
				var notify = new Notify({message: CONFIG.messages.sendSettings.success});
				notify.print();

				//end loading witness
				loadingWitness = false; //hard coded

				if ( cb ) {
					cb(true); //true as if settings were saved properly
				}

			}, function(error) {

				//stop loading
				loadingManager.remove('Saving settings');

				//print notification
				var notify = new Notify({message: CONFIG.messages.sendSettings.fail});
				notify.print();

			});

		}


		//given a settings object, put them on the specific inputs
		function writeSettings(settings) {
/*
			//notifications
			$receiveNotifications
				.prop('checked', settings.notifications.enabled)
				.trigger('change');
			if ( settings.notifications.enabled ) {
				$notificationsEmail.val( settings.notifications.email );
			}
*/
			//minebox bandwidth limit
			$uploadLimit.val( settings.sia_upload_limit_kbps );
			$currency.val( settings.currency );
			//$downloadLimit.val( settings.limit.download );

		}


		//read inputs and return settings object
		function readSettings() {
			var settings = {};
/*
			settings.notifications = {
				enabled: $receiveNotifications.is(':checked'),
				email: $notificationsEmail.val()
			};
*/
			settings.sia_upload_limit_kbps = $uploadLimit.val();
			settings.currency = $currency.val();
			//download: $downloadLimit.val()

			return settings;
		}


		function saveSettings() {
			//is loading witness active?

			if ( !loadingWitness ) {
				var settings = readSettings();
				sendSettings(settings, function(response) {
					console.log(response);
				});
			} else {
				//just ignore
			}

		}


		//execute functions
		$(document).ready(function() {
			loadSettings(writeSettings);
		});


		$saveSettingsButton.on('click', saveSettings);

	}());

/*
	//enabling inputs accordingly to their checkboxes
	(function inputEnabler() {

		//feel the change of the inputs
		$receiveNotifications.on('change', toggleNotifications);
		$bandwidthLimit.on('change', toggleBandwidth);

		//executing it on document ready
		$(document).ready(function() {
			toggleNotifications();
			toggleBandwidth();
		});

		function toggleNotifications() {
			if ( $receiveNotifications.is(':checked') ) {
				//enable
				$notificationsBox.removeClass('disabled');
				$notificationsEmail.removeAttr('disabled');
			} else {
				//empty and disable
				$notificationsBox.addClass('disabled');
				$notificationsEmail
					.val('')
					.attr('disabled', 'disabled');
			}
		}

		function toggleBandwidth() {
			if ( $bandwidthLimit.is(':checked') ) {
				//enable
				$bandwidthBox.removeClass('disabled');
				$uploadLimit.removeAttr('disabled');
				$downloadLimit.removeAttr('disabled');
			} else {
				//empty and disable
				$bandwidthBox.addClass('disabled');
				$uploadLimit
					.val('')
					.attr('disabled', 'disabled');
				$downloadLimit
					.val('')
					.attr('disabled', 'disabled');
			}
		}

	}());
*/


	//feedback manager (and loading witness)
	(function feedbackManager() {
		
	}());


}
