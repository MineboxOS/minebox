package io.minebox.nbd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 24.04.17.
 */
@Singleton
public class MetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataService.class);

    public static final String TEST_METADATA_DIR = "/home/andreas/minebox/restoreSample/backups/backup.1492640813";
    public static final String ROOT_PATH = "http://localhost:8050/v1/file/";
    private ImmutableMap<String, String> filenameLookup;


    @Inject
    public MetadataService() {
        loadMetaData();
    }

    public void loadMetaData() {
        try {
            final HttpResponse<InputStream> response = Unirest.get(ROOT_PATH + "latestMeta")
                    .asBinary();
            final String nameHeader = response.getHeaders().getFirst("Content-Disposition");
            final ZipInputStream zis = new ZipInputStream(response.getBody());
            ZipEntry entry = null;
            final ImmutableList.Builder<String> b = ImmutableList.builder();
            while ((entry = zis.getNextEntry()) != null) {
                final String siaName = entry.getName();
                //    backup.1492640813/minebox_v1_0.1492628694.dat.sia
                if (siaName.endsWith(".sia") && siaName.contains("/")) {
                    final String datName = siaName.substring(siaName.lastIndexOf('/') + 1, siaName.length() - 4);
                    //minebox_v1_0.1492628694.dat
                    b.add(datName);
                }
            }

            ImmutableList<String> latestMetaSiaFiles = b.build();
            // minebox_v1_0.dat
            filenameLookup = Maps.uniqueIndex(latestMetaSiaFiles, new Function<String, String>() {
                @Nullable
                @Override
                public String apply(@Nullable String input) {
                    final ArrayList<String> segments = Lists.newArrayList(Splitter.on(".").split(input));
                    segments.remove(1);
                    return Joiner.on(".").join(segments); // minebox_v1_0.dat
                }
            });
            final String filename = extractFilename(nameHeader);
            Path latestMeta = Paths.get("staging/" + filename);
            Files.copy(response.getBody(), latestMeta);
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to load backup...");
        }
    }

    String extractFilename(String attachmenName) {
        //             attachment; filename=backup.1492640813.zip
        final Matcher matcher = Pattern.compile("filename\\=(.*)$").matcher(attachmenName);
        matcher.find();
        return matcher.group(1);
    }

    public boolean downloadIfPossible(File file) {
        final String toDownload = filenameLookup.get(file.getName());
        if (toDownload == null) {
            return false;
        } else {
            downloadFromDemoData(file, toDownload);
        }
        return true;
    }

    public void downloadFromDemoData(File file, String toDownload) {
        try {
            LOGGER.info("downloading missing file {} from remote service... ", toDownload);
            final long start = System.currentTimeMillis();
            final InputStream is = Unirest.get(ROOT_PATH + toDownload).asBinary().getBody();
            Files.copy(is, Paths.get(file.toURI()));
            final long duration = System.currentTimeMillis() - start;
            LOGGER.info("downloaded {} successfully in {} seconds", toDownload, Duration.ofMillis(duration).getSeconds());
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to download file " + toDownload, e);
            throw new RuntimeException("unable to download file " + toDownload, e);
        }
    }
}
