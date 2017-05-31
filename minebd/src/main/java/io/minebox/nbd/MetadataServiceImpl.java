package io.minebox.nbd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
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
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by andreas on 24.04.17.
 */
@Singleton
public class MetadataServiceImpl implements MetadataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceImpl.class);
    private final String rootPath;
    private final RemoteTokenService remoteTokenService;
    private volatile boolean wasInit = false;
    private volatile boolean connectedToMetadata = false;
    private volatile boolean hasMetadata = false;
    private ImmutableMap<String, String> filenameLookup;

    @Inject
    public MetadataServiceImpl(@Named("httpMetadata") String rootPath, RemoteTokenService remoteTokenService, EncyptionKeyProvider encyptionKeyProvider) {
        this.rootPath = rootPath;
        this.remoteTokenService = remoteTokenService;
        encyptionKeyProvider.onLoadKey(this::init);
    }

    static String extractFilename(String attachmenName) {
        //             attachment; filename=backup.1492640813.zip
        final Matcher matcher = Pattern.compile("filename\\=(.*)$").matcher(attachmenName);
        matcher.find();
        return matcher.group(1);
    }

    private void init() {
        if (wasInit) return;
        wasInit = true;
        final ImmutableList<String> metaData = loadMetaData();
        try {//list of remote filenames
            filenameLookup = Maps.uniqueIndex(metaData, input -> {
                final ArrayList<String> segments = Lists.newArrayList(Splitter.on(".").split(input));
                segments.remove(1); //remove timestamp
                return Joiner.on(".").join(segments); // minebox_v1_0.dat
            });
        } catch (IllegalArgumentException iae) {
            LOGGER.error("unable to build metadata index since the files were non-unique");
            filenameLookup = ImmutableMap.of();
        }
    }

    private ImmutableList<String> loadMetaData() {
        final Optional<String> token = remoteTokenService.getToken();
        if (!token.isPresent()) {
            LOGGER.error("unable to obtain auth token. not trying to fetch the latest meta data");
            return ImmutableList.of();
        }
        try {
            final HttpResponse<InputStream> response = Unirest.get(rootPath + "file/latestMeta")
                    .header("X-Auth-Token", token.get())
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
            connectedToMetadata = true;
            LOGGER.info("loaded backup from metadata service");
            final ImmutableList<String> ret = b.build();
            hasMetadata = !ret.isEmpty();
            return ret;
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to load backup from metadata service");
            return ImmutableList.of();
        } // minebox_v1_0.dat

/*            final String filename = extractFilename(nameHeader);
            Path latestMeta = Paths.get("staging/" + filename);
            Files.copy(response.getBody(), latestMeta);*/

    }

    @Override
    public boolean downloadIfPossible(File file) {
        if (!wasInit) throw new IllegalStateException("i was not inited yet");
        Preconditions.checkNotNull(file);
        final String name = file.getName();
        final String toDownload = filenameLookup.get(name);
        if (toDownload == null) {
            return false;
        } else {
            downloadFromDemoData(file, toDownload);
            return true;
        }
    }

    @Override
    public boolean hasMetadata() {
        if (!wasInit) throw new IllegalStateException("i was not inited yet");
        return hasMetadata;
    }

    @Override
    public boolean connectedMetadata() {
        if (!wasInit) throw new IllegalStateException("i was not inited yet");
        return connectedToMetadata;
    }


    @Override
    public Collection<String> allFilenames() {
        return filenameLookup.keySet();
    }

    private void downloadFromDemoData(File file, String toDownload) {
        final Optional<String> token = remoteTokenService.getToken();
        if (!token.isPresent()) {
            LOGGER.error("unable to obtain auth token needed to download file {}", toDownload);
            throw new RuntimeException("unable to download file " + toDownload);
        }
        try {
            LOGGER.info("downloading missing file {} from remote service... ", toDownload);
            final long start = System.currentTimeMillis();
            final InputStream is = Unirest.get(rootPath + "file/" + toDownload)
                    .header("X-Auth-Token", token.get())
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
