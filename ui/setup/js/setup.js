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




	
}