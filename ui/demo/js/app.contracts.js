/* jsing dashboard */
viewLoader.add('contracts', contracts);

function contracts() {

  var contracts;
  var $contractInfo = $('#contract-info');
  var $contractsButtons;

  init();

  function init() {
    //recover contracts list
    getContractsList(function() {
      buildContent();
    });
  }



  function buildContent() {
    var template;
    var output;
    var counter = 0;
    get.template('templates/contracts/iteration.html', function(data) {
      template = data;
      for (var n = 0; n < contracts.length; n++) {
        getContractDetails(contracts[n],function(details){
          output = template;
          output = replaceAll(output, '{{id}}', details.id);
          output = replaceAll(output, '{{file-size-bytes}}', details.fileSizeBytes);
          output = replaceAll(output, '{{max-duration-weeks}}', details.maxDurationWeeks);
          output = replaceAll(output, '{{starting-time}}', details.startingTime);
          output = replaceAll(output, '{{collateral-per-tb-month-sc}}', details.collateralPerTBMonthSC);
          output = replaceAll(output, '{{price-per-tb-per-month-sc}}', details.pricePerTBPerMonthSC);
          output = replaceAll(output, '{{bandwidth-price-sc}}', details.bandwidthPriceSC);

          output = replaceAll(output, '{{date}}', format.date(parseInt(details.startingTime)));
          output = replaceAll(output, '{{time}}', format.time(parseInt(details.startingTime)));

          $contractInfo.append(output);
        });
      }
    }, function() {
      fail();
    });
  }



  function getContractsList(callback_func) {
    get.contracts.list(function(response) {
      contracts = response;
      if (callback_func) {
        callback_func();
      }
    }, function() {
      fail();
    });
  }



  function getContractDetails(id,callback_func) {
    get.contracts.details(id,function(details) {
      callback_func(details);
    }, function() {
      fail();
    });
  }



  function fail() {
    alert('Everything failed, start again');
  }

}
