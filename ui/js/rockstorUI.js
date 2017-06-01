//requires notify


var rockstorLogin = RockstorLogin();


function RockstorLogin() {

	var CONFIG = {
		urls: {
			setup: '/setup_user',
			appliances: '/api/appliances',
			login: '/api/login'
		}
	};
	var info = {};

	function updateInfo() {
		info.username = $('.register-section .register-username').val();
		info.password = $('.register-section .register-password').val();
		info.passwordRepeat = $('.register-section .register-password-repeat').val();
		info.hostname = $('.register-section .register-hostname').val();
	}


	function init(cb) {


		//updating users info
		updateInfo();


		//calling to setup
		$.ajax({
			url: CONFIG.urls.setup,
			method: 'POST',
			dataType: 'application/json',
			contentType: 'application/json',
			data: JSON.stringify({
				username: info.username,
				password: info.password,
				is_active: true
			})
		})
		.done(function(data) {





			//calling to login
			$.ajax({
				url: CONFIG.urls.login,
				method: 'POST',
				dataType: 'application/json',
				data: {
					username: info.username,
					password: info.password
				}
			})
			.done(function(data) {






				//the cookie is set now
				//calling to appliances
				$.ajax({
					url: CONFIG.urls.appliance,
					method: 'POST',
					dataType: 'application/json',
					contentType: 'application/json',
					headers: {
						"X-CSRFToken": Cookie.get('csrftoken')
					},
					data: JSON.stringify({
						hostname: info.hostname,
						current_appliance: true
					})
				})
				.done(function(data) {




					//we're done
					if ( cb ) {
						cb();
					}







				}).
				fail(function(err) {
					//something failed
					var notify = new Notify({message:'We couldn\t call to appliances.'});
					notify.print();
				});




			}).
			fail(function(err) {
				//something failed
				var notify = new Notify({message:'We couldn\t log you in.'});
				notify.print();
			});



		}).
		fail(function(err) {
			//something failed
			var notify = new Notify({message:'We couldn\t setup the user.'});
			notify.print();
		});



	}


	return {
		init: init
	}


}