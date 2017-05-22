var config = {
  fullHeight: {
    exclude: [
      '#header'
    ]
  },
  widgets: {
    containerId: '#widgets-box',
    rootFolder: 'templates/widgets/',
    map: {
      disks: {
        file: 'disks.html'
      },
      backup: {
        file: 'backups.html'
      },
      resources: {
        file: 'resources.html'
      }
    },
    layout: {
      containerId: '#widgets-box',
      packery: {
        itemSelector: '.widget',
        gutter: 0
      }
    },
    attr: {
      widget: 'data-menu-button', //this attribute will be automatically generated and filled in the widget when spawning it. It will store the same data as config.widgets.attr.button for clicked button
      button: 'data-load' //sidebar buttons must have this attr properly filled in order to know which is the data to load. the string recovered in here will be matched with the info within config.widgets.map
    },
    updating: {}
  },
  views: {
    path: 'views/',
    interval: 200, //default 200
    containerID: 'wrapper',
    map: {
      'dashboard': 'dashboard.html',
      'wallet': 'wallet.html',
      'backups': 'backups.html',
      'disks': 'disks.html',
      'contracts': 'contracts.html',
      'plugins': 'plugins.html',
      'filesystem': 'filesystem.html',
      'donate': 'donate.html'
    },
    buttons: {
      class: 'active', //string that will be added to buttons to set them active
      selector: '#menu li', //string to jQuery-select button elements
      attribute: 'data-go' //attribute that holds the id of the content that view.buttons must show
    },
    messages: {
      fail: 'Something went wrong. Please try again.'
    }
  },
  wallet: {
    sidebarContainerID: 'sidebar-coin-iteration',
    containerID: 'content',
    linkClass: 'wallet-link',
    messages: {
      fail: 'Content loading failed. Try again later.'
    }
  },
  backup: {
    templates: {
      path: 'templates/backups/',
      files: {
        statusIdle: 'backup-status-idle.html',
        statusLive: 'backup-status-live.html'
      }
    }
  },
  tabs: {
    button: {
      selector: '.tab-buttons li',
      attr: 'data-tab'
    },
    tab: {
      selector: '.tab',
      class: 'tab',
      prefix: 'tab',
      containerSelector: '.tabs-container'
    },
    activeClass: 'active'
  },
  backups: {
    snapshots: {
      firstLoad: 13, //n of snapshots that will be loaded when document is loaded
      olderLoad: 12, //n of snapshots to load when panning to the right
      method: 'owl', //whether you want to append, prepend or use native's owl carousel method (append will add them at right, prepend at left)
      carousel: {
        pagination: false,
        navigation: false,
        items: 12,
        itemsDesktop: 10,
        itemsDesktopSmall: 8,
        itemsTablet: 6,
        itemsTabletSmall: 4,
        itemsMobile: 2,
        addClassActive: true
      }
    },
    templates: {
      path: 'templates/backups/',
      files: {
        globalStatus: 'backup-status-live.html',
        snapshotsIteration: 'backup-snapshot-iteration.html'
      }
    }
  },
  patternLock: {
    margin: 5,
    radius: 10
  },
  filesystem: {
    templates: {
      addresses: {
        li: 'templates/filesystem/li.html',
        ul: 'templates/filesystem/ul.html'
      }
    }
  }
};
