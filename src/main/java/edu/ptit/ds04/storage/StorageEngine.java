package edu.ptit.ds04.storage;

import edu.ptit.ds04.common.Config;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class StorageEngine {
    private final File baseDir;

    public static class DocLoadResult {
        public final int version;
        public final String content;
        public final List<Operation> history;

        public DocLoadResult(int version, String content, List<Operation> history) {
            this.version = version;
            this.content = content;
            this.history = history;
        }
    }

    public StorageEngine(String baseDirPath) {
        this.baseDir = new File(baseDirPath != null ? baseDirPath : Config.STORAGE_DIR);
        if (!this.baseDir.exists()) {
            this.baseDir.mkdirs();
        }
    }

    public StorageEngine() {
        this(null);
    }

    private File getDocDir(String docId) {
        File dir = new File(baseDir, docId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getWalFile(String docId) {
        return new File(getDocDir(docId), "wal.log");
    }

    private File getSnapshotFile(String docId) {
        return new File(getDocDir(docId), "snapshot.json");
    }

    public synchronized void appendWal(String docId, int version, Operation op) {
        File walFile = getWalFile(docId);
        JSONObject record = new JSONObject();
        record.put("version", version);
        record.put("op", op.toJSONObject());
        record.put("timestamp", op.getTimestamp());

        try (FileOutputStream fos = new FileOutputStream(walFile, true);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(record.toString());
            writer.newLine();
            writer.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            System.err.println("[StorageEngine] Error appending WAL for " + docId + ": " + e.getMessage());
        }
    }

    public synchronized void saveSnapshot(String docId, int version, String content, JSONObject metadata) {
        File snapshotFile = getSnapshotFile(docId);
        File tempFile = new File(getDocDir(docId), "snapshot.json.tmp");

        JSONObject snap = new JSONObject();
        snap.put("doc_id", docId);
        snap.put("version", version);
        snap.put("content", content != null ? content : "");
        snap.put("metadata", metadata != null ? metadata : new JSONObject());
        snap.put("timestamp", System.currentTimeMillis());

        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(snap.toString(2));
                writer.flush();
                fos.getFD().sync();
            }
            Files.move(tempFile.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[StorageEngine] Error saving snapshot for " + docId + ": " + e.getMessage());
        }
    }

    public synchronized DocLoadResult loadDocument(String docId) {
        int version = 0;
        String content = "";
        List<Operation> history = new ArrayList<>();

        File snapshotFile = getSnapshotFile(docId);
        if (snapshotFile.exists()) {
            try {
                String snapContent = Files.readString(snapshotFile.toPath(), StandardCharsets.UTF_8);
                JSONObject snapObj = new JSONObject(snapContent);
                version = snapObj.optInt("version", 0);
                content = snapObj.optString("content", "");
            } catch (Exception e) {
                System.err.println("[StorageEngine] Warning loading snapshot for " + docId + ": " + e.getMessage());
            }
        }

        File walFile = getWalFile(docId);
        if (walFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(walFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    JSONObject record = new JSONObject(line);
                    int opVer = record.getInt("version");
                    JSONObject opJson = record.getJSONObject("op");
                    Operation op = Operation.fromJSONObject(opJson);
                    op.setServerVersion(opVer);

                    history.add(op);

                    if (opVer > version) {
                        content = OperationalTransformation.applyOperation(content, op);
                        version = opVer;
                    }
                }
            } catch (Exception e) {
                System.err.println("[StorageEngine] Warning replaying WAL for " + docId + ": " + e.getMessage());
            }
        }

        return new DocLoadResult(version, content, history);
    }

    public List<String> listDocuments() {
        List<String> docs = new ArrayList<>();
        File[] files = baseDir.listFiles(File::isDirectory);
        if (files != null) {
            for (File f : files) {
                if (!f.getName().startsWith(".")) {
                    docs.add(f.getName());
                }
            }
        }
        return docs;
    }
}
