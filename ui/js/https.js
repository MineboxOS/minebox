//requires notify.js

var notify = new Notify({
	message: 'We have detected you\'re not connecting via https. Press OK to redirect.',
	onAccept: function() {
		httpsRedirection();
	},
	onClose: function() {
		httpsRedirection();
	}
});

if ( location.protocol != 'https:' ) {
	notify.print();
}

function httpsRedirection() {
	window.location = 'https:' + window.location.href.substring(window.location.protocol.length);
}