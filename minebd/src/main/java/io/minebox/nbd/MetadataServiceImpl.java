package io.minebox.nbd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 24.04.17.
 */
@Singleton
public class MetadataServiceImpl implements MetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceImpl.class);

    public static final String TEST_METADATA_DIR = "/home/andreas/minebox/restoreSample/backups/backup.1492640813";
    volatile boolean wasInit = false;
    private ImmutableMap<String, String> filenameLookup;
    private final String rootPath;
    private final AuthTokenService authTokenService;

    @Inject
    public MetadataServiceImpl(@Named("httpMetadata") String rootPath, AuthTokenService authTokenService) {
        this.rootPath = rootPath;
        this.authTokenService = authTokenService;
    }

    public void init() {
        if (wasInit) return;
        wasInit = true;
        final ImmutableList<String> metaData = loadMetaData();
        filenameLookup = Maps.uniqueIndex(metaData, input -> {
            final ArrayList<String> segments = Lists.newArrayList(Splitter.on(".").split(input));
            segments.remove(1);
            return Joiner.on(".").join(segments); // minebox_v1_0.dat
        });
    }

    private ImmutableList<String> loadMetaData() {
        try {
            final HttpResponse<InputStream> response = Unirest.get(rootPath + "latestMeta")
                    .header("X-Auth-Token", authTokenService.getToken())
                    .asBinary();
//            final String nameHeader = response.getHeaders().getFirst("Content-Disposition");
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
            LOGGER.info("loaded backup from metadata service");
            return b.build();
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to load backup from metadata service");
            return ImmutableList.of();
        } // minebox_v1_0.dat

/*            final String filename = extractFilename(nameHeader);
            Path latestMeta = Paths.get("staging/" + filename);
            Files.copy(response.getBody(), latestMeta);*/

    }

    static String extractFilename(String attachmenName) {
        //             attachment; filename=backup.1492640813.zip
        final Matcher matcher = Pattern.compile("filename\\=(.*)$").matcher(attachmenName);
        matcher.find();
        return matcher.group(1);
    }

    @Override
    public boolean downloadIfPossible(File file) {
        init();
        final String toDownload = filenameLookup.get(file.getName());
        if (toDownload == null) {
            return false;
        } else {
            downloadFromDemoData(file, toDownload);
            return true;
        }
    }

    private void downloadFromDemoData(File file, String toDownload) {
        try {
            LOGGER.info("downloading missing file {} from remote service... ", toDownload);
            final long start = System.currentTimeMillis();
            final InputStream is = Unirest.get(rootPath + toDownload)
                    .header("X-Auth-Token", authTokenService.getToken())
                    .asBinary().getBody();
            Files.copy(is, Paths.get(file.toURI()));
            final long duration = System.currentTimeMillis() - start;
            LOGGER.info("downloaded {} successfully in {} seconds", toDownload, Duration.ofMillis(duration).getSeconds());
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to download file " + toDownload, e);
            throw new RuntimeException("unable to download file " + toDownload, e);
        }
    }

}
