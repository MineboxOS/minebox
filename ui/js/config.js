var config = {
	mug: {
		url: 'https://' + location.hostname + ':5000/'
	},
	views: {
		path: 'views/',
		interval: 200, //default 200
		containerID: 'wrapper',
		map: {
			'dashboard': 'dashboard.html',
			'wallet': 'wallet.html',
			'contracts': 'contracts.html',
			'snapshots': 'snapshots.html',
			'settings': 'settings.html'
		},
		buttons: {
			class: 'active', //string that will be added to buttons to set them active
			selector: '#menu li', //string to jQuery-select button elements
			attribute: 'data-go' //attribute that holds the id of the content that view.buttons must show
		},
		messages: {
			fail: 'Something went wrong. Please try again.'
		}
	},
	fullHeight: {
		exclude: [
			'#header'
		]
	},
};