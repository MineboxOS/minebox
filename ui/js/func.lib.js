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
  output = replaceAll(output, '<', '');
  output = replaceAll(output, '>', '');
  output = replaceAll(output, '"', '');
  output = replaceAll(output, "'", '');
  output = replaceAll(output, '.', '-');
  output = replaceAll(output, ':', '');
  output = replaceAll(output, ',', '');
  output = replaceAll(output, ';', '');
  output = replaceAll(output, 'º', '');
  output = replaceAll(output, 'ª', '');
  output = replaceAll(output, '/', '');
  output = replaceAll(output, '\\', '');
  output = replaceAll(output, '|', '');
  output = replaceAll(output, '@', '-at-');
  output = replaceAll(output, '?', '');
  output = replaceAll(output, '!', '');
  output = replaceAll(output, '¡', '');
  output = replaceAll(output, '¿', '');
  output = replaceAll(output, '·', '');
  output = replaceAll(output, '*', '');
  output = replaceAll(output, '+', '');
  output = replaceAll(output, '#', '');

  return output;
}













function loadSpecificScripts( match, func ) {
  //This function will determine whether or not execute Home();
  //This function is meant to prevent scripts to execute out of their
  //ideal scope (as in example: team scripts executing when on privacy policy page)
  //It accepts two parameters:
    // the first is the string that may match $SEO[$lang][$page][$id]
    // the second is the function alias to execute
  if ( match == page ) {
    func();
  }
}












function validateEmail(email) {
  var atpos = email.indexOf("@");
  var dotpos = email.lastIndexOf(".");
  if ( atpos < 1 || dotpos < atpos+2 || dotpos+2 >= email.length ) {
    return false;
  } else {
    return true;
  }
}












function ios() {
  //http://stackoverflow.com/questions/9038625/detect-if-device-is-ios
  var iDevices = [
    'iPad Simulator',
    'iPhone Simulator',
    'iPod Simulator',
    'iPad',
    'iPhone',
    'iPod'
  ];

  if (!!navigator.platform) {
    while (iDevices.length) {
      if (navigator.platform === iDevices.pop()){ return true; }
    }
  }

  return false;
}












function keysToIgnore( pressed ) {
  //this function will return true if pressed is eiher cursor keys, shift, cmd,
  // ctrl, alt, option... otherwise will return false
  var ignoreKeys = [
    16, //shift
    17, //ctrl
    18, //alt
    91, //cmd
    20, //capsLock
    9,  //tab
    37, //cursorLeft
    38, //cursorUp
    39, //cursorRight
    40  //cursorDown
  ];
  var match = false;

  for ( var n = 0; n < ignoreKeys.length; n++ ) {
    if ( ignoreKeys[n] == pressed ) {
      match = true;
      break;
    }
  }

  return match;

}












function isNumericKey( pressed ) {
  //this function will return true if pressed is one of the numeric keys
  //otherwise will return false
  var numericKeys = [
    48, //0
    49, //1
    50, //2
    51, //3
    52, //4
    53, //5
    54, //6
    55, //7
    56, //8
    57  //9
  ];
  var match = false;

  for ( var n = 0; n < numericKeys.length; n++ ) {
    if ( numericKeys[n] == pressed ) {
      match = true;
      break;
    }
  }

  return match;
}












/**
 * Number.prototype.format(n, x)
 *
 * @param integer n: length of decimal
 * @param integer x: length of sections
 */
Number.prototype.format = function(n, x) {
    var re = '\\d(?=(\\d{' + (x || 3) + '})+' + (n > 0 ? '\\.' : '$') + ')';
    return this.toFixed(Math.max(0, ~~n)).replace(new RegExp(re, 'g'), '$&,');
};
function priceFormatter( number ) {
    return number.format(2, 3);
}












function make2Digits( number ) {

  if ( number > 9 ) {
    return number;
  } else {
    return '0' + number;
  }

}












function formatNumber( number ) {
  return parseFloat( number ).toFixed(2);
}












function formatDate(date, format, utc) {
    var MMMM = ["\x00", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    var MMM = ["\x01", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    var dddd = ["\x02", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    var ddd = ["\x03", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

    function ii(i, len) {
        var s = i + "";
        len = len || 2;
        while (s.length < len) s = "0" + s;
        return s;
    }

    var y = utc ? date.getUTCFullYear() : date.getFullYear();
    format = format.replace(/(^|[^\\])yyyy+/g, "$1" + y);
    format = format.replace(/(^|[^\\])yy/g, "$1" + y.toString().substr(2, 2));
    format = format.replace(/(^|[^\\])y/g, "$1" + y);

    var M = (utc ? date.getUTCMonth() : date.getMonth()) + 1;
    format = format.replace(/(^|[^\\])MMMM+/g, "$1" + MMMM[0]);
    format = format.replace(/(^|[^\\])MMM/g, "$1" + MMM[0]);
    format = format.replace(/(^|[^\\])MM/g, "$1" + ii(M));
    format = format.replace(/(^|[^\\])M/g, "$1" + M);

    var d = utc ? date.getUTCDate() : date.getDate();
    format = format.replace(/(^|[^\\])dddd+/g, "$1" + dddd[0]);
    format = format.replace(/(^|[^\\])ddd/g, "$1" + ddd[0]);
    format = format.replace(/(^|[^\\])dd/g, "$1" + ii(d));
    format = format.replace(/(^|[^\\])d/g, "$1" + d);

    var H = utc ? date.getUTCHours() : date.getHours();
    format = format.replace(/(^|[^\\])HH+/g, "$1" + ii(H));
    format = format.replace(/(^|[^\\])H/g, "$1" + H);

    var h = H > 12 ? H - 12 : H == 0 ? 12 : H;
    format = format.replace(/(^|[^\\])hh+/g, "$1" + ii(h));
    format = format.replace(/(^|[^\\])h/g, "$1" + h);

    var m = utc ? date.getUTCMinutes() : date.getMinutes();
    format = format.replace(/(^|[^\\])mm+/g, "$1" + ii(m));
    format = format.replace(/(^|[^\\])m/g, "$1" + m);

    var s = utc ? date.getUTCSeconds() : date.getSeconds();
    format = format.replace(/(^|[^\\])ss+/g, "$1" + ii(s));
    format = format.replace(/(^|[^\\])s/g, "$1" + s);

    var f = utc ? date.getUTCMilliseconds() : date.getMilliseconds();
    format = format.replace(/(^|[^\\])fff+/g, "$1" + ii(f, 3));
    f = Math.round(f / 10);
    format = format.replace(/(^|[^\\])ff/g, "$1" + ii(f));
    f = Math.round(f / 10);
    format = format.replace(/(^|[^\\])f/g, "$1" + f);

    var T = H < 12 ? "AM" : "PM";
    format = format.replace(/(^|[^\\])TT+/g, "$1" + T);
    format = format.replace(/(^|[^\\])T/g, "$1" + T.charAt(0));

    var t = T.toLowerCase();
    format = format.replace(/(^|[^\\])tt+/g, "$1" + t);
    format = format.replace(/(^|[^\\])t/g, "$1" + t.charAt(0));

    var tz = -date.getTimezoneOffset();
    var K = utc || !tz ? "Z" : tz > 0 ? "+" : "-";
    if (!utc) {
        tz = Math.abs(tz);
        var tzHrs = Math.floor(tz / 60);
        var tzMin = tz % 60;
        K += ii(tzHrs) + ":" + ii(tzMin);
    }
    format = format.replace(/(^|[^\\])K/g, "$1" + K);

    var day = (utc ? date.getUTCDay() : date.getDay()) + 1;
    format = format.replace(new RegExp(dddd[0], "g"), dddd[day]);
    format = format.replace(new RegExp(ddd[0], "g"), ddd[day]);

    format = format.replace(new RegExp(MMMM[0], "g"), MMMM[M]);
    format = format.replace(new RegExp(MMM[0], "g"), MMM[M]);

    format = format.replace(/\\(.)/g, "$1");

    return format;
};