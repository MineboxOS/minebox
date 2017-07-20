//requires tabNavigator & hashManager
function ProgressScreen() {

	var $progressScreenElement = $('#progress-screen');
	var loadingSpace = LoadingSpace();
	var mineboxProcess = null;

	function open(pro) {

		//updating mineboxProcess
		mineboxProcess = pro;

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

			//call to rockstorLogin
			rockstorLogin.init(function() {
				//faking "loading screen"
				bootLog(function() {
					//redirecting to home
					window.location.href = '/home';
				});
			});
		});
	}

	function getProcess() {
		return mineboxProcess;
	}

	function bootLog(cb) {
		var $bootLog = $('#boot-log input');
		var n = 0;
		var checkingInt = null;
		var int = setInterval(interval, 3000);

		interval();
		function interval() {
			if ( n > 0) {
				check( $($bootLog[n-1]) );
			}
			checking( $($bootLog[n]) );

			if ( n == $bootLog.length ) {
				clearInterval(int);
				clearInterval(checkingInt);
				if ( cb ) { cb() }
			}

			n++;
		}

		function check( $element ) {
			$element.prop('checked', true);
		}

		function checking( $element ) {
			if ( checkingInt ) {
				clearInterval(checkingInt);
				checkingInt = null;
			}
			checkingInt = setInterval(checkingIntervalFunction, 500); //interval exec
			checkingIntervalFunction(); //first exec
			function checkingIntervalFunction() {
				if ( $element.prop('checked') ) {
					$element.prop('checked', false);
				} else {
					check($element);
				}
			}
		}
	}



	return {
		open: open,
		getProcess: getProcess
	}

}
var progressScreen = ProgressScreen();