viewLoader.add('settings', Settings);

function Settings() {

	//init vars
	var $bandwidthBox = $('.bandwidth-box'),
		$bandwidthLimit = $('#bandwidth-limit'),
		$uploadLimit = $('#upload-limit'),
		$downloadLimit = $('#download-limit'),
		$notificationsBox = $('.notifications-box'),
		$receiveNotifications = $('#receive-notifications'),
		$notificationsEmail = $('#user-email'),
		$saveSettingsButton = $('#save-settings');

	var loadingWitness = false; //hard coded


	//handle settings read/write/load/save
	(function handleSettings() {
		//check settings in mug
		function loadSettings(cb) {

			//init loading witness
			loadingManager.add('User settings');

			setTimeout(function() {
				//connects to mug
				//returns this object
				var settings = {
					notifications: {
						enabled: true,
						email: 'myemail@myserver.com'
					},
					limit: {
						enabled: true,
						upload: 1000,
						download: 0
					}
				};

				//end loading witness
				loadingManager.remove('User settings');

				if (cb) {
					cb(settings);
				} else {
					return settings;
				}
			}, 5000);
		}


		//send settings to mug
		function sendSettings(settings, cb) {
			//init loading witness
			loadingWitness = true; //hard coded

			//send settings object to MUG
			setTimeout(function() {

				//end loading witness
				loadingWitness = false; //hard coded

				if ( cb ) {
					cb(true); //true as if settings were saved properly
				}
			}, 4000);
		}


		//given a settings object, put them on the specific inputs
		function writeSettings(settings) {

			//notifications
			$receiveNotifications
				.prop('checked', settings.notifications.enabled)
				.trigger('change');
			if ( settings.notifications.enabled ) {
				$notificationsEmail.val( settings.notifications.email );
			}

			//minebox bandwidth limit
			$bandwidthLimit
				.prop('checked', settings.limit.enabled)
				.trigger('change');
			if ( settings.limit.enabled ) {
				$uploadLimit.val( settings.limit.upload );
				$downloadLimit.val( settings.limit.download );
			}

		}


		//read inputs and return settings object
		function readSettings() {
			var settings = {};

			settings.notifications = {
				enabled: $receiveNotifications.is(':checked'),
				email: $notificationsEmail.val()
			};

			settings.limit = {
				enabled: $bandwidthLimit.is(':checked'),
				upload: $uploadLimit.val(),
				download: $downloadLimit.val()
			};

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



	//feedback manager (and loading witness)
	(function feedbackManager() {
		
	}());


}
