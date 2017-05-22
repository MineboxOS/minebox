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
