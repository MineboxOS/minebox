function FileUploadManager() {

  var $uploadButton = $('#upload-button');
  var $realUploadButton = $('#upload');

  //handling upload button
  $uploadButton.on('click', function() {
    $realUploadButton.trigger('click');
  });
  $realUploadButton.bind('change', function() {
    uploadManager();
  });



  function uploadManager() {
    var files = {};
    function getSelected() {
      var $def = $('#filesystem-tree .volume:first-child>ul');
      var $sel = $('#filesystem-tree .selected');
      if ( $sel.length == 0 || $sel.attr('data-slug') == 'storage' ) {
        $sel = $def;
      } else {
        if ( $sel.prop('tagName') == 'LI' ) {
          $sel = $sel.children('ul');
        }
      }
      return $sel;
    }

    function getFilesData(callback_func) {
      function getExt(str) {
        var cut = str.lastIndexOf('.') + 1; //+1 removes also the "."
        return str.substring( cut, str.length );
      }
      var input = document.getElementById('upload');
      var data = [];
      for ( var n = 0; n < input.files.length; n++ ) {
        data[n] = {
          name: input.files[n].name,
          ext: getExt(input.files[n].name),
          size: input.files[n].size
        }
      }
      if (callback_func) {
        callback_func();
      }
      return data;
    }

    function getExtension( fileExt ) {
      known = ['png', 'pdf', 'mp3', 'mp4', 'mov', 'jpg','key','html','css','java','psd','ai','bmp','dwg','eps','tiff','ots','php','py','c','sql','rb','cpp','tga','dxf','doc','odt','xls','docx','ppt','asp','ics','dat','xml','yml','h','exe','avi','odp','dotx','xlsx','ods','pps','dot','txt','rtf','m4v','flv','mpg','quicktime','mid','3gp','aiff','aac','wav','zip','ott','tgz','dmg','iso','rar','gif'];

      for ( var n = 0; n < known.length; n++ ) {
        if ( fileExt == known[n] ) {
          return fileExt;
        }
      }

      return 'blank';
    }

    function uploadFiles() {
      var output = '';
      get.template(config.filesystem.templates.addresses.li, function(template) {
        for ( var n = 0; n < filesData.length; n++ ) {
          output += template;
          output = replaceAll(output, '{{id}}', getRandomString(15));
          output = replaceAll(output, '{{type}}', 'file');
          output = replaceAll(output, '{{status}}', 'uploading unchecked');
          output = replaceAll(output, '{{parent}}', $selected.attr('data-id'));
          output = replaceAll(output, '{{slug}}', filesData[n].size);
          output = replaceAll(output, '{{encrypted}}', 0);
          output = replaceAll(output, '{{icon}}', 'file-'+getExtension(filesData[n].ext));
          output = replaceAll(output, '{{name}}', filesData[n].name);
        }
        $selected.append(output);
        fakeUploader();
      }, function(err) {
        output = err;
      });
    }

    var $selected = getSelected();
    var filesData = getFilesData( uploadFiles );
  }


  /***************************/
  //fake uploader (temporal solution to pretend files are being uploaded)
  /***************************/
  var $pendingElements = null;
  var activeLoads = [];
  var refreshRate = 50;
  var uploadSpeed = 100; //bytes in ms



  function fakeUploader() {
    //trigger :hover
    $('#filesystem-uploading .action-button').addClass('hover');
    $pendingElements = $('.uploading.unchecked');
    var obj = {};
    for ( var n = 0; n < $pendingElements.length; n++ ) {
      $($pendingElements[n]).removeClass('unchecked');
      obj.id = $($pendingElements[n]).attr('data-id'); //storing id
      obj.element = $($pendingElements[n]); //storing matching element
      obj.bytes = 0; //already uploaded bytes
      obj.total = parseInt($($pendingElements[n]).attr('data-slug')); //data-slug stores file size (in bytes)
      obj.time = obj.total / uploadSpeed; //ms to upload the file
      obj.iterations = obj.time / refreshRate; //number of required to completely upload the file
      obj.increase = parseInt(obj.total / obj.iterations); //bytes that will be uploaded on each iteration (every refreshRate)
      obj.onUploadProgress = uploadProgress; //func to exec on each iteration
      obj.onUploadFinished = uploadFinished; //func to exec when upload has finished

      activeLoads.push( obj );
    }
    function uploadFinished() {
      var id = this.id;
      var index = null;
      for ( var n = 0; n < activeLoads.length; n++ ) {
        if ( activeLoads[n].id == id ) {
          index = n;
        }
      }
      activeLoads.splice(index, 1);
      $(this.element).removeClass('uploading').addClass('uploaded');
    }
    var temporalIncrease;
    function uploadProgress() {
      temporalIncrease = getRandomInt(-67, 8789);
      if ( this.bytes + (this.increase + temporalIncrease) >= this.total ) {
        this.bytes = this.total;
        this.onUploadFinished();
      } else {
        this.bytes += this.increase + temporalIncrease;
      }
    }
    fakeUploadingFiles();
  }



  function fakeUploadingFiles() {
    var fakeUploader = null;
    if ( !fakeUploader ) {
      fakeUploader = setInterval(function() {
        for ( var n = 0; n < activeLoads.length; n++ ) {
          activeLoads[n].onUploadProgress();
        }
        if ( activeLoads.length == 0 ) {
          clearInterval(fakeUploader);
        }
      }, refreshRate);
    }
  }



  function permanentUploadingChecker() {
    var interval = setInterval(function() {
      if ( activeLoads.length ) {
        $('files-uploading').fadeOut(300);
      } else {
        $('files-uploading').fadeIn(300);
      }
      stats();
    }, refreshRate);
    function stats() {
      var uploads = null;
      var bytes = null;
      var kbytes = null;
      var total = null;
      var percent = null;

      //updating active uploads
      uploads = activeLoads.length;

      //updating bytes & total
      for ( var n = 0; n < activeLoads.length; n++ ) {
        bytes += activeLoads[n].bytes;
        total += activeLoads[n].total;
      }

      if (!bytes) { bytes = 0; }
      if (!total) { total = 0; }

      kbytes = parseInt( bytes/1000 );
      total = parseInt(total/1000);

      //updating percent
      var ratio = total / kbytes;
      percent = 100 / ratio;
      percent = percent.toFixed(2);

      if ( !$.isNumeric(percent) ) {
        percent = 0;
      }


      $('#filesystem-uploading .number').html(uploads);
      $('#filesystem-uploading .size').html(kbytes + '/' + total);
      $('#filesystem-uploading .percent').html(percent);
      $('#filesystem-uploading .progress').css('width', percent + '%');

      if ( percent > 0 && percent < 100 ) {
        $('#filesystem-uploading').addClass('loading');
      } else {
        $('#filesystem-uploading').removeClass('loading');
      }

    }
  }


  //init loop for fake uploader
  permanentUploadingChecker();

  //add/remove hover class on #filesystem-uploading
  $('#filesystem-uploading .action-button').on('mouseover', function() {
    $(this).removeClass('hover');
  });
  $('#filesystem-uploading .action-button').on('click', function() {
    if ( $(this).hasClass('hover') ) {
      $(this).removeClass('hover');
    } else {
      $(this).addClass('hover');
    }
  });
}
