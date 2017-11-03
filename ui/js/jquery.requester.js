function Requester() {

  var xhr;

  //init as empty function to prevent error on console if no timeoutFunction is set on current instance
  timeoutFunction = function() {};

  var CONFIG = {
    cache: false,
    timeout: 55000 // ms, just under a minute
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
  function setBeforeSend(func) {
    CONFIG.beforeSend = func;
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

  function getHandler() {
    return xhr;
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
    setBeforeSend: setBeforeSend,
    setCredentials: setCredentials,
    setContentType: setContentType,
    setTimeoutTime: setTimeoutTime,
    setTimeoutFunc: setTimeoutFunc,
    abort: abort,
    getHandler, getHandler,
    run: run
  }
}
