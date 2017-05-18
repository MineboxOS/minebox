function HashManager() {

	//vars
	var CONFIG = {
		lastHash: '',
		event: {
			target: 'body',
			name: 'hashChanged'
		}
	};



	function writeHash( hash ) {
		//function that writes the hash
		window.location.hash = hash;
		$(window).trigger('hashchange');
	}

	
	function readHash() {
		//function that reads the hash, removes the # and returns the string
		var hash = window.location.hash;
		return hash.substr( 1, window.location.hash.length );
	}



	function returnEventConfig() {
		//returns event configuration to ease listening the rising events
		return CONFIG.event;
	}


	window.onhashchange = function() {
		var currentHash = readHash();
		//if hash have changed
		if ( currentHash != CONFIG.lastHash ) {
			//update CONFIG.lastHash
			CONFIG.lastHash = currentHash;
			//rising an event
			$(CONFIG.event.target).trigger(CONFIG.event.name);
		}
	}




	return {
		write: writeHash,
		read: readHash,
		eventConfig: returnEventConfig
	}
}
var hashManager = HashManager();