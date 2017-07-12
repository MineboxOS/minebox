function urlInfo() {

  function objectify( string ) {
    var obj = {};
    //splitting on &
    var amp = string.split('&');
    //splitting on =
    obj.h = amp[0];
    var eq;
    for ( var n = 1; n < amp.length; n++ ) {
      eq = amp[n].split('=');
      obj[eq[0]] = eq[1];
    }
    return obj;
  }
  function stringify( obj ) {
    var string = '';
    var keys = Object.keys(obj);
    string += obj.h;
    for ( var n = 1; n < keys.length; n++ ) {
      string += '&';
      string += keys[n]; //printing curreny key
      string += '=';
      string += obj[keys[n]]; //printing current value
    }
    return string;
  }

  function get() {
    var hashRaw = window.location.hash;
    var hash = hashRaw.substring(1, hashRaw.length);
    return objectify( hash );
  }
  function update( obj ) {
    window.location.hash = stringify( obj );
  }

  return {
    get: get,
    update: update
  }
}
