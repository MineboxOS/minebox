//requires hashManager

function TabNavigator( data ) {

	//data contains
		//buttons DOM selection
		//tabs DOM selection
		//defaultTab string id
		//halting list. an array of strings that will halt the system and make it ignore hash changes in the url

	var $buttons = data.buttons;
	var $tabs = data.tabs;



	//vars
	var CONFIG = {
		event: null,
		animationDuration: 600,
		defaultTab: data.defaultTab,
		halt: false, //when this var is set to OK, showTab() function won't work
		haltingOn: data.haltingOn
	};




	function go( string ) {
		//write hash
		hashManager.write(string);
		halt(string);
	}


	function halt( string ) {
		//this fnc checks if the requested tag is on the halting list
		for ( var n = 0; n < CONFIG.haltingOn.length; n++ ) {
			if ( string == CONFIG.haltingOn[n] ) {
				CONFIG.halt = true;
				console.log('halted!');
			}
		}
	}



	function hideAll(cb) {
		$tabs.fadeOut( CONFIG.animationDuration / 2);
		setTimeout(function() {
			$('html,body').scrollTop(0);
			if (cb) {cb();}
		}, CONFIG.animationDuration / 2);

	}




	function showTab($tab,cb) {
		if ( !CONFIG.halt ) {
			//hidding all tabs
			hideAll(function() {
				$tab.fadeIn(CONFIG.animationDuration, function() {
					if (cb) {cb();}
				});
			});
		} else {
			alert('You can\'t go back at this point.');
		}
	}




	$buttons.on('click', function() {
		go( $(this).attr('data-go') );
	});




	$(document).ready(function() {

		//read hash
		var hash = hashManager.read();

		//sets CONFIG.event configuration inherited from hashManager
		CONFIG.event = hashManager.eventConfig();

		//binding events
		//changed hash
		$(CONFIG.event.target).bind(CONFIG.event.name, function() {
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
		go: go
	}

}