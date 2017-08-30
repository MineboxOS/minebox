package io.minebox.nbd.download;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.minebox.SiaUtil;
import io.minebox.nbd.RemoteTokenService;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DownloadFactory implements Provider<DownloadService> {
    private final MetaDataStatus.MetaDataStatusProvider metaDataStatusProvider;
    private RemoteTokenService remoteTokenService;
    private final String metadataUrl;
    private final Path siaDir;
    private final Provider<SiaUtil> siaUtilProvider;


    static final private Logger LOGGER = LoggerFactory.getLogger(DownloadFactory.class);

    @Inject
    public DownloadFactory(MetaDataStatus.MetaDataStatusProvider metaDataStatusProvider,
                           RemoteTokenService remoteTokenService,
                           @Named("httpMetadata") String metadataUrl,
                           @Named("siaDataDirectory") String siaDataDirectory,
                           Provider<SiaUtil> siaUtilProvider) {

        this.metaDataStatusProvider = metaDataStatusProvider;
        this.remoteTokenService = remoteTokenService;
        this.metadataUrl = metadataUrl;
        siaDir = Paths.get(siaDataDirectory).toAbsolutePath();

        this.siaUtilProvider = siaUtilProvider;
    }

    @Override
    public DownloadService get() {
       /*
       cases:
       fresh key, try to reach metadata
       fresh key, metadata reachable but empty -> NothingTodoDownloadService
       fresh key, metadata unreachable -> crash, invalid status.
       fresh key, metadata reachable -> save MetaDataStatus, SiaHostedDownload





       old key: dont try to reach metadata.
       old key, metadata was loaded -> save MetaDataStatus, continue restore with SiaHostedDownload
       old key, metadata was empty -> save MetaDataStatus, NothingTodoDownloadService



        */
        if (!metaDataStatusProvider.fileExists()) {
            //fresh key
            final ImmutableList<String> siaPaths = loadMetaData();
            if (siaPaths == null) {
                throw new IllegalStateException("fresh key, unable to locate metadata ");
            }
            if (siaPaths.isEmpty()) {
                return new NothingTodoDownloadService();
            }

            Map<String, String> filenameLookup = Maps.newHashMap();
            for (String loadedSiaPath : siaPaths) {
                final Pair<String, Long> file_created = parseTimestamp(loadedSiaPath);
                final String siaPath = filenameLookup.get(file_created.getLeft());
                if (siaPath != null) {
                    final Pair<String, Long> existing = parseTimestamp(siaPath);
                    if (existing.getRight() < file_created.getRight()) {
                        filenameLookup.put(file_created.getLeft(), loadedSiaPath);
                    }
                } else {
                    filenameLookup.put(file_created.getLeft(), loadedSiaPath);
                }
            }
            final MetaDataStatus toWrite = new MetaDataStatus();
            toWrite.foundMetaData = true;
            toWrite.lookup = filenameLookup;
            metaDataStatusProvider.write(toWrite);

            return new SiaHostedDownload(siaUtilProvider.get(), filenameLookup);

        } else {
            final MetaDataStatus metaDataStatus = metaDataStatusProvider.get();

            if (metaDataStatus.foundMetaData) {
                return new SiaHostedDownload(siaUtilProvider.get(), metaDataStatus.lookup);
            }
            //we were fresh, nothing to download...
            return new NothingTodoDownloadService();
        }

    }

    private static Pair<String, Long> parseTimestamp(String input) {
        final ArrayList<String> segments = Lists.newArrayList(Splitter.on(".").split(input));
        final String removed = segments.remove(1);//remove timestamp
        return Pair.of(Joiner.on(".").join(segments), Long.parseLong(removed)); // minebox_v1_0.dat
    }

    private ImmutableList<String> loadMetaData() {
        final Optional<String> token = remoteTokenService.getToken();
        if (!token.isPresent()) {
            LOGGER.error("unable to obtain auth token. not trying to fetch the latest meta data");
            return null;
        }
        try {
            final InputStream body = downloadLatestMetadataZip(token.get());
            if (body == null) {
                return ImmutableList.of(); //no metadata loaded
            }
            final ZipInputStream zis = new ZipInputStream(body);
            ZipEntry entry;

            ImmutableList<String> ret = null;
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();

                if (entryName.equals("fileinfo")) {
                    ret = readSiaPathsFromInfo(zis);
                } else {
                    digestSiaFile(entry, zis);
                }
            }
            //one of those files must have been fileinfo...
            if (ret == null) {
                LOGGER.warn("fileinfo not found in metadata");
                return ImmutableList.of();
            } else {
                LOGGER.info("loaded backup from metadata service");
            }
            return ret;
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to load backup from metadata service");
            return null;
        }
    }

    protected InputStream downloadLatestMetadataZip(String token) throws UnirestException {
        final HttpResponse<InputStream> response = Unirest.get(metadataUrl + "file/latestMeta")
                .header("X-Auth-Token", token)
                .asBinary();
        if (response.getStatus() == 404) return null;
        return response.getBody();
    }


    private ImmutableList<String> readSiaPathsFromInfo(ZipInputStream zis) {
        ImmutableList<String> ret;
        JSONArray fileInfo = new JSONArray(convertStreamToString(zis));
        final ImmutableList.Builder<String> filenamesBuilder = ImmutableList.builder();
        for (Object o : fileInfo) {
            JSONObject o2 = (JSONObject) o;
            final String siapath = o2.getString("siapath");
            filenamesBuilder.add(siapath);
        }
        ret = filenamesBuilder.build();
        return ret;
    }

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    protected void digestSiaFile(ZipEntry entry, ZipInputStream zis) {
        final String entryName = entry.getName();
        final Path dest = siaDir.resolve(entryName);
        LOGGER.info("unpacking file {}", dest.toAbsolutePath().toString());
//        if (entryName.startsWith("renter")) {
        try {
            Files.deleteIfExists(dest); //yes, we overwrite everything we find
            Files.copy(zis, dest);
        } catch (IOException e) {
            throw new RuntimeException("unable to create renter file", e);
//            }
        }
    }


}
