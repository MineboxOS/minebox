package io.minebox.nbd.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.minebox.nbd.SerialNumberService;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MetaDataStatus {

    public boolean foundMetaData;
    public Map<String, String> lookup;


    public static class MetaDataStatusProvider implements Provider<MetaDataStatus> {

        private final File file;

        @Inject
        public MetaDataStatusProvider(
                @Named("parentDir") String parentDir,
                SerialNumberService serialNumberService) {
            file = getStatusFile(parentDir, serialNumberService);
        }

        private File getStatusFile(String parentDir, SerialNumberService serialNumberService) {
            final File myDir = new File(parentDir, serialNumberService.getPublicIdentifier());
            return new File(myDir, "MetaDataStatus.json");
        }

        public boolean fileExists() {
            return file.exists();
        }

        @Override
        public MetaDataStatus get() {
            try {
                final MetaDataStatus metaDataStatus = new ObjectMapper().readValue(file, MetaDataStatus.class);
                return metaDataStatus;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void write(MetaDataStatus toWrite) {
            try {
                new ObjectMapper().writeValue(file, toWrite);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
