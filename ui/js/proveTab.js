//requires notify.js
//requires tabNavigator

function proveTab() {

	//show a notification if register is empty on ready
	var notify = new Notify({
		message: 'Oops! Lost your track?<br />Seems that you have arrived here refreshing your browser or by magic.<br/>You need to have a valid and verified hostname and private key. Validate them again and provide username and password.<br />Press OK to proceed.'
	});

	if ( !register.seed || !register.hostname ) {
		tabNavigator.go('register');
		notify.print();
	}



	//print qr code
	$('body').on('click', '#print-encryption-key-qr-code-reminder', function() {
		window.open('print-qr-code.html');
	});
}