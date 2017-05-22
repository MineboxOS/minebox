package io.minebox.resource;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minebox.nbd.Encryption;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.swagger.annotations.Api;
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
    private final EncyptionKeyProvider encyptionKeyProvider;


    @Inject
    public SerialNoResource(Encryption encryption, EncyptionKeyProvider encyptionKeyProvider) {
        this.encryption = encryption;
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    @GET
    @Path("/")
    @Produces("text/plain")
    @PermitAll
    public Response getSerialNumber() {
        final ListenableFuture<String> masterPassword = encyptionKeyProvider.getMasterPassword();
        if (!masterPassword.isDone()) {
            return Response.status(Response.Status.PRECONDITION_FAILED)
                    .entity("Key is not set yet..")
                    .build();
        }
        return Response
                .ok(encryption.getPublicIdentifier())
                .build();
    }

}