function genVars(number) {
  var snapshotPath = "/mnt2/cifs-0/.snapshot/";
  var d = new Date();
  var currentTimestamp = d.getTime();
  var objSnap;
  var objProg;
  var id;
  var hour = 3600 * 1000;
  var day = hour * 24;
  var rest;

  SNAPSHOTS = [];
  PROGRESSES = [];

  for ( var n = 0; n < number; n++ ) {
    rest = (day * n) + (getRandomInt(0,23) * hour) + getRandomInt(0,999);
    d = new Date(currentTimestamp - rest);
    id = d.getFullYear() + '' + make2Digit(d.getMonth()+1) + '' +  make2Digit(d.getDate()) + 'T' + getRandomInt(100000, 999999) + 'Z';
    objSnap = {
      id: id,
      snapshot: snapshotPath + id,
      size: getRandomInt(10000, 80000),
      files: getRandomInt(100, 240),
      date: d.getTime()
    };
    objProg = {
      id: id,
      progress: 100
    }
    SNAPSHOTS.push(objSnap);
    PROGRESSES.push(objProg);
  }
}


var SNAPSHOTS = [{"id":"20160920T648402Z","snapshot":"/mnt2/cifs-0/.snapshot/20160920T648402Z","size":59868,"files":172,"date":1474402797958},{"id":"20160920T876397Z","snapshot":"/mnt2/cifs-0/.snapshot/20160920T876397Z","size":74850,"files":142,"date":1474327198876},{"id":"20160918T216363Z","snapshot":"/mnt2/cifs-0/.snapshot/20160918T216363Z","size":21520,"files":190,"date":1474233598593},{"id":"20160918T332099Z","snapshot":"/mnt2/cifs-0/.snapshot/20160918T332099Z","size":38436,"files":120,"date":1474161598509},{"id":"20160916T401960Z","snapshot":"/mnt2/cifs-0/.snapshot/20160916T401960Z","size":19586,"files":240,"date":1474028398385},{"id":"20160916T177190Z","snapshot":"/mnt2/cifs-0/.snapshot/20160916T177190Z","size":70437,"files":153,"date":1474003198000},{"id":"20160914T972369Z","snapshot":"/mnt2/cifs-0/.snapshot/20160914T972369Z","size":45507,"files":151,"date":1473880797996},{"id":"20160914T435631Z","snapshot":"/mnt2/cifs-0/.snapshot/20160914T435631Z","size":23919,"files":147,"date":1473823198072},{"id":"20160913T911529Z","snapshot":"/mnt2/cifs-0/.snapshot/20160913T911529Z","size":76468,"files":169,"date":1473751198748},{"id":"20160911T605869Z","snapshot":"/mnt2/cifs-0/.snapshot/20160911T605869Z","size":26882,"files":111,"date":1473625198179},{"id":"20160911T534686Z","snapshot":"/mnt2/cifs-0/.snapshot/20160911T534686Z","size":21083,"files":119,"date":1473585597916},{"id":"20160909T821340Z","snapshot":"/mnt2/cifs-0/.snapshot/20160909T821340Z","size":27171,"files":140,"date":1473452398589},{"id":"20160908T176360Z","snapshot":"/mnt2/cifs-0/.snapshot/20160908T176360Z","size":12181,"files":229,"date":1473355198881},{"id":"20160907T967620Z","snapshot":"/mnt2/cifs-0/.snapshot/20160907T967620Z","size":14123,"files":146,"date":1473268798366},{"id":"20160907T820373Z","snapshot":"/mnt2/cifs-0/.snapshot/20160907T820373Z","size":42010,"files":107,"date":1473207598001},{"id":"20160905T465129Z","snapshot":"/mnt2/cifs-0/.snapshot/20160905T465129Z","size":11909,"files":121,"date":1473103198765},{"id":"20160905T227350Z","snapshot":"/mnt2/cifs-0/.snapshot/20160905T227350Z","size":56703,"files":207,"date":1473049198884},{"id":"20160903T537824Z","snapshot":"/mnt2/cifs-0/.snapshot/20160903T537824Z","size":52046,"files":118,"date":1472919598409},{"id":"20160902T716551Z","snapshot":"/mnt2/cifs-0/.snapshot/20160902T716551Z","size":44364,"files":123,"date":1472825998070},{"id":"20160902T631794Z","snapshot":"/mnt2/cifs-0/.snapshot/20160902T631794Z","size":22875,"files":175,"date":1472797198407},{"id":"20160901T633156Z","snapshot":"/mnt2/cifs-0/.snapshot/20160901T633156Z","size":18238,"files":227,"date":1472714398570},{"id":"20160831T584775Z","snapshot":"/mnt2/cifs-0/.snapshot/20160831T584775Z","size":60226,"files":175,"date":1472631598683},{"id":"20160829T223503Z","snapshot":"/mnt2/cifs-0/.snapshot/20160829T223503Z","size":75536,"files":213,"date":1472505598785},{"id":"20160829T591398Z","snapshot":"/mnt2/cifs-0/.snapshot/20160829T591398Z","size":58586,"files":240,"date":1472422798124}];
var PROGRESSES = [{"id":"20160920T648402Z","progress":100},{"id":"20160920T876397Z","progress":100},{"id":"20160918T216363Z","progress":100},{"id":"20160918T332099Z","progress":100},{"id":"20160916T401960Z","progress":100},{"id":"20160916T177190Z","progress":100},{"id":"20160914T972369Z","progress":100},{"id":"20160914T435631Z","progress":100},{"id":"20160913T911529Z","progress":100},{"id":"20160911T605869Z","progress":100},{"id":"20160911T534686Z","progress":100},{"id":"20160909T821340Z","progress":100},{"id":"20160908T176360Z","progress":100},{"id":"20160907T967620Z","progress":100},{"id":"20160907T820373Z","progress":100},{"id":"20160905T465129Z","progress":100},{"id":"20160905T227350Z","progress":100},{"id":"20160903T537824Z","progress":100},{"id":"20160902T716551Z","progress":100},{"id":"20160902T631794Z","progress":100},{"id":"20160901T633156Z","progress":100},{"id":"20160831T584775Z","progress":100},{"id":"20160829T223503Z","progress":100},{"id":"20160829T591398Z","progress":100}];

/*
genVars(24);
console.log( 'var SNAPSHOTS = ' + JSON.stringify(SNAPSHOTS) );
console.log( 'var PROGRESSES = ' + JSON.stringify(PROGRESSES) );
*/


function getSnapshots(callback) {
    return window.setTimeout(function () {
        return callback(SNAPSHOTS);
    }, 100);
};




function createSnapshot(callback) {
    var id = '20160920T' + Math.floor(100000 * Math.random()) + 'Z';
    var snapshot = '/mnt2/cifs-0/.snapshot/' + id;
    var d = new Date();
    var obj = {
      id: id,
      snapshot: snapshot,
      size: getRandomInt(),
      files: Math.floor(100 * Math.random()),
      date: d.getTime()
    };
    SNAPSHOTS.push(obj);
    PROGRESSES.push({id: id, progress: 0});
    return window.setTimeout(function () {
        return callback(obj);
    }, 100);
};
/*function uploadSnapshot(id, callback) {
    return window.setTimeout(function () {
        return callback(true);
    }, 100);
};*/




function snapshotUploadProgress(id, success, fail) {
  for ( var n = 0 ; n < PROGRESSES.length; n++ ) {
    if ( PROGRESSES[n].id == id ) {
      if(PROGRESSES[n].progress<100){PROGRESSES[n].progress += 5};
      success(PROGRESSES[n].progress);
      return true;
    }
  }
  //fallback if id was not found
  fail();
}




function getFiles(callback) {
    return window.setTimeout(function () {
        return callback([]);
    }, 100);
};




function getContracts(callback) {
    return window.setTimeout(function () {
        return callback(['contract-0', 'contract-1', 'contract-2', 'contract-3','contract-0', 'contract-1', 'contract-2', 'contract-3','contract-0', 'contract-1', 'contract-2', 'contract-3','contract-0', 'contract-1', 'contract-2', 'contract-3']);
    }, 100);
};




function getContract(id, callback) {
    return window.setTimeout(function () {
        return callback({ "id": getRandomString(10),
                          "fileSizeBytes": getRandomInt(1000,10000),
                          "maxDurationWeeks": getRandomInt(3,8),
                          "startingTime": getRandomInt( 1474462173201, new Date().getTime() ),
                          "collateralPerTBMonthSC": getRandomInt(100,200),
                          "pricePerTBPerMonthSC": getRandomInt(800, 1700),
                          "bandwidthPriceSC": getRandomInt(200,300)
                        });
    }, 100);
};
