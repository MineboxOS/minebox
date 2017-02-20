package io.minebox.nbd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

/**
 * Created by andreas on 19.02.17.
 */
public class Uploader {

    //yes this password contains real money. but not enough to make me figure out how to secure it for this test...
    public static final String PASSWORD = "fainted pumpkins awful adventure gnaw vortex nutshell sifting afoot hijack pride doctor lymph total reunion arena distance oozed vipers claim hockey damp sash calamity bowling arises riots jigsaw acumen";
    //    public static final String PASSWORD = "rage return onto madness abort vegan under inwardly madness stick swept tucks demonstrate duration viking oneself extra jabbed arsenic dotted renting orchid honked mighty onslaught batch tugs jagged absorb";
    public static final long SECONDS_2 = Duration.ofSeconds(2).toMillis();
    public static final long MINUTES_30 = Duration.ofMinutes(30).toMillis();

    public static void main(String[] args) throws UnirestException, IOException {
        Unirest.setTimeouts(SECONDS_2, MINUTES_30);
        new ProcessBuilder("./siad")
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE).directory(new File("/home/andreas/minebox/siablank"));


        siaCommand("wallet/unlock", ImmutableMap.of("encryptionpassword", PASSWORD));
    }

    private static void siaCommand(String command, ImmutableMap<String, Object> params) throws UnirestException, IOException {
        System.out.println("running command " + command);
        final long start = System.currentTimeMillis();
        final HttpResponse<JsonNode> response = Unirest
                .post("http://localhost:9980/" + command)
                .header("User-Agent", "Sia-Agent")
                .queryString(params)
                .asJson();

        System.out.println("status = " + response.getStatus());
        System.out.println("response.getStatusText() = " + response.getStatusText());

        final InputStream rawBody = response.getRawBody();
        if (rawBody != null) {
            final byte[] resp = ByteStreams.toByteArray(rawBody);
            final String bodyStr = new String(resp, Charsets.UTF_8);
            System.out.println("bodyStr = " + bodyStr);
        }
        long total = System.currentTimeMillis() - start;
        System.out.println("runtime = " + total + " ms");
    }
}
