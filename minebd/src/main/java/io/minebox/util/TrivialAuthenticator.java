package io.minebox.util;

import java.security.Principal;
import java.util.Optional;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import io.minebox.config.MinebdConfig;

/**
 * Created by andreas on 12.05.17.
 */
@Singleton
public class TrivialAuthenticator implements Authenticator<BasicCredentials, Principal> {
    private final static Principal INSTANCE = () -> "Local Authenticated user";
    private final String password;

    @Inject
    public TrivialAuthenticator(MinebdConfig config) {
        password = FileUtil.readPassword(config.authFile, config.ignoreMissingPaths, "123");
    }

    @Override
    public Optional<Principal> authenticate(BasicCredentials credentials) throws AuthenticationException {
        return credentials.getPassword().equals(password) ? Optional.of(INSTANCE) : Optional.empty();
    }
}
