package io.minebox.nbd.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.minebox.nbd.SerialNumberService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MetaDataStatus {

    public Map<String, String> lookup;


    public static class MetaDataStatusProvider implements Provider<MetaDataStatus> {

        private File file;
        private final List<String> parentDirs;
        private final SerialNumberService serialNumberService;

        @Inject
        public MetaDataStatusProvider(
                @Named("parentDirs") List<String> parentDir,
                SerialNumberService serialNumberService) {

            this.parentDirs = parentDir;
            this.serialNumberService = serialNumberService;
        }

        private File getStatusFile(String parentDir, SerialNumberService serialNumberService) {
            final File myDir = new File(parentDir, serialNumberService.getPublicIdentifier());
            return new File(myDir, "MetaDataStatus.json");
        }

        public boolean fileExists() {
            initFile();
            return file.exists();
        }

        @Override
        public MetaDataStatus get() {
            initFile();
            try {
                return new ObjectMapper().readValue(file, MetaDataStatus.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void initFile() {
            if (file == null) {
                file = getStatusFile(parentDirs.get(0), serialNumberService);
            }
        }

        public void write(MetaDataStatus toWrite) {
            initFile();
            try {
                file.getParentFile().mkdirs();
                new ObjectMapper().writeValue(file, toWrite);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
