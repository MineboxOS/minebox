//requires tabNavigator & hashManager
function ProgressScreen() {

	var $progressScreenElement = $('#progress-screen');
	var loadingSpace = LoadingSpace();
	var mineboxProcess = null;

	function open(pro) {

		//updating mineboxProcess
		mineboxProcess = pro;

		// Don't display the log items that don't fit the current process.
		if (mineboxProcess == 'recover') {
			$('#boot-log-restore').addClass('active');
		}
		else {
			$('#boot-log-restore').addClass('deactivated');
		}
		$('#boot-log-storage-key').addClass('active');
		$('#boot-log-storage-init').addClass('active');

		//init loading space animation
		loadingSpace.init();
		//change hash
		hashManager.write('running');
		//hide all views
		tabNavigator.hideAll(function() {
			//disable tabNavigator 
			tabNavigator.disable();
			//display progress screen
			$progressScreenElement.fadeIn(300);

			//running "loading screen"
			bootLog(function() {
				//redirecting to home
				window.location.href = '/home';
			});

			//call to rockstorLogin
			rockstorLogin.init(function() {
			});
		});
	}

	function getProcess() {
		return mineboxProcess;
	}

	function bootLog(cb) {
		var $bootLog = $('#boot-log li');
		var int = setInterval(interval, 3000);

		interval();
		function interval() {
			$.ajax({
				url: config.mug.url + 'status',
				method: 'GET',
				contentType: 'application/json',
			})
			.done(function(data) {
				if (data['minebd_encrypted']) {
					$('#boot-log-storage-key').removeClass('active');
					$('#boot-log-storage-key').addClass('done');
				}
				if (data['minebd_storage_mounted']) {
					$('#boot-log-storage-init').removeClass('active');
					$('#boot-log-storage-init').addClass('done');
				}
				// If we are in a restore run, consider it "done" for this screen when we can mount the storage.
				if (!$('#boot-log-restore').hasClass('deactivated') && data['minebd_storage_mounted']) {
					$('#boot-log-restore').removeClass('active');
					$('#boot-log-restore').addClass('done');
				}
				// In case, users have been set up and we didn't catch it, switch to done.
				if (data['users_created']) {
					$('#boot-log-user').removeClass('active');
					$('#boot-log-user').removeClass('fail');
					$('#boot-log-user').addClass('done');
				}
				if (data['user']) {
					$('#boot-log-login').removeClass('active');
					$('#boot-log-login').removeClass('fail');
					$('#boot-log-login').addClass('done');
				}
				// Same for hostname as for user.
				if (data['hostname'] != 'Minebox') {
					$('#boot-log-hostname').removeClass('active');
					$('#boot-log-hostname').removeClass('fail');
					$('#boot-log-hostname').addClass('done');
				}
			})

			var n = 0;
			$bootLog.each(function() {
				if ($(this).hasClass('done') || $(this).hasClass('deactivated')) {
					n++;
				}
			});

			if ( n == $bootLog.length ) {
				clearInterval(int);
				if ( cb ) { cb() }
			}
		}
	}



	return {
		open: open,
		getProcess: getProcess
	}

}
var progressScreen = ProgressScreen();