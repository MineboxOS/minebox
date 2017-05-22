viewLoader.add('donate', Donate);

function Donate() {

  var $donateButton = $('#donate-button'),
      $cancelDonation = $('.cancel-donation'),
      status = {
        used: 35,
        notused: 65,
        donated: 0,
        totalDonators: 196,
        totalDonatedCapacity: '200TB',
        totalSavingMoney: '6000€',
        currentlyDonating: '0B'
      };

  $donateButton.on('click', function() {
    if ( !$(this).hasClass('disabled') ) {
      donate();
    }
  });

  $cancelDonation.on('click', function() {
    r = window.confirm('Are you sure you want to cancel your donation?');
    if ( r ) {
      reverseDonation();
    }
  });

  function fill() {
    $('.hdd .used .value').html(status.used);
    $('.hdd .used').css('width', status.used + '%');
    $('.hdd .not-used .value').html(status.notused);
    $('.hdd .not-used').css('width', status.notused + '%');
    $('.hdd .donation .value').html(status.donated);
    $('.hdd .donation').css('width', status.donated + '%');
    $('.resume .total-donators').html(status.totalDonators);
    $('.resume .total-donated-capacity').html(status.totalDonatedCapacity);
    $('.resume .total-saving').html(status.totalSavingMoney);
    $('.resume .donating-value').html(status.currentlyDonating);
  }
  fill();

  function donate() {
    //disabling donate button
    $donateButton.addClass('disabled clicked');
    //modifying #donate class
    $('#donate').removeClass('not-donator').addClass('donator');
    //adding cookie
    //$.cookie();
    //update data
    status.notused = 60;
    status.donated = 5;
    status.totalDonators += 1;
    status.totalDonatedCapacity = '200.6TB';
    status.totalSavingMoney = '6150€';
    status.currentlyDonating = '60GB';
    fill();
  }

  function reverseDonation() {
    //disabling donate button
    $donateButton.removeClass('disabled clicked');
    //modifying #donate class
    $('#donate').addClass('not-donator').removeClass('donator');
    //adding cookie
    //$.cookie();
    //update data
    status.notused = 65;
    status.donated = 0;
    status.totalDonators -= 1;
    status.totalDonatedCapacity = '200TB';
    status.totalSavingMoney = '6000€';
    status.currentlyDonating = '0GB';
    fill();
  }


}
