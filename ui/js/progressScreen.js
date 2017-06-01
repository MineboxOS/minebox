//requires tabNavigator & hashManager
function ProgressScreen() {

	var $progressScreenElement = $('#progress-screen');
	var loadingSpace = LoadingSpace();

	function open() {
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
			rockstorLogin.init();

			//faking "loading screen"
			//bootLog();
		});
	}


	return {
		open: open
	}

}
var progressScreen = ProgressScreen();