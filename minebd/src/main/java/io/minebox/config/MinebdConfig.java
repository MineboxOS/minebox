package io.minebox.config;

import io.dropwizard.Configuration;
import io.dropwizard.util.Size;

/**
 * Created by andreas on 11.04.17.
 */
public class MinebdConfig extends Configuration {

    public Integer nbdPort = 10819;
    public Integer maxOpenFiles = 10;
    public String parentDir;
    public Size reportedSize = Size.gigabytes(4);
    public String encryptionSeed = "to_be_replaced_with_usb";
    public Size bucketSize = Size.megabytes(40);

}
