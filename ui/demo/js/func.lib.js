function getRandomString( length ) {
  var string = '',
      letters = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'],
      chars = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z','0','1','2','3','4','5','6','7','8','9'];
  for ( var n = 0; n < length; n++ ) {
    if ( n == 0 ) {
      string += letters[getRandomInt(0, letters.length - 1 )];
    } else {
      string += chars[getRandomInt(0, chars.length - 1 )];
    }
  }
  return string;
}


function getRandomInt( min, max ) {
  //http://stackoverflow.com/questions/1527803/generating-random-numbers-in-javascript-in-a-specific-range
  //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/random
  return Math.floor(Math.random() * (max - min + 1)) + min;
}


function objectLength(obj) {
 return Object.keys(obj).length;
}


function objectKeys(obj) {
  return Object.keys( obj );
}


function replaceAll( str, find, replace ) {
  function escapeRegExp(str) {
    return str.replace(/([.*+?^=!:${}()|\[\]\/\\])/g, "\\$1");
  }
  return str.replace(new RegExp(escapeRegExp(find), 'g'), replace);
}


function Format() {
  function d(timestamp) {
    var d = new Date(timestamp);
    return make2Digit(d.getDate()) + '/' + make2Digit(d.getMonth()+1) + '/' + d.getFullYear();
  }
  function t(timestamp) {
    var d = new Date(timestamp);
    return make2Digit(d.getHours()) + ':' + make2Digit(d.getMinutes()) + ':' + make2Digit(d.getSeconds());
  }
  return {
    date: d,
    time: t
  }
}

function make2Digit(value) {
  value = parseInt(value);
  if ( value < 10 && value >= 0 ) {
    return '0' + value;
  } else if ( value > -10 && value < 0 ) {
    return '-0' + (value*-1);
  } else {
    return value;
  }
}

function safeString(string) {
  /* requires replaceAll() */
  var output = string.toLowerCase();
  output = replaceAll(output, 'ñ', 'n');
  output = replaceAll(output, ' ', '-');
  output = replaceAll(output, 'á', 'a');
  output = replaceAll(output, 'à', 'a');
  output = replaceAll(output, 'ä', 'a');
  output = replaceAll(output, 'é', 'e');
  output = replaceAll(output, 'è', 'e');
  output = replaceAll(output, 'ë', 'e');
  output = replaceAll(output, 'í', 'i');
  output = replaceAll(output, 'ì', 'i');
  output = replaceAll(output, 'ï', 'i');
  output = replaceAll(output, 'ó', 'o');
  output = replaceAll(output, 'ò', 'o');
  output = replaceAll(output, 'ö', 'o');
  output = replaceAll(output, 'ú', 'u');
  output = replaceAll(output, 'ú', 'u');
  output = replaceAll(output, 'ü', 'u');
  output = replaceAll(output, 'ñ', 'n');
  output = replaceAll(output, '<', '-');
  output = replaceAll(output, '>', '-');
  output = replaceAll(output, '"', '-');
  output = replaceAll(output, "'", '-');
  output = replaceAll(output, '.', '-');
  output = replaceAll(output, ':', '-');
  output = replaceAll(output, ',', '-');
  output = replaceAll(output, ';', '-');
  output = replaceAll(output, 'º', '-');
  output = replaceAll(output, 'ª', '-');
  output = replaceAll(output, '/', '-');
  output = replaceAll(output, '\\', '-');
  output = replaceAll(output, '|', '-');
  output = replaceAll(output, '@', '-');
  output = replaceAll(output, '?', '-');
  output = replaceAll(output, '!', '-');
  output = replaceAll(output, '¡', '-');
  output = replaceAll(output, '¿', '-');
  output = replaceAll(output, '·', '-');
  output = replaceAll(output, '*', '-');
  output = replaceAll(output, '+', '-');
  output = replaceAll(output, '#', '-');

  return output;
}
