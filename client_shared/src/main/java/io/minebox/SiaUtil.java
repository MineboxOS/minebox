package io.minebox;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.Path;

public class SiaUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiaUtil.class);


    final private String path;

    @Inject
    public SiaUtil(@Named("siaClientUrl") String siaClientUrl) {
        this.path = siaClientUrl;
    }

    private static final String NOT_SYNCED = "cannot init from seed until blockchain is synced";
    private static final String LOCKED = "wallet must be unlocked before it can be used";
    private static final String SEED_MISSING = "wallet has not been encrypted yet";
    private static final String NO_FUNDS = " unable to fund transaction: insufficient balance";
    private static final String NO_ADDRESS = "could not read address";

    static boolean alreadyUnderway(HttpResponse<JsonNode> unlockReply) {
        return checkErrorFragment(unlockReply, "another wallet rescan is already underway");
    }

    static boolean notSynced(HttpResponse<JsonNode> unlockReply) {
        return checkErrorFragment(unlockReply, "cannot init from seed until blockchain is synced");
    }

    static boolean needsEncryption(HttpResponse<JsonNode> unlockReply) {
        return checkErrorFragment(unlockReply, SEED_MISSING);
    }

    private static boolean checkErrorFragment(HttpResponse<JsonNode> reply, String fragment) {
        if (reply == null){
            throw new RuntimeException("reply was null!. checking for fragment: "+fragment);
        }
        final JsonNode body = reply.getBody();
        if (body == null){
            throw new RuntimeException("replybody was null! checking for fragment: "+fragment);
        }
        JSONObject object = body.getObject();
        if (!object.has("message")) {
            return false;
        }
        String errorMessage = object.getString("message");
        return errorMessage.contains(fragment);
    }

    static boolean notAnAddress(HttpResponse<JsonNode> reply) {
        return checkErrorFragment(reply, NO_ADDRESS);
    }

    static boolean notEnoughFunds(HttpResponse<JsonNode> reply) {
        return checkErrorFragment(reply, NO_FUNDS);
    }

    static boolean walletIsLocked(HttpResponse<JsonNode> reply) {
        return checkErrorFragment(reply, LOCKED);

    }

    private static boolean walletHasNoSeed(HttpResponse<JsonNode> reply) {
        throw new UnsupportedOperationException("not implemented");

    }

    static boolean isNotSynced(HttpResponse<JsonNode> reply) {
        return checkErrorFragment(reply, NOT_SYNCED);
    }

    public String calcHastingsAmount(double sendingEurAmount) {
        double eurPerBTC = 2911;
        double bitcoinPerSiacoin = 0.00000247;
        double siacoins = sendingEurAmount / eurPerBTC / bitcoinPerSiacoin;

        return siaToHastings(siacoins);
    }

    private String siaToHastings(double siacoins) {
        BigDecimal hastings_per_sia = BigDecimal.valueOf(10).pow(24);
        return BigDecimal.valueOf(siacoins).multiply(hastings_per_sia).toBigIntegerExact().toString();
    }


    public HttpResponse<JsonNode> siaCommand(Command command, ImmutableMap<String, Object> params, String... extraCommand) {
        try {
            return command.unirest(path, extraCommand)
                    .header("User-Agent", "Sia-Agent")
                    .queryString(params)
                    .asJson();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }


    HttpResponse<JsonNode> sendFunds(String amount, String destination) {
        return siaCommand(Command.SENDCOINS, ImmutableMap.of("amount", amount, "destination", destination));

    }

    public HttpResponse<JsonNode> download(String siaPath, Path destination) {
        final String dest = destination.toAbsolutePath().toString();
        return siaCommand(Command.DOWNLOAD, ImmutableMap.of("destination", dest), siaPath);

    }

    HttpResponse<JsonNode> initSeed(String seed) {
        return siaCommand(Command.INITSEED, ImmutableMap.of("encryptionpassword", seed, "seed", seed));
    }

    public void waitForConsensus() {
        boolean synced = false;
        while (true) {
            LOGGER.warn("checking if blockchain is ready");
            final HttpResponse<JsonNode> result = this.siaCommand(Command.CONSENSUS, ImmutableMap.of());
            final JSONObject result2 = result.getBody().getObject();
            synced = result2.getBoolean("synced");
            if (synced) {
                LOGGER.info("blockchain ready (height " + result2.getInt("height") + ")");
                break;
            }
            LOGGER.warn("blockchain not ready (height " + result2.getInt("height") + "), retrying in 10 seconds...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }


    }

    public boolean unlockWallet(String seed) {
        HttpResponse<JsonNode> unlockReply = siaCommand(Command.UNLOCK, ImmutableMap.of("encryptionpassword", seed));
        if (alreadyUnderway(unlockReply)) {
            LOGGER.info("unable to unlock, operation was already started..");
            return false;
        }
        if (needsEncryption(unlockReply)) {
            LOGGER.info("no seed yet, (encryption missing) - running init");
            HttpResponse<JsonNode> seedReply = initSeed(seed);
            if (notSynced(seedReply)) {
                LOGGER.warn("blockchain not ready, retrying in 10 seconds...");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            LOGGER.info("retrying unlock after init");
            return unlockWallet(seed);
        }
        return true;
    }

    public enum Command {
        WALLET("/wallet", "GET"), //confirmedsiacoinbalance
        CONSENSUS("/consensus", "GET"),
        DOWNLOAD("/renter/download", "GET"),
        ADDRESS("/wallet/address", "GET"),
        INITSEED("/wallet/init/seed", "POST", true),
        SENDCOINS("/wallet/siacoins", "POST"),//        amount      // hastings //        destination // address
        UNLOCK("/wallet/unlock", "POST", true);

        private final String command;
        private final String httpMethod;
        private final boolean longOperation;


        Command(String command, String httpMethod, boolean longOperation) {
            this.command = command;
            this.httpMethod = httpMethod;
            this.longOperation = longOperation;
        }


        Command(String command, String method) {
            this(command, method, false);
        }

        public HttpRequest unirest(String baseUrl, String... extraPath) {
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
}
