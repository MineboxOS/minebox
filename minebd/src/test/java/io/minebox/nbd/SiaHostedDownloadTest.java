package io.minebox.nbd;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.minebox.SiaUtil;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Optional;

public class SiaHostedDownloadTest {
    @Test
    public void testInit() throws Exception {
       /* Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Names.named("siaClientUrl"))
            }
        });
        final SiaHostedDownload instance = injector.getInstance(SiaHostedDownload.class);*/


        final SiaUtil siaUtil = new SiaUtil("http://localhost:9980");

        final RemoteTokenService dummyRemoteToken = new RemoteTokenService(null, null) {
            @Override
            public Optional<String> getToken() {
                return Optional.of("123");
            }
        };
        final SiaHostedDownload underTest = new SiaHostedDownload(siaUtil,
                null,
                "junit/sia/Sia-v1.3.0-linux-amd64",
                dummyRemoteToken,
                new StaticEncyptionKeyProvider("123")) {
            @Override
            protected InputStream downloadLatestMetadataZip(String token) throws UnirestException {
                try {
                    return new FileInputStream("/home/andreas/minebox/backup.1503060902.zip");
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

        };
        underTest.initKeyListener();
    }
}