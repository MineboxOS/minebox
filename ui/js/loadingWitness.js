function LoadingWitness() {


	var $loading = $('#loading-witness');
	var CONFIG = {
		activeClass: 'active'
	};


	function start() {
		if ( !$loading.hasClass( CONFIG.activeClass ) ) {
			$loading.addClass( CONFIG.activeClass );
		} //otherwise just ignore
	}

	function stop() {
		$loading.removeClass( CONFIG.activeClass );
	}


	return {
		start: start,
		stop: stop
	}

}
var loadingWitness = LoadingWitness();