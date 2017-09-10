package io.minebox.sia;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public class SiaFileUtil {


    public static int extractSeconds(String siaPath) {
//        minebox_v1_9.1504724036.dat
        final String seconds = Splitter.on(".").splitToList(siaPath).get(1);
        return Integer.parseInt(seconds);
    }

    public static int fileToNumber(String name) {
        final String number = Splitter.on(CharMatcher.anyOf("_.")).splitToList(name).get(2);
        return Integer.parseInt(number);
    }

    public static FileTime getFileTime(String siaPath) {
        final int seconds = extractSeconds(siaPath);
        return FileTime.from(seconds, TimeUnit.SECONDS);
    }
}
