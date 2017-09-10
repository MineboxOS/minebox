package io.minebox.sia;

import com.google.common.base.Joiner;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequest;

public enum SiaCommand {
    //        WALLET("/wallet", "GET"), //confirmedsiacoinbalance
    VERSION("/daemon/version", "GET"),
    WALLET("/wallet", "GET"),
    STOP("/daemon/stop", "GET"),
    CONSENSUS("/consensus", "GET"),
    DOWNLOAD("/renter/download", "GET", true),
    ADDRESS("/wallet/address", "GET"),
    INITSEED("/wallet/init/seed", "POST", true),
    SENDCOINS("/wallet/siacoins", "POST"),//        amount      // hastings //        destination // address
    UNLOCK("/wallet/unlock", "POST", true);

    private final String command;
    private final String httpMethod;
    private final boolean longOperation;


    SiaCommand(String command, String httpMethod, boolean longOperation) {
        this.command = command;
        this.httpMethod = httpMethod;
        this.longOperation = longOperation;
    }


    SiaCommand(String command, String method) {
        this(command, method, false);
    }

    HttpRequest unirest(String baseUrl, String... extraPath) {
        if (longOperation) {
            Unirest.setTimeouts(10000, 15 * 60000);
        } else {
            Unirest.setTimeouts(10000, 60000);
        }
        String joinedPath = "/" + Joiner.on("/").join(extraPath);
        if (joinedPath.length() == 1) {
            joinedPath = "";
        }
        if (httpMethod.equals("GET")) {
            return Unirest.get(baseUrl + command + joinedPath);
        } else if (httpMethod.equals("POST")) {
            return Unirest.post(baseUrl + command + joinedPath);
        }
        throw new IllegalStateException("unknown method");
    }
}
