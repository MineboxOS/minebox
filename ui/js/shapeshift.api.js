

//methods to connect to shapeshift API
//requires requester.js


function Shapeshift() {


	var CONFIG = {
		api_url: 'https://shapeshift.io'
	}




	/* GET METHODS */
	var getRateRequester = new Requester();
	function getRate( pair, successFunc, failFunc ) {
		//gets the rate (price) for given pair
		getRateRequester.setURL( CONFIG.api_url + '/rate/' + pair );
		getRateRequester.setMethod('GET');
		getRateRequester.setCache(false);
		getRateRequester.run(successFunc, failFunc);
	}




	var getDepositLimitRequester = new Requester();
	function getDepositLimit( pair, successFunc, failFunc ) {
		//gets deposit limit for given pair
		//coins exceeding limit will be returned to the return address if provided, otherwise contact Shapeshift
		getDepositLimitRequester.setURL( CONFIG.api_url + '/limit/' + pair );
		getDepositLimitRequester.setMethod('GET');
		getDepositLimitRequester.setCache(false);
		getDepositLimitRequester.run(successFunc, failFunc);
	}




	var getMarketInfoRequester = new Requester();
	function getMarketInfo( pair, successFunc, failFunc ) {
		//gets market info for given pair [optional], if given. otherwise returns all market info
		getMarketInfoRequester.setURL( CONFIG.api_url + '/marketinfo/' + pair );
		getMarketInfoRequester.setMethod('GET');
		getMarketInfoRequester.setCache(false);
		getMarketInfoRequester.run(successFunc, failFunc);
	}




	var getTransactionStatusRequester = new Requester();
	function getTransactionStatus( address, successFunc, failFunc ) {
		//gets transaction status for a given address
		getTransactionStatusRequester.setURL( CONFIG.api_url + '/txStat/' + address );
		getTransactionStatusRequester.setMethod('GET');
		getTransactionStatusRequester.setCache(false);
		getTransactionStatusRequester.run(successFunc, failFunc);
	}




	var getSupportedCoinsRequester = new Requester();
	function getSupportedCoins( successFunc, failFunc ) {
		//gets a list of all coins supported by Shapeshift and current status (available/unavailable)
		getSupportedCoinsRequester.setURL( CONFIG.api_url + '/getCoins/' );
		getSupportedCoinsRequester.setMethod('GET');
		getSupportedCoinsRequester.setCache(false);
		getSupportedCoinsRequester.run(successFunc, failFunc);
	}




	/* POST METHODS */
	var convertRequester = new Requester();
	function convert( data, successFunc, failFunc ) {
		//converts the coins of given pair, and withdraws the result to withdrawal address
		//data example: [copied from shapeshift api documentation]
			//{
			//	"withdrawal": "[output_address]",
			//  "pair": "btc_ltc",
			//  "returnAddress": "[return_address]"
			//}
		//data explaination:
		//	   withdrawal: the address for resulting coin to be sent to
		//	         pair: what coins are being exchanged in the form [input coin]_[output coin]  ie btc_ltc
		//	returnAddress: (Optional) address to return deposit to if anything goes wrong with exchange
		//	      destTag: (Optional) Destination tag that you want appended to a Ripple payment to you
		//	    rsAddress: (Optional) For new NXT accounts to be funded, you supply this on NXT payment to you

		convertRequester.setURL( CONFIG.api_url + '/shift' );
		convertRequester.setMethod('POST');
		convertRequester.setCache(false);
		convertRequester.setDataType('JSON');
		convertRequester.setData( data );
		convertRequester.run(successFunc, failFunc);
	}




	var cancelConversionRequester = new Requester();
	function cancelConversion( deposit_address, successFunc, failFunc ) {
		//This call allows you to request for canceling a pending transaction by the deposit address. If there is fund sent to the deposit address, this pending transaction cannot be canceled.
		cancelConversionRequester.setURL( CONFIG.api_url + '/cancelpending' );
		cancelConversionRequester.setMethod('POST');
		cancelConversionRequester.setCache(false);
		cancelConversionRequester.setDataType('JSON');
		cancelConversionRequester.setData( { address: deposit_address } );
		cancelConversionRequester.run(successFunc, failFunc);
	}




	/* MISC METHODS*/
	var validateAddressRequester = new Requester();
	function validateAddress( address, coinSymbol, successFunc, failFunc ) {
		//validates a given address for a given coinSymbol
		validateAddressRequester.setURL( CONFIG.api_url + '/getCoins/' + address + '/' + coinSymbol );
		validateAddressRequester.setMethod('GET');
		validateAddressRequester.setCache(false);
		validateAddressRequester.run(successFunc, failFunc);
	}



	return {
		get: {
			rate: getRate,
			depositLimit: getDepositLimit,
			marketInfo: getMarketInfo,
			transactionStatus: getTransactionStatus,
			supportedCoins: getSupportedCoins
		},
		post: {
			convert: convert,
			cancelConversion: cancelConversion
		},
		validateAddress: validateAddress
	}

}

var shapeshift = Shapeshift();