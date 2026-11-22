package edu.ptit.ds04.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public static final String SERVER_HOST;
    public static final int SERVER_PORT;
    public static final String STORAGE_DIR;
    public static final int SNAPSHOT_INTERVAL_OPS;
    public static final String AUTH_TOKEN_SECRET;
    public static final int MAX_PAYLOAD_BYTES;
    public static final long HEARTBEAT_INTERVAL_MS;
    public static final long HEARTBEAT_TIMEOUT_MS;

    static {
        Properties props = new Properties();
        File envFile = new File(".env");
        if (envFile.exists()) {
            try (InputStream in = new FileInputStream(envFile)) {
                props.load(in);
            } catch (Exception ignored) {}
        }

        SERVER_HOST = getProp(props, "SERVER_HOST", "0.0.0.0");
        SERVER_PORT = Integer.parseInt(getProp(props, "SERVER_PORT", "8765"));
        STORAGE_DIR = getProp(props, "STORAGE_DIR", "data/documents");
        SNAPSHOT_INTERVAL_OPS = Integer.parseInt(getProp(props, "SNAPSHOT_INTERVAL_OPS", "50"));
        AUTH_TOKEN_SECRET = getProp(props, "AUTH_TOKEN_SECRET", "ds04_collaborative_secret_key_change_in_production");
        MAX_PAYLOAD_BYTES = Integer.parseInt(getProp(props, "MAX_PAYLOAD_BYTES", "1048576")); // 1MB
        HEARTBEAT_INTERVAL_MS = Long.parseLong(getProp(props, "HEARTBEAT_INTERVAL_MS", "10000")); // 10s
        HEARTBEAT_TIMEOUT_MS = Long.parseLong(getProp(props, "HEARTBEAT_TIMEOUT_MS", "30000"));   // 30s
    }

    private static String getProp(Properties props, String key, String defaultVal) {
        String env = System.getenv(key);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String p = props.getProperty(key);
        if (p != null && !p.trim().isEmpty()) {
            return p.trim();
        }
        return defaultVal;
    }
}
