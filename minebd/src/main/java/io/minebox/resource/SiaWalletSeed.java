package io.minebox.resource;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.minebox.nbd.SiaSeedService;
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import io.swagger.annotations.Api;

/**
 * Created by andreas on 05.07.17.
 */
@Path(SiaWalletSeed.PATH)
@Singleton
@Api(SiaWalletSeed.PATH)
public class SiaWalletSeed {

    public static final String PATH = "siawalletseed";
    private final SiaSeedService siaSeedService;
    private final EncyptionKeyProvider encyptionKeyProvider;

    @Inject
    public SiaWalletSeed(SiaSeedService siaSeedService, EncyptionKeyProvider encyptionKeyProvider) {
        this.siaSeedService = siaSeedService;
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    @GET
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
                .ok(siaSeedService.getSiaSeed())
                .build();
    }

}
