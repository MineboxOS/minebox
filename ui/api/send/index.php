<?php

	
	http_response_code( 200 );
	//$response = array();
	$response = null;
	$response->message = "1e-05 SC successfully sent to d7edb32b1182ccdd46a1ab3909e454b3afd8e3d2781a38f78c398c173d2d6a77773978bd699e.";
	$response->transactionids = array("47e023c54fbeb583f25f555ded7f97e2702183c1d3cba4be557f58a61d70a731", "7da76ebac83b9c259874395bc5a0860643e8aa68612870a8f60662c42f5aadc8");
	echo json_encode($response);

?>