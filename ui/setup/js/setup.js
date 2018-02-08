function Setup() {
	var CONFIG = {
		urls: {
			status: config.mug.url + 'status',
		}
	};


	//executing the function when the tab is opened for first time
	$('.welcome-section.navigation-tab').bind('tabShown', function() {
		// Get current status of the backend and react to that.
		$.ajax({
			url: CONFIG.urls.status,
			method: 'GET',
			dataType: 'json',
			contentType: 'application/json',
		})
		.done(function(data) {
			// React to status.
			if (data['minebd_storage_mounted'] && data['user_setup_complete']) {
				// Everything set up already.
				console.log('Setup already completed.');
				$('#welcomeline').html('Your Minebox is already set up, go to the <a href="/">main user interface</a> or the <a href="../">Minebox dashboard</a>.');
			}
			else if (data['minebd_storage_mounted']) {
				// Minebox storage ready, but no users have been set up.
				console.log('Minebox storage ready, but no users have been set up.');
				// Only offer to set user/host info.
				$('.encryption').hide();
				// Remove the go action - this will make the click function to preceed to progress screen directly.
				$('#register-minebox-button').removeAttr('data-go');
				tabNavigator.go('register');
			}
			else if (!data['minebd_running']) {
				// Storage inactive
				console.log('Storage inactive, reboot box, or contact support!');
				var notify = new Notify({message:'The Minebox storage is inactive. Try rebooting, or contact Minebox support if this persists.', relPath: '../'});
				notify.print();
			}
			else if (data['minebd_encrypted']) {
				// Encryption set up, getting ready for use...
				console.log('Minebox key is set, jumping to progress screen.');
				progressScreen.open(data['restore_running'] ? 'recover' : 'register');
			}
			else {
				// No encryption key yet, show setup welcome as usual.
			}
		}).
		fail(function(request, textStatus, errorThrown) {
			// Getting status failed.
			console.log('Status could not be retrieved from MUG.');
			var notify = new Notify({message:'The status of your Minebox could not be determined. Try reloading or rebooting.', relPath: '../'});
			notify.print();
		});
	});


	//executing the function when the tab is opened for first time
	$('.recover-section.navigation-tab').bind('tabShown', function() {
		if ( $(this).attr('data-services') != 'on' ) {
			recoverTab();
			$(this).attr('data-services', 'on');
		}
	});




	//executing the function when the tab is opened for first time
	$('.register-section.navigation-tab').bind('tabShown', function() {
		if ( $(this).attr('data-services') != 'on' ) {
			registerTab();
			$(this).attr('data-services', 'on');
		}
	});




	//executing the function when the tab is opened for first time
	$('.prove-section.navigation-tab').bind('tabShown', function() {
		if ( $(this).attr('data-services') != 'on' ) {
			proveTab();
			$(this).attr('data-services', 'on');
		}
	});


}
