package io.minebox.nbd.download;

import com.google.common.base.Splitter;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public class SiaFileUtil {


    public static int extractSeconds(String siaPath) {
//        minebox_v1_9.1504724036.dat
        final String seconds = Splitter.on(".").splitToList(siaPath).get(1);
        return Integer.parseInt(seconds);
    }

    public static int fileToNumber(File file) {
//        "minebox_v1_" + bucketNumber + ".dat"
        final String name = file.getName();
        final String startFragment = "minebox_v1_";
        final String endFragment = ".dat";
        if (!name.endsWith(endFragment)) {
            throw new IllegalArgumentException(name + "does not end like a minebox file");
        }
        if (!name.startsWith(startFragment)) {
            throw new IllegalArgumentException(name + "does not start like a minebox file");
        }
        final int startLength = startFragment.length();
        final int endLength = endFragment.length();
        final String number = name.substring(startLength, name.length() - endLength);
        return Integer.parseInt(number);

    }

    public static FileTime getFileTime(String siaPath) {
        final int seconds = extractSeconds(siaPath);
        return FileTime.from(seconds, TimeUnit.SECONDS);
    }
}
