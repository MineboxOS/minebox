var mug_url = "https://" + location.hostname + ":5000/";

window.onload = function() {
  fetchMUG("status", "GET", null,
     function(aResult) {
        var status = document.getElementById("status");
        if (aResult["backup_type"]) {
          if (aResult["minebd_storage_mounted"] && aResult["users_created"]) {
            status.textContent = "Active";
          }
          else if (aResult["minebd_storage_mounted"]) {
            status.textContent = "Minebox storage ready, but no users have been set up.";
          }
          else if (!aResult["minebd_running"]) {
            status.textContent = "Storage inactive, call support!";
          }
          else if (aResult["minebd_encrypted"]) {
            status.textContent = "Encryption set up, getting ready for use...";
          }
          else {
            status.textContent = "No encryption key yet, please proceed to setup.";
          }
          document.getElementById("cheight").textContent = aResult["consensus_height"];
          document.getElementById("csync").textContent = aResult["consensus_synced"] ? "Yes" : "No";
          document.getElementById("user").textContent = aResult["logged_in"] ? aResult["user"] : "Not logged in";
          document.getElementById("wbalance").textContent = aResult["wallet_confirmed_balance_sc"];
          document.getElementById("wunlocked").textContent = aResult["wallet_unlocked"];
          document.getElementById("siad").textContent = aResult["sia_daemon_running"] ? "active" : "stopped";
        }
        else {
          status.textContent = "Unknown (Server issue?)"
          console.log(aResult);
        }
      },
      {}
  );

  document.getElementById("keybtn").onclick = function() {
    fetchMUG("key", "PUT", "foobar",
      function(aResult) {
        document.getElementById("keyoutput").textContent = aResult["statusCode"] + (aResult["message"] ? ": " + aResult["message"] : "")
      },
      {}
    );
  };

  document.getElementById("setupbtn").onclick = function() {
    fetchRockstor("setup_user", "POST", "json", '{"username":"mbuser","password":"mypass","is_active":true}',
      function(aResult) {
        document.getElementById("setupoutput").textContent += " " + aResult["statusCode"]
        fetchRockstor("api/login", "POST", "form", 'username=mbuser&password=mypass',
          function(aResult) {
            document.getElementById("setupoutput").textContent += " " + aResult["statusCode"]
            fetchRockstor("api/appliances", "POST", "json", '{"hostname":"DemoMinebox","current_appliance":true}',
              function(aResult) {
                document.getElementById("setupoutput").textContent += " " + aResult["statusCode"]
              },
              {}
            );
          },
          {}
        );
      },
      {}
    );
  };
}

function fetchMUG(aEndpoint, aMethod, aSendData, aCallback, aCallbackForwards) {
  var XHR = new XMLHttpRequest();
  XHR.onreadystatechange = function() {
    if (XHR.readyState == 4) {
      // State says we are fully loaded.
      var result = {};
      if (XHR.getResponseHeader("Content-Type") == "application/json") {
        // Got a JSON object, see if we have success.
        result = JSON.parse(XHR.responseText);
      }
      else {
        result = {"success": false, "data": XHR.responseText};
      }
      result["statusCode"] = XHR.status;
      aCallback(result, aCallbackForwards);
    }
  };
  XHR.open(aMethod, mug_url + aEndpoint, true);
  XHR.withCredentials = "true";
  //XHR.setRequestHeader("Accept", "application/json");
  try {
    XHR.send(aSendData); // Send actual form data.
  }
  catch (e) {
    aCallback({"success": false, "statusCode": 500, "data": e}, aCallbackForwards);
  }
}

function fetchRockstor(aEndpoint, aMethod, aDataFormat, aSendData, aCallback, aCallbackForwards) {
  var XHR = new XMLHttpRequest();
  XHR.onreadystatechange = function() {
    if (XHR.readyState == 4) {
      // State says we are fully loaded.
      var result = {};
      if (XHR.getResponseHeader("Content-Type") == "application/json") {
        // Got a JSON object, see if we have success.
        result = JSON.parse(XHR.responseText);
      }
      else {
        result = {"success": true, "data": XHR.responseText};
      }
      result["statusCode"] = XHR.status;
      aCallback(result, aCallbackForwards);
    }
  };
  XHR.open(aMethod, "/" + aEndpoint, true);
  if (aDataFormat == "json") { XHR.setRequestHeader("Content-Type", "application/json"); }
  else if (aDataFormat == "form") { XHR.setRequestHeader("Content-Type", "application/x-www-form-urlencoded"); }
  //XHR.setRequestHeader("Accept", "application/json");
  csrftoken = getCookieValue("csrftoken");
  if (csrftoken) {
    XHR.setRequestHeader("X-CSRFToken", csrftoken);
  }
  XHR.withCredentials = "true";
  try {
    XHR.send(aSendData); // Send actual form data.
  }
  catch (e) {
    aCallback({"success": false, "statusCode": 500, "data": e}, aCallbackForwards);
  }
}

function getCookieValue(aCookieName) {
  var cmatch = document.cookie.match('(^|;)\\s*' + aCookieName + '\\s*=\\s*([^;]+)');
  return cmatch ? cmatch.pop() : '';
}
