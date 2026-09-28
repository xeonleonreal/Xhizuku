package rikka.shizuku.server;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ShizukuConfig {

    public static final int LATEST_VERSION = 2;

    @SerializedName("version")
    public int version = LATEST_VERSION;

    @SerializedName("packages")
    public List<PackageEntry> packages = new ArrayList<>();

    @SerializedName("nightdog_enabled")
    public boolean nightDogEnabled = false;

    public static class PackageEntry extends ConfigPackageEntry {

        @SerializedName("uid")
        public final int uid;

        @SerializedName("flags")
        public int flags;

        @SerializedName("packages")
        public List<String> packages;

        /**
         * Epoch millis when a temporary grant/deny expires. 0 means no expiry.
         * Absent in configs written by older versions (defaults to 0).
         */
        @SerializedName("expiry")
        public long expiry;

        public PackageEntry(int uid, int flags) {
            this.uid = uid;
            this.flags = flags;
            this.packages = new ArrayList<>();
            this.expiry = 0L;
        }

        @Override
        public boolean isAllowed() {
            return (flags & ConfigManager.FLAG_ALLOWED) != 0;
        }

        @Override
        public boolean isDenied() {
            return (flags & ConfigManager.FLAG_DENIED) != 0;
        }
    }

    public ShizukuConfig() {
    }

    public ShizukuConfig(@NonNull List<PackageEntry> packages) {
        this.version = LATEST_VERSION;
        this.packages = packages;
    }
}
