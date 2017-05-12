package io.minebox.util;

import java.io.IOException;
import java.security.Principal;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;

import io.dropwizard.auth.AuthFilter;

/**
 * Created by andreas on 12.05.17.
 */
public class StringAuthFilter<P extends Principal> extends AuthFilter<String, P> {
    private final static String X_AUTH_TOKEN_HEADER = "X-Auth-Token";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        final String credentials =
                requestContext.getHeaders().getFirst(X_AUTH_TOKEN_HEADER);
        if (!authenticate(requestContext, credentials, "SCHEME")) {
            throw new WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm));
        }
    }
}
