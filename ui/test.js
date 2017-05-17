var mug_url = "https://127.0.0.13:5000/";

window.onload = function() {
  fetchMUG("status", "GET", null,
     function(aResult) {
        if (aResult["consensus_height"]) {
          document.getElementById("status").textContent = "Active";
          document.getElementById("cheight").textContent = aResult["consensus_height"];
          document.getElementById("csync").textContent = aResult["consensus_synced"] ? "Yes" : "No";
          document.getElementById("user").textContent = aResult["logged_in"] ? aResult["user"] : "Not logged in";
          document.getElementById("wbalance").textContent = aResult["wallet_confirmed_balance_sc"];
          document.getElementById("wunlocked").textContent = aResult["wallet_unlocked"];
          document.getElementById("siad").textContent = aResult["sia_daemon_running"] ? "active" : "stopped";
        }
        else {
          document.getElementById("status").textContent = "Unknown (Server issue?)"
          console.log(aResult);
        }
      },
      {}
  );
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

