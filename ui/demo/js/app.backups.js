viewLoader.add('backups', backup);

//requires display();
function backup() {

  var CONFIG = {
    mug: {
      url: config.mug.url,
      requester: new Requester()
    },
    templates: {
      bars: '<div id="backup-{{backup_name}}" class="bar-box backup-status-{{backup_status}} backup-metadata-{{backup_metadata}}" data-name="{{backup_name}}" data-timestamp="{{snapshot_time}}" data-size="{{backup_size}}" data-relative-size="{{backup_relative_size}}" data-progress="{{backup_progress}}" data-relative-progress="{{backup_relative_progress}}"><div class="bar"></div></div>'
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
      $browserBars,
      numberOfBars,
      backupInterface = BackupInterface(CONFIG);


  function init() {
    //load backups
    loadBackups();
    //making .window draggable
    $scrollWindow.draggable({
      axis: 'x',
      containment: '#browser',
      drag: backupInterface.handleScroll
    });
  }


  function loadBackups() {
    //CONFIG.mug.requester.setURL( CONFIG.mug.url + '');
    CONFIG.mug.requester.setURL('json/backupStatus.json'); //temporal override
    CONFIG.mug.requester.setMethod('GET');
    CONFIG.mug.requester.run(function(response) {
      backupInterface.print(response);
      backupInterface.build();
    }, function(error) {
      console.log(error);
    });
  }


  function BackupInterface(cfg) {

    //vars
    var printingTempHTMLString,
        graphContainerWidth,
        windowScrollRatio,
        scrollingWindowData = {};

    /* 
     * FUNCTIONS FOR PRINTING
     * Functions that actually prints the html by given an array
     */ 
    //printing bars ( array, destination )
    function printBars( array ) {
      //printing each iteration
      for ( var n = 0; n < array.length; n++ ) {
        //preparing HTML string
        printingTempHTMLString = cfg.templates.bars;
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_name}}', array[n].name );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_status}}', array[n].status.toLowerCase() );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_metadata}}', array[n].metadata.toLowerCase() );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_size}}', array[n].size );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_relative_size}}', array[n].relative_size );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_progress}}', array[n].progress );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{backup_relative_progress}}', array[n].relative_progress );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_time}}', array[n].time_snapshot );
        //printing in graph
        $cumulativeFiles.append( printingTempHTMLString );
        //printing en browser
        $browserBarsBox.append( printingTempHTMLString );
      }
      //once everything is printed, update selection elements
      updateBarsSelectors();
    }

    /* 
     * FUNCTIONS FOR BUILD
     * Functions that build windows, containers and bars width
     */ 
    //gathering functionalities using common sense
    function build() {
      setGraphContainerWidth();
      setWindowScrollerWidth();
      setGraphBarsWidth();
      setBrowserBarsWidth();
    }

          //select bars
          function updateBarsSelectors() {
            //updating elements
            $graphBars = $('#graph .bar-box');
            $browserBars = $('#browser-bars .bar-box');
          }

          //get bar numbers by screen width
          function getBarsByScreen() {
            return cfg.bars[ display() ];
          }

          //set graph bars width -> called on init and on resize
          function setGraphBarsWidth() {
            //the width is the result of dividing available space (100%) between the number of bars
            //init var for preventing bars to be wider than they should in case there are less bars than barsByScreen()
            var barsTotal;
            if ( $graphBars.length < getBarsByScreen() ) {
              barsTotal = getBarsByScreen();
            } else {
              barsTotal = $graphBars.length;
            }
            //setting its width
            $graphBars.css( 'width', 100 / barsTotal + '%' );
          }

          //set browser bars width -> called on init and on resize
          function setBrowserBarsWidth() {
            //the width is the result of dividing available space (100%) between the number of bars
            $browserBars.css( 'width', 100 / $browserBars.length + '%' );
          }

          //set graph container width
          function setGraphContainerWidth() {
            //getting the number of bars, divinding it by barsByScreen and multiplying the result by 100, we will get the percent scale of current screen size (#graphContainer's parent is same width as screen)
            //if happens that we get as a result less than 100%, we will force it to be at least 100% width of its parent
            graphContainerWidth = ( $graphBars.length / getBarsByScreen() ) * 100;
            if ( graphContainerWidth < 100 ) {
              graphContainerWidth = 100;
            }
            //setting the percent width
            $graphContainer.css( 'width', graphContainerWidth + '%' );
          }

          //set window scroller width
          function setWindowScrollerWidth() {
            //getting the ratio for visible bars within the screen
            windowScrollRatio = $graphBars.length / getBarsByScreen();
            //preventing ratio to go under 1, so scrollWindow never goes above 100%
            if ( windowScrollRatio < 1 ) {
              windowScrollRatio = 1;
            }
            //setting the percent
            $scrollWindow.css( 'width', 100 / windowScrollRatio + '%' );
          }


    /*
     * FUNCTIONS FOR NAVIGATION
     * Functions that handle window position, buttons pressed...
     */
          //handle drag and drop in scrolling window
          function handleScrollingWindow() {
            scrollingWindowData.leftPos = $scrollWindow.offset().left;
            scrollingWindowData.offset = $scrollWindow.width() / 2;
            scrollingWindowData.minimum = scrollingWindowData.offset;
            scrollingWindowData.maximum = window.innerWidth - scrollingWindowData.offset;
            scrollingWindowData.total = scrollingWindowData.maximum - scrollingWindowData.minimum;
            scrollingWindowData.percent = ( 100 - ( 100 / ( scrollingWindowData.total / scrollingWindowData.leftPos ) ) );
            $graphContainer.css({
              'transform': 'translateX(' + scrollingWindowData.percent + '%)',
              'margin-right': ( scrollingWindowData.percent * window.innerWidth ) / 100 + 'px'
            });
          }


    return {
      build: build,
      print: printBars,
      handleScroll: handleScrollingWindow
    }

  }

  $(document).ready(function() {
    init();
  });

  $(window).resize(function() {
    backupInterface.build();
  });

}
