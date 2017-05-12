package io.minebox.resource;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minebox.nbd.Encryption;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(SerialNoResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(SerialNoResource.PATH)
@Singleton
public class SerialNoResource {
    public static final String PATH = "/serialnumber";
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialNoResource.class);
    private final Encryption encryption;


    @Inject
    public SerialNoResource(Encryption encryption) {
        this.encryption = encryption;
    }

    @GET
    @Path("/")
    @Produces("text/plain")
    @PermitAll
    public String getSerialNumber() {
        return encryption.getPublicIdentifier();
    }

}