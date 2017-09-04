package io.minebox.config;

import com.google.inject.name.Named;
import io.dropwizard.util.Size;

/**
 * Created by andreas on 11.04.17.
 */
public class MinebdConfig {

    public Integer nbdPort = 10809;
    public Integer maxOpenFiles = 10;
    public String parentDir;
    public Size reportedSize = Size.gigabytes(4);
    public String encryptionKeyPath;
    public String authFile;
    public Size bucketSize = Size.megabytes(40);
    public String httpMetadata;
    public Boolean ignoreMissingPaths = false;
    public String siaDataDirectory;
    public String siaClientUrl;

    public MinebdConfig() {
        //explicit non-annotated constructor so guice does not accidentally this class badly
    }
}
