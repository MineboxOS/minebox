package io.minebox.config;

import io.dropwizard.util.Size;

import java.util.List;

/**
 * Created by andreas on 11.04.17.
 */
public class MinebdConfig {

    public Integer nbdPort = 10809;
    public Integer maxOpenFiles = 10;
    public List<String> parentDirs;
    public Size reportedSize = Size.gigabytes(4);
    public String encryptionKeyPath;
    public String authFile;
    public Size bucketSize = Size.megabytes(40);
    public Size maxUnflushed = Size.megabytes(100);
    public Size minFreeSystemMem = Size.megabytes(400);
    public String httpMetadata;
    public Boolean ignoreMissingPaths = false;
    public String siaDataDirectory;
    public String siaClientUrl;

    public MinebdConfig() {
        //explicit non-annotated constructor so guice does not accidentally this class badly
    }
}
