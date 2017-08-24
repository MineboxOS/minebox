package io.minebox.nbd;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
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
import io.minebox.nbd.encryption.EncyptionKeyProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by andreas on 24.04.17.
 */
public abstract class AbstractDownload implements DownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDownload.class);

    private static Pair<String, Long> parseTimestamp(String input) {
        final ArrayList<String> segments = Lists.newArrayList(Splitter.on(".").split(input));
        final String removed = segments.remove(1);//remove timestamp
        return Pair.of(Joiner.on(".").join(segments), Long.parseLong(removed)); // minebox_v1_0.dat
    }

    protected final String metadataUrl;
    protected final RemoteTokenService remoteTokenService;
    private EncyptionKeyProvider encyptionKeyProvider;
    private volatile boolean wasInit = false;
    protected volatile boolean connectedToMetadata = false;
    private volatile boolean hasMetadata = false;
    private Map<String, String> filenameLookup;

    AbstractDownload(String metadataUrl, RemoteTokenService remoteTokenService, EncyptionKeyProvider encyptionKeyProvider) {
        this.metadataUrl = metadataUrl;
        this.remoteTokenService = remoteTokenService;
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    @Inject
    public void initKeyListener() {
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
        final ImmutableList<String> siaPaths = loadMetaData();
        try {//list of remote filenames

            filenameLookup = Maps.newHashMap();
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
            final InputStream body = downloadLatestMetadataZip(token.get());
            final ZipInputStream zis = new ZipInputStream(body);
            ZipEntry entry;

            ImmutableList<String> ret = null;
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();
                if (entryName.startsWith("renter")) {
                    digestRenterFile(entry, zis);
                }
                if (entryName.equals("fileinfo")) {
                    ret = readSiaPathsFromInfo(zis);
                }

            }
            finishedDigest();
            //one of those files must have been fileinfo...
            if (ret == null) {
                connectedToMetadata = false;
                LOGGER.warn("fileinfo not found in metadata");
                return ImmutableList.of();
            } else {
                LOGGER.info("loaded backup from metadata service");
                connectedToMetadata = true;
                hasMetadata = !ret.isEmpty();
            }
            return ret;
        } catch (UnirestException | IOException e) {
            LOGGER.error("unable to load backup from metadata service");
            return ImmutableList.of();
        } // minebox_v1_0.dat

/*            final String filename = extractFilename(nameHeader);
            Path latestMeta = Paths.get("staging/" + filename);
            Files.copy(response.getBody(), latestMeta);*/

    }

    protected void finishedDigest() {
        //override this in testcase, so we can restart siad at the right time
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


    protected InputStream downloadLatestMetadataZip(String token) throws UnirestException {
        final HttpResponse<InputStream> response = Unirest.get(metadataUrl + "file/latestMeta")
                .header("X-Auth-Token", token)
                .asBinary();
        return response.getBody();
    }

    protected abstract void digestRenterFile(ZipEntry entry, ZipInputStream zis);

    @Override
    public RecoveryStatus downloadIfPossible(File file) {
        if (!wasInit) throw new IllegalStateException("i was not inited yet");
        Preconditions.checkNotNull(file);
        final String name = file.getName();
        final String toDownload = filenameLookup.get(name);
        if (toDownload == null) {
            return RecoveryStatus.NO_FILE;
        } else {
            final boolean ret = downloadFile(file, toDownload);
            if (ret) {
                return RecoveryStatus.RECOVERED;
            } else {
                return RecoveryStatus.ERROR;
            }
        }
    }

    protected abstract boolean downloadFile(File file, String toDownload);

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

    public Collection<String> allSiaPaths() {
        return filenameLookup.values();
    }

}
