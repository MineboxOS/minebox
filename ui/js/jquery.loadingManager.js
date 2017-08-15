function LoadingManager() {

	//there is an array of loading identifiers

	//there is a loop/events based function that feels the changes on the array and
		//whenever there is something on it, displays the loader screen and the ids list
		//whenever there is nothing on it, hides the loader screen

	//the loader screen may work as a click-prevention-map and disallow the user to interact with the layout underneath

	var CONFIG = {
		events: {
			loaderAdded: 'loaderAdded',
			loaderRemoved: 'loaderRemoved'
		},
		speed: 200
	};
	var loading = [];

	var $loadingScreen = $('#loading-screen'),
		$loadingContents = $('#loading-contents .loading-contents');




	//add a new loading ID to loading array
	function addLoader( id ) {

		//adding id to loading array, even if there is already the same id on the list
		loading.push(id);

		//rise the event
		$('body').trigger( CONFIG.events.loaderAdded );
	}



	//removes matching ID from loading array
	function removeLoader( id ) {

		//removing id from the array, only the first occurence since there might be several ID's matching names
		for ( var n = 0; n < loading.length; n++ ) {
			if ( id == loading[n] ) {
				loading.splice( n, 1 );
				break;
			}
		}

		//rise the event
		$('body').trigger( CONFIG.events.loaderRemoved );
	}



	//writes down in #loading-contents whatever is on array
	//it is executed anytime the array have changed
	function updateContents() {
		//emptying container
		$loadingContents.html( loading.toString() );
	}




	//displays loading screen
	function show() {
		$loadingScreen.fadeIn( CONFIG.speed );
	}



	//hides loading screen
	function hide() {
		$loadingScreen.fadeOut( CONFIG.speed );
	}


	//when a loader id has been added
	$('body').bind( CONFIG.events.loaderAdded, function() {
		//update array contents witness
		updateContents();

		if ( $loadingScreen.is(':hidden') ) {
			//if $loadingScreen was hidden
			//show $loadingScreen
			show();
		}
	});


	//when a loader id has been removed
	$('body').bind( CONFIG.events.loaderRemoved, function() {
		//update array contents witness
		updateContents();

		console.log(loading);
		console.log(loading.length);

		if ( !loading.length ) {
			//loading array is empty
			//hide $loadingScreen
			hide();
		}
	});


	return {
		add: addLoader,
		remove: removeLoader,
		update: updateContents,
		show: show,
		hide: hide
	}



}

var loadingManager = LoadingManager();