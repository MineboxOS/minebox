package io.minebox.resource;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.auth.Auth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@Path(KeyResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(KeyResource.PATH)
@Singleton
public class KeyResource {
    public static final String PATH = "/keys";
    public static final int BYTES_FOR_SEED = 128 / 8;//16*8 = we request 128 bits of entropy
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyResource.class);


    @Inject
    public KeyResource() {
    }

    @GET
    @Path("/")
    @ApiOperation(value = "just a dummy  lists all WS classes",
            response = String.class)
    @Produces("text/plain")
    @PermitAll
    public String currentKey() {
        return Joiner.on(" ").join(getSeedWords());
    }

    @GET
    @Path("/asJson")
    @ApiOperation(value = "just a dummy  lists all WS classes",
            response = List.class)
    @Produces("application/json")
    @PermitAll
    public Response jsonKey() {
        return Response.ok(getSeedWords()).build();

    }

    private List<String> getSeedWords() {
        List<String> seedWords;
        try {
            final MnemonicCode mnemonicCode = new MnemonicCode();
            seedWords = mnemonicCode.toMnemonic(getRandomSeed());
        } catch (IOException | MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e);
        }
        return seedWords;
    }


    public byte[] getRandomSeed() {
        File file = new File("/dev/urandom");
        if (!file.exists()) {
            throw new RuntimeException("/dev/urandom not present");
        }
        byte[] ret = new byte[BYTES_FOR_SEED];
        try {
            FileInputStream stream = new FileInputStream(file);
            DataInputStream dis = new DataInputStream(stream);
            dis.readFully(ret);
            dis.close();
        } catch (IOException e) {
            throw new RuntimeException("unable to read random data from /dev/urandom ", e);
        }
        return ret;
    }


}