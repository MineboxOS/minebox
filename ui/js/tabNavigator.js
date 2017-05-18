//requires hashManager

function TabNavigator( data ) {

	//data contains
		//buttons DOM selection
		//tabs DOM selection
		//defaultTab string id

	var $buttons = data.buttons;
	var $tabs = data.tabs;



	//vars
	var CONFIG = {
		events: {
			tabShown: {
				name: 'tabShown'
			}
		},
		animationDuration: 600,
		defaultTab: data.defaultTab
	};




	function go( string ) {
		//write hash
		hashManager.write(string);
	}



	function hideAll(cb) {
		$tabs.fadeOut( CONFIG.animationDuration / 2);
		setTimeout(function() {
			$('html,body').scrollTop(0);
			if (cb) {cb();}
		}, CONFIG.animationDuration / 2);

	}




	function showTab($tab,cb) {
		//hidding all tabs
		hideAll(function() {
			$tab.trigger(CONFIG.events.tabShown.name)
				.fadeIn(CONFIG.animationDuration, function() {
				if (cb) {cb();}
			});
		});
	}




	function disable() {
		$(CONFIG.events.hash.target).unbind(CONFIG.events.hash.name);
	}




	$buttons.on('click', function() {
		if ( !$(this).attr('disabled') ) {
			go( $(this).attr('data-go') );
		}
	});




	$(document).ready(function() {

		//read hash
		var hash = hashManager.read();

		//sets CONFIG.events.hash configuration inherited from hashManager
		CONFIG.events.hash = hashManager.eventConfig();

		//binding events
		//changed hash
		$(CONFIG.events.hash.target).bind(CONFIG.events.hash.name, function() {
			//reading hash again
			hash = hashManager.read();
			//finding the tab
			var $theTab = null;
			for ( var n = 0; n < $tabs.length; n++ ) {
				if ( $($tabs[n]).attr('data-tab') == hash ) {
					$theTab = $($tabs[n]);
					break;
				}
			}
			if ( $theTab ) {
				//the tab exists
				//show tab
				showTab($theTab);
			}
		});

		if ( !hash.length ) {
			go( CONFIG.defaultTab );
		} else {
			go( hash );
		}

	});

	return {
		go: go,
		hideAll: hideAll,
		disable: disable
	}

}