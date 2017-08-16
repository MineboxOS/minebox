viewLoader.add('snapshots', Snapshot);

//requires display();
function Snapshot() {

  var CONFIG = {
    mug: {
      url: config.mug.url,
      requester: new Requester()
    },
    templates: {
      bars: '<div id="snapshot-{{snapshot_name}}" class="bar-box snapshot-status-{{snapshot_status}} snapshot-metadata-{{snapshot_metadata}}" data-name="{{snapshot_name}}" data-timestamp="{{snapshot_time}}" data-size="{{snapshot_size}}" data-relative-size="{{snapshot_relative_size}}" data-progress="{{snapshot_progress}}" data-relative-progress="{{snapshot_relative_progress}}"><div class="bar"></div><div class="info"><p class="date"></p><p class="name"></p><p class="size"></p></div></div>'
    },
    bars: {
      desktop: 16,
      laptop: 12,
      tablet: 8,
      phone: 4
    },
    snapshotsList: [],
    snapshots: [],
    messages: {
      loadSnapshots: {
        fail: 'We couldn\'t get your backup list. Try again in a few minutes.'
      },
      loadSpecificSnapshots: {
        fail: 'We couldn\'t retrieve data from current backups. Try again later.'
      }
    },
    events: {
      snapshotsLoaded: 'snapshotsLoaded'
    }
  };

  var $cumulativeFiles = $('#cumulative-files'),
      $timeline = $('#timeline'),
      $timelineBarsBox = $('#timeline-bars'),
      $graph = $('#graph'),
      $graphContainer = $('#graph-inner'),
      $scrollWindow = $('#timeline .window'),
      $panelButtons = $('.panel-button'),
      $newSnapshotButton = $('#new-snapshot-button'),
      $sizeScale = $('#size-scale'),
      $graphBars,
      $timelineBars,
      numberOfBars,
      snapshotInterface = snapshotInterface(CONFIG);


  function init() {
    //load snapshots
    loadsnapshots();
    //making .window draggable
    $scrollWindow.draggable({
      axis: 'x',
      containment: '#timeline'
    }).bind('drag', snapshotInterface.handleScroll);
  }


  function loadsnapshots() {


    (function init() {

      //start loading
      loadingManager.add('Snapshots list');

      //loading all backup names
      CONFIG.mug.requester.setURL( CONFIG.mug.url + 'backup/list');
      CONFIG.mug.requester.setMethod('GET');
      //CONFIG.mug.requester.setType('JSON');
      CONFIG.mug.requester.setCache(false);
      CONFIG.mug.requester.setCredentials(true);
      CONFIG.mug.requester.run(function(response) {

        //saving snapshotsList array
        CONFIG.snapshotsList = response;

        //temporal var
        var loadedSnapshots = 0;
        //iterating through snapshotsList and calling to the server on each iteration to get their info
        for ( var n = 0; n < CONFIG.snapshotsList.length; n++ ) {
          loadSnapshotInfo( CONFIG.snapshotsList[n], function(response) {
            //store snapshot info
            CONFIG.snapshots.push( response );

            //increase loadedSnapshots witness
            loadedSnapshots++;
            if ( loadedSnapshots == CONFIG.snapshotsList.length ) {
              //rise event when all the snapshots' info is loaded
              $('body').trigger(CONFIG.events.snapshotsLoaded);
            }
            
          });
        }

        //stop loading
        loadingManager.remove('Snapshots list');

      }, function(error) {

        //stop loading
        loadingManager.remove('Snapshots list');

        //printing message
        var notify = new Notify({ message: CONFIG.messages.loadSnapshots.fail });
        notify.print();

      });
    }());


    function loadSnapshotInfo(snapshotName, cb) {

      //start loading
      loadingManager.add('Snapshot info #' + snapshotName);

      CONFIG.mug.requester.setURL( CONFIG.mug.url + 'backup/' + snapshotName + '/status');
      CONFIG.mug.requester.setMethod('GET');
      CONFIG.mug.requester.setType('JSON');
      CONFIG.mug.requester.setCache(false);
      CONFIG.mug.requester.run(function(response) {

        //stop loading
        loadingManager.remove('Snapshot info #' + snapshotName);

        cb(response);

      }, function(error) {

        //stop loading
        loadingManager.remove('Snapshot info #' + snapshotName);

        //printing message
        var notify = new Notify({ message: CONFIG.messages.loadSpecificSnapshots.fail });
        notify.print();

      });
    }


    $('body').bind(CONFIG.events.snapshotsLoaded, function() {
        //once snapshots array is filled with info, print them and build the interface
        snapshotInterface.print(CONFIG.snapshots);
        snapshotInterface.build();
    });

  }


  function snapshotInterface(cfg) {

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
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_name}}', array[n].name );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_status}}', array[n].status.toLowerCase() );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_metadata}}', array[n].metadata.toLowerCase() );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_size}}', array[n].size );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_relative_size}}', array[n].relative_size );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_progress}}', array[n].progress );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_relative_progress}}', array[n].relative_progress );
        printingTempHTMLString = replaceAll( printingTempHTMLString, '{{snapshot_time}}', array[n].time_snapshot );
        //printing in graph
        $cumulativeFiles.append( printingTempHTMLString );
        //printing en timeline
        $timelineBarsBox.append( printingTempHTMLString );
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
      setTimelineBarsWidth();
    }

          //select bars
          function updateBarsSelectors() {
            //updating elements
            $graphBars = $('#graph .bar-box');
            $timelineBars = $('#timeline-bars .bar-box');
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
            //setting bars height & info
            renderVisible();
          }

          //set timeline bars width -> called on init and on resize
          function setTimelineBarsWidth() {
            //the width is the result of dividing available space (100%) between the number of bars
            $timelineBars.css( 'width', 100 / $timelineBars.length + '%' );
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

          function renderVisible() {
            renderGraphBarsHeight(getVisible());
            renderTimelineBarsHeight();
          }

          function renderGraphBarsHeight($visible) {
            //init maximum var
            var maximum = 0;
            var minimum = parseInt( $($visible[0]).attr('data-size') ); //giving it a value to start with
            //iterating through visible to know the maximum and minimum
            for ( var n = 0; n < $visible.length; n++ ) {
              if ( parseInt( $($visible[n]).attr('data-size') ) > maximum ) {
                maximum = parseInt( $($visible[n]).attr('data-size') );
              }
              if ( parseInt( $($visible[n]).attr('data-size') ) < minimum ) {
                minimum = parseInt( $($visible[n]).attr('data-size') );
              }
            }
            
            //iterating through visible again to apply the height and fill the data
            for ( var n = 0; n < $visible.length; n++ ) {
              $($visible[n]).find('.bar').css('height', ( logScale(minimum, maximum, $($visible[n]).attr('data-size') ) ) + '%' );
              printData( $($visible[n]) );
            }

            //adjust size scale values
            adjustSizeScaleValues( maximum );
          }

          var rendering = null;
          function renderTimelineBarsHeight() {
            if ( rendering ) {
              clearTimeout(rendering);
              rendering = null;
            }
            rendering = setTimeout(function() {
              //init maximum var
              var maximum = 0;
              var minimum = parseInt( $($timelineBars[0]).attr('data-size') ); //giving it a value to start with
              //iterating through visible to know the maximum and minimum
              for ( var n = 0; n < $timelineBars.length; n++ ) {
                if ( parseInt( $($timelineBars[n]).attr('data-size') ) > maximum ) {
                  maximum = parseInt( $($timelineBars[n]).attr('data-size') );
                }
                if ( parseInt( $($timelineBars[n]).attr('data-size') ) < minimum ) {
                  minimum = parseInt( $($timelineBars[n]).attr('data-size') );
                }
              }

              //iterating through visible again to apply the maximum
              for ( var n = 0; n < $timelineBars.length; n++ ) {
                $($timelineBars[n]).find('.bar').css('height', ( logScale(minimum, maximum, $($timelineBars[n]).attr('data-size') ) ) + '%' );
              }
            }, 600);
          }

          function logScale(min,max,size) {
            //making size a integer
            size = parseInt( size );
            //Position will be between min and max
            var minp = min;
            var maxp = max;

            //The result should be between 0 an 100
            var minv = Math.log(10);
            var maxv = Math.log(100);

            //Calculate adjustment factor
            var scale = (maxv-minv) / (maxp-minp);

            return Math.exp(minv + scale*(size-minp));
          }

          function printData($element) {
            var d = new Date( parseInt($element.attr('data-timestamp')) );
            var info = {
              date: d.getDate() + '/' + (d.getMonth() + 1) + '/' + d.getFullYear(),
              name: '#' + $element.attr('data-name'),
              size: ( parseInt( $element.attr('data-size') ) / 1000000 ) + 'MB'
            };
            $element.find('.date').html( info.date );
            $element.find('.name').html( info.name );
            $element.find('.size').html( info.size );
          }

          function adjustSizeScaleValues( maximum ) {
            $sizeScale.find('.fourty').html( ( ( maximum * 40 ) / 100 ) / 1000000 + 'MB' );
            $sizeScale.find('.sixty').html( ( ( maximum * 60 ) / 100 ) / 1000000 + 'MB' );
            $sizeScale.find('.seventy').html( ( ( maximum * 70 ) / 100 ) / 1000000 + 'MB' );
            $sizeScale.find('.seventyfive').html( ( ( maximum * 75 ) / 100 ) / 1000000 + 'MB' );
          }

          function takeNewSnapshot() {
            //ask to the server
            //on return..
            var d = new Date();
            var array = [{
              name: getRandomString(10),
              status: 'uploading',
              metadata: 'uploading',
              size: 4000000000,
              relative_size: 40000000,
              progress: 95,
              relative_progress: 0,
              time_snapshot: d.getTime()
            }];

            printBars(array);
            build();
          }



    /*
     * FUNCTIONS FOR NAVIGATION
     * Functions that handle window position
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
            renderVisible();
          }

          function getVisible() {
            var arrayOfVisible = [];
            var cache = {
              timelineWindowPosition: $scrollWindow.offset().left,
              timelineWindowWidth: $scrollWindow.width(),
              timelineBarsWidth: $($timelineBars[0]).width()
            };

            for ( var n = 0; n < $timelineBars.length; n++ ) {
              if ( $($timelineBars[n]).offset().left > cache.timelineWindowPosition - cache.timelineBarsWidth ) {
                if ( $($timelineBars[n]).offset().left < cache.timelineWindowPosition + cache.timelineWindowWidth + cache.timelineBarsWidth ) {
                  arrayOfVisible.push( $($graphBars[n]) );
                }
              }
            }
            return arrayOfVisible;
          }

          //handle click and point in scrolling window
          var clickAndPointData = {};
          $timelineBarsBox.on('click', function(e) {
            //preventing event to go deeper on DOM
            e.stopPropagation();
            //storing clicked point and offset
            clickAndPointData.offset = $scrollWindow.width() / 2;
            clickAndPointData.clicked = e.pageX;
            //preventing "click" out of the margin area
            if ( clickAndPointData.clicked < clickAndPointData.offset ) {
              //if clicked too on the left
              clickAndPointData.go = 0;
            } else if ( clickAndPointData.clicked > window.innerWidth - clickAndPointData.offset ) {
              //if clicked too on the right
              clickAndPointData.go = (window.innerWidth - clickAndPointData.offset) - clickAndPointData.offset;
            } else {
              //if clicked within the margins
              clickAndPointData.go = clickAndPointData.clicked - clickAndPointData.offset;
            }
            //moving
            $scrollWindow.css('left', clickAndPointData.go).trigger('drag');
          });


    /*
     * FUNCTIONS FOR PANEL
     * Functions that handle button's panel activity
     */
          //handle button .activity
          $panelButtons.on('click', function() {
            if ( $(this).attr('id') != 'new-snapshot-button' ) {
              if ( $(this).hasClass('active') ) {
                $(this).removeClass('active');
              } else {
                $(this).addClass('active');
              }
            }
            buttonClicked( $(this) );
            $(this).blur();
          });

          $newSnapshotButton.on('click', function() {
            takeNewSnapshot();
          });

          function buttonClicked( $clicked ) {
            if ( $clicked.attr('id') == 'display-size-scale-button' ) {
              displaySizeScale();
            } else if ( $clicked.attr('id') == 'navigator-button' ) {
              displayTimeline();
            }
          }

          function displaySizeScale() {
            if ( $sizeScale.hasClass('active') ) {
              $sizeScale.removeClass('active');
            } else {
              $sizeScale.addClass('active');
            }
          }

          function displayTimeline() {
            if ( $timeline.hasClass('active') ) {
              $timeline.removeClass('active');
              $graph.addClass('expand');
            } else {
              $timeline.addClass('active');
              $graph.removeClass('expand');
            }
          }


    /*
     * FUNCTIONS FOR BARS INTERACTION
     * Functions that handle bars activity
     */
          //handle .bar events
          $('body').on('click', '#graph .bar-box', function() {
            //only if clicked bar is not active already
            if ( !$(this).hasClass('active') ) {
              //removing class on all bars
              $('#graph .bar-box').removeClass('active');
              //add class to clicked
              $(this).addClass('active');
            }
          });


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
    snapshotInterface.build();
    snapshotInterface.handleScroll();
  });

}
