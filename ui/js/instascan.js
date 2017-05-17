var instascanManager = InstascanManager();

function InstascanManager() {

	var $instascanBox = $('#instascan-box');
	var videoElementID = 'instascan-video';
	let scanner = null;

	function scan(cb) {
		scanner = new Instascan.Scanner({
			video: document.getElementById(videoElementID)
		});
		scanner.addListener('scan', function (content) {
			hide(function() {
				cb(content);
			});
		});
		Instascan.Camera.getCameras().then(function (cameras) {
			if (cameras.length > 0) {
				scanner.start(cameras[0]);
			} else {
				alert('No cameras found.');
			}
		}).catch(function (e) {
			console.error(e);
		});
	}

	function show(cb) {
		$instascanBox.addClass('active');
		if ( cb ) { setTimeout(cb, 300) };
	}

	function hide(cb) {
		$instascanBox.removeClass('active');
		scanner.stop();
		if ( cb ) { setTimeout(cb, 300) };
	}


	return {
		scan: scan,
		show: show,
		hide: hide
	}
}