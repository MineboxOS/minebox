package io.minebox.nbd;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import io.minebox.nbd.encryption.EncyptionKeyProvider;

/**
 * Created by andreas on 31.05.17.
 */
public class SerialNumberService {

    private final EncyptionKeyProvider encyptionKeyProvider;
    private String publicId;

    @Inject
    public SerialNumberService(EncyptionKeyProvider encyptionKeyProvider) {
        this.encyptionKeyProvider = encyptionKeyProvider;
    }

    public String getPublicIdentifier() {
        if (publicId == null) {
            publicId = buildPubId(encyptionKeyProvider.getImmediatePassword());
        }
        return publicId;
    }

    private String buildPubId(String key) {
        return Hashing.sha256().newHasher().putString("public", Charsets.UTF_8).putString(key, Charsets.UTF_8).hash().toString();
    }
}
