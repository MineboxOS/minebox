package io.minebox.resource;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minebox.nbd.RemoteTokenService;
import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(RemoteTokenResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Api(RemoteTokenResource.PATH)
@Singleton
public class RemoteTokenResource {
    public static final String PATH = "/auth";
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteTokenResource.class);
    private final RemoteTokenService remoteTokenService;


    @Inject
    public RemoteTokenResource(RemoteTokenService remoteTokenService) {
        this.remoteTokenService = remoteTokenService;
    }

    @GET
    @Path("/getMetadataToken")
    @Produces("text/plain")
    @PermitAll
    public String getMetadataToken() {
        return remoteTokenService.getToken();
    }
}