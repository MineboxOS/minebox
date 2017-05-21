package io.minebox.resource;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minebox.nbd.AuthTokenService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(AuthResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(AuthResource.PATH)
@Singleton
public class AuthResource {
    public static final String PATH = "/auth";
    public static final int BYTES_FOR_SEED = 128 / 8;//16*8 = we request 128 bits of entropy
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthResource.class);
    private final AuthTokenService authTokenService;


    @Inject
    public AuthResource(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @GET
    @Path("/getMetadataToken")
    @Produces("text/plain")
    @PermitAll
    public String getMetadataToken() {
        return authTokenService.getToken();
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