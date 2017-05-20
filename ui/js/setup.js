function Setup() {

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
			});
		}


		return {
			open: open
		}

	}
	var progressScreen = ProgressScreen();

	
}

	


	/*
	var setupsFlowNavigator = SetupsFlowNavigator();
	function SetupsFlowNavigator() {

		var buttonsSelector = '.setup-flow-button',
			tabsSelector = '#setup-page>section',
			$tabs = $(tabsSelector),
			time = 300;



		//on click
		$('body').on('click', buttonsSelector, function() {
			tab = $(this).attr('data-setup-flow');

			if ( $(tabsSelector + '.' + tab + '-section') && $(tabsSelector + '.' + tab + '-section').is(':hidden') ) {
				//if exists and is hidden
				showSetupSection(tab);
			}
		});


		//on init
		$(document).ready(function() {
			init();
		});

		//funcs
		function hideAll() {
			$('html, body').animate({
				scrollTop: 0,
			}, time/2, function() {
				$tabs.fadeOut(0);
				window.location.hash = '';
			});
		}

		function showSetupSection( section ) {
			hideAll();
			setTimeout(function() {
				$(tabsSelector + '.' + section + '-section').fadeIn(time);
				window.location.hash = section;
			}, time + 10);
		}

		function init() {
			if ( !window.location.hash ) {
				$($tabs[0]).fadeIn(time);
			} else {
				hash = window.location.hash;
				hash = hash.substr(1, hash.length);

				if ( $(tabsSelector + '.' + hash + '-selection') ) {
					//if exists
					showSetupSection(hash);
				}
			}
		}

		return {
			init: init,
			show: showSetupSection,
			hide: hideAll
		}

	}*/




	