package io.minebox;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;

public class SiaUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SiaUtil.class);


    final private String path;

    @Inject
    public SiaUtil(@Named("siaClientUrl") String siaClientUrl) {
        this.path = siaClientUrl;
    }


    private static final String NO_HOSTS = "insufficient hosts to recover file";
    private static final String NOT_SYNCED = "cannot init from seed until blockchain is synced";
    private static final String LOCKED = "wallet must be unlocked before it can be used";
    private static final String SEED_MISSING = "wallet has not been encrypted yet";
    private static final String NO_FUNDS = " unable to fund transaction: insufficient balance";
    private static final String NO_ADDRESS = "could not read address";

    private static boolean alreadyUnderway(HttpResponse<String> unlockReply) {
        return checkErrorFragment(unlockReply, "another wallet rescan is already underway");
    }

    private static boolean notSynced(HttpResponse<String> unlockReply) {
        return checkErrorFragment(unlockReply, "cannot init from seed until blockchain is synced");
    }

    private static boolean needsEncryption(HttpResponse<String> unlockReply) {
        return checkErrorFragment(unlockReply, SEED_MISSING);
    }

    private static boolean checkErrorFragment(HttpResponse<String> reply, String fragment) {
        if (reply == null) {
            throw new RuntimeException("reply was null!. checking for fragment: " + fragment);
        }
        if (statusGood(reply)) {
            return false;
        }
        final String body = reply.getBody();
        if (body == null) {
            throw new RuntimeException("replybody was null! checking for fragment: " + fragment);
        }
        return body.contains(fragment);
    }

    static boolean notAnAddress(HttpResponse<String> reply) {
        return checkErrorFragment(reply, NO_ADDRESS);
    }

    static boolean notEnoughFunds(HttpResponse<String> reply) {
        return checkErrorFragment(reply, NO_FUNDS);
    }

    static boolean walletIsLocked(HttpResponse<String> reply) {
        return checkErrorFragment(reply, LOCKED);

    }

    static boolean isNotSynced(HttpResponse<String> reply) {
        return checkErrorFragment(reply, NOT_SYNCED);
    }


    public BigInteger calcHastingsAmount(double sendingCentsAmount) {
        double eurPerBTC = 3400;
        double bitcoinPerSiacoin = 0.00000170;
        double siacoins = sendingCentsAmount / 100 / eurPerBTC / bitcoinPerSiacoin;

        return siaToHastings(siacoins);
    }

    private BigInteger siaToHastings(double siacoins) {
        BigDecimal hastings_per_sia = BigDecimal.valueOf(10).pow(24);
        return BigDecimal.valueOf(siacoins).multiply(hastings_per_sia).toBigIntegerExact();
    }


    private HttpResponse<String> siaCommand(SiaCommand command, ImmutableMap<String, Object> params, String... extraCommand) {
        try {
            final HttpRequest httpRequest = command.unirest(path, extraCommand)
                    .header("User-Agent", "Sia-Agent")
                    .queryString(params);
            return httpRequest.asString();
        } catch (UnirestException e) {
            throw new NoConnectException(e);
        }
    }


    public void stopProcess() {
        siaCommand(SiaCommand.STOP, ImmutableMap.of());
    }

    HttpResponse<String> sendFunds(BigInteger amount, String destination) {
        return siaCommand(SiaCommand.SENDCOINS, ImmutableMap.of("amount", amount.toString(), "destination", destination));

    }

    public boolean download(String siaPath, Path destination) {
        final String dest = destination.toAbsolutePath().toString();
        final HttpResponse<String> downloadResult = siaCommand(SiaCommand.DOWNLOAD, ImmutableMap.of("destination", dest), siaPath);
        final boolean noHosts = checkErrorFragment(downloadResult, NO_HOSTS);
        if (noHosts) {
            LOGGER.warn("unable to download file {} due to NO_HOSTS  ", siaPath);
            return false;
        }
        if (statusGood(downloadResult)) {
            return true;
        }
        LOGGER.warn("unable to download file {} for an unexpected reason: {} ", siaPath, downloadResult.getBody());
        return false;
    }

    private static boolean statusGood(HttpResponse<String> response) {
        if (response == null) {
            return false;
        }
        final int status = response.getStatus();
        return status >= 200 && status < 300;
    }

    private HttpResponse<String> initSeed(String seed) {
        return siaCommand(SiaCommand.INITSEED, ImmutableMap.of("encryptionpassword", seed, "seed", seed));
    }

    public void waitForConsensus() {
        final long start = System.currentTimeMillis();
        while (true) {
            LOGGER.warn("checking if blockchain is ready");
            HttpResponse<String> result;
            try {
                result = this.siaCommand(SiaCommand.CONSENSUS, ImmutableMap.of());
            } catch (NoConnectException e) {
                result = null;
            }
            JSONObject result2 = null;
            final long runtime = System.currentTimeMillis() - start;
            if (statusGood(result)) {
                result2 = new JSONObject(result.getBody());
                if (result2.getBoolean("synced")) {
                    LOGGER.info("blockchain ready (height " + result2.getInt("height") + ") after " + runtime + " ms");
                    break;
                }
            }
            if (result2 != null) {
                LOGGER.warn("blockchain not ready (height " + result2.getInt("height") + ") after " + runtime + " ms" + ", retrying in 10 seconds...");
            } else {
                LOGGER.warn("blockchain not ready, unknown height after " + runtime + " ms");
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public boolean unlockWallet(String seed) {
        HttpResponse<String> unlockReply = siaCommand(SiaCommand.UNLOCK, ImmutableMap.of("encryptionpassword", seed));
        if (alreadyUnderway(unlockReply)) {
            LOGGER.info("unable to unlock, operation was already started..");
            return false;
        }
        if (needsEncryption(unlockReply)) {
            LOGGER.info("no seed yet, (encryption missing) - running init");
            HttpResponse<String> seedReply = initSeed(seed);
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


    public String myAddress() {
        final HttpResponse<String> string = siaCommand(SiaCommand.ADDRESS, ImmutableMap.of());
        final JSONObject jsonObject = new JSONObject(string.getBody());
        return jsonObject.getString("address");
    }

    public String getWalletInfo() {
        final HttpResponse<String> stringHttpResponse = siaCommand(SiaCommand.WALLET, ImmutableMap.of());
        return stringHttpResponse.getBody();
    }

    public double estimatedPercent(long blocks) {
        final LocalDateTime block100k = LocalDateTime.of(2017, Month.APRIL, 13, 23, 29, 49);
        final long minutes = block100k.until(LocalDateTime.now(), ChronoUnit.MINUTES);
        final int blockTime = 9;
        final long diff = minutes / blockTime;

        long estimatedHeight = 100000 + (diff / blockTime);
        return ((double) blocks / (double) estimatedHeight);

//        block100kTimestamp := time.Date(2017, time.April, 13, 23, 29, 49, 0, time.UTC)
//        blockTime := float64(9) // overestimate block time for better UX
//        diff := t.Sub(block100kTimestamp)
//        estimatedHeight := 100e3 + (diff.Minutes() / blockTime)
//        return types.BlockHeight(estimatedHeight + 0.5) // round to the nearest block
    }

    private static class NoConnectException extends RuntimeException {
        NoConnectException(UnirestException e) {
            super(e);
        }
    }
}
