viewLoader.add('backups', backup);

//requires display();
function backup() {

  var CONFIG = {
    mug: {
      url: config.mug.url,
      requester: new Requester()
    },
    templates: {
      bars: '<div id="backup-{{backup_name}}" class="bar-box backup-status-{{backup_status}} backup-metadata-{{backup_metadata}}" data-name="{{backup_name}}" data-timestamp="{{snapshot_time}}" data-size="{{backup_size}}" data-relative-size="{{backup_relative_size}}" data-progress="{{backup_progres}}" data-relative-progress="{{backup_relative_progress}}"><div class="bar"></div></div>'
    },
    bars: {
      desktop: 16,
      laptop: 12,
      tablet: 8,
      phone: 4
    }
  };

  var $cumulativeFiles = $('#cumulative-files'),
      $browserBarsBox = $('#browser-bars'),
      $graphContainer = $('#graph-inner'),
      $scrollWindow = $('#browser .window'),
      $graphBars,
      $browserBars;


  function init() {
    //load backups
    loadBackups();
    //making .window draggable
    $scrollWindow.draggable({
      axis: 'x',
      containment: '#browser',
      drag: handleScroll
    });
  }


  function loadBackups() {
    //CONFIG.mug.requester.setURL('');
    CONFIG.mug.requester.setURL('json/backupStatus.json'); //temporal override
    CONFIG.mug.requester.setMethod('GET');
    CONFIG.mug.requester.run(function(response) {
      printResponseArray(response);
    }, function(error) {
      console.log(error);
    });
  }


  function printResponseArray(array) {
    //printing each iteration
    for ( var n = 0; n < array.length; n++ ) {
      printBar( array[n] );
    }
    //building bars once they are printed
    buildBars();
  }

  //temporal variable to store html string
  var tempHTMLString;
  function printBar(obj) {

    tempHTMLString = CONFIG.templates.bars;
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_name}}', obj.name );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_status}}', obj.status.toLowerCase() );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_metadata}}', obj.metadata.toLowerCase() );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_size}}', obj.size );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_relative_size}}', obj.relative_size );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_progress}}', obj.progress );
    tempHTMLString = replaceAll( tempHTMLString, '{{backup_relative_progress}}', obj.relative_progress );
    tempHTMLString = replaceAll( tempHTMLString, '{{snapshot_time}}', obj.time_snapshot );

    //printing in graph
    $cumulativeFiles.append( tempHTMLString );

    //printing en browser
    $browserBarsBox.append( tempHTMLString );
  }

  function buildBars() {
    //updating elements
    $graphBars = $('#graph .bar-box');
    $browserBars = $('#browser-bars .bar-box');
    //setting width of containers
    buildBarsContainers();
    //setting width of bars
    setBarsWidth();
  }

  function buildBarsContainers() {
    //setting graph-inner width
    $graphContainer.width( ( $graphBars.length / getBarsByScreen() ) * window.innerWidth );
    //setting scroll window width
    $scrollWindow.width( 100 / ( $graphBars.length / getBarsByScreen() ) + '%' );
  }


  function getBarsByScreen() {
    return CONFIG.bars[ display() ];
  }

  function setBarsWidth() {
    //setting width of graph bars
    $graphBars.css('width', ( 100 / $graphBars.length ) + '%');
    //setting width of browser bars
    $browserBars.css('width', ( 100 / $browserBars.length ) + '%');
  }

  var leftPos, offset, minimum, maximum, total;
  function handleScroll() {
    leftPos = $scrollWindow.offset().left;
    offset = $scrollWindow.width() / 2;
    minimum = offset;
    maximum = innerWidth - offset;
    total = maximum - minimum;
    $graphContainer.css( 'transform', 'translateX(' + ( 100 - ( 100 / ( total / leftPos ) ) ) + '%)' );
    console.log( ( 100 - ( 100 / ( total / leftPos ) ) ) )
  }



  $(document).ready(function() {
    init();
  });

  $(window).resize(function() {
    buildBars();
  });

}
