function LoadingWitness() {


	var $loading = $('#loading-witness');
	var CONFIG = {
		activeClass: 'active'
	};


	function start() {
		$loading.addClass( CONFIG.activeClass );
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