function Requester() {

  var xhr;

  var CONFIG = {
    cache: false,
    timeout: 10000
  };

  function setMethod(method) {
    CONFIG.method = method;
  }
  function setURL(url) {
    CONFIG.url = url;
  }
  function setData(data) {
    CONFIG.data = data;
  }
  function setType(type) {
    CONFIG.dataType = type;
  }
  function setContentType(type) {
    CONFIG.contentType = type;
  }
  function setCache(cache) {
    CONFIG.cache = cache;
  }
  function setCredentials(credentials) {
    CONFIG.xhrFields = {
      withCredentials: credentials
    };
  }
  function setTimeoutTime(time) {
    CONFIG.timeout = time;
  }
  function setTimeoutFunc(func) {
    timeoutFunction = func;
  }

  function abort() {
    xhr.abort();
  }

  function run( successCallback, errorCallback, callback_func ) {
    xhr = $.ajax(CONFIG)
    .done(function( data ) {
      successCallback( data );
    })
    .fail(function( response, textStatus ) {
      errorCallback( response );

      //executing timeout function if any
      if ( textStatus == 'timeout' && timeoutFunction ) {
        timeoutFunction();
      }
    })
    .always(function() {
      if (callback_func) { callback_func(); }
    });
  }

  return {
    setMethod: setMethod,
    setURL: setURL,
    setData: setData,
    setType: setType,
    setCache: setCache,
    setCredentials: setCredentials,
    setContentType: setContentType,
    setTimeoutTime: setTimeoutTime,
    setTimeoutFunc: setTimeoutFunc,
    abort: abort,
    run: run
  }
}
