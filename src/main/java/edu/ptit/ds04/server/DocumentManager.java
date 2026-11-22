package edu.ptit.ds04.server;

import edu.ptit.ds04.common.Config;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;
import edu.ptit.ds04.storage.StorageEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentManager {
    private final StorageEngine storage;
    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();

    public static class ApplyOpResult {
        public final Operation transformedOp;
        public final int newVersion;
        public final String updatedContent;

        public ApplyOpResult(Operation transformedOp, int newVersion, String updatedContent) {
            this.transformedOp = transformedOp;
            this.newVersion = newVersion;
            this.updatedContent = updatedContent;
        }
    }

    public static class HistorySyncResult {
        public final List<Operation> operations;
        public final int currentVersion;
        public final String content;

        public HistorySyncResult(List<Operation> operations, int currentVersion, String content) {
            this.operations = operations;
            this.currentVersion = currentVersion;
            this.content = content;
        }
    }

    public DocumentManager(StorageEngine storage) {
        this.storage = storage != null ? storage : new StorageEngine();
    }

    public DocumentState getOrLoadDocument(String docId) {
        return documents.computeIfAbsent(docId, id -> {
            StorageEngine.DocLoadResult loaded = storage.loadDocument(id);
            DocumentState state = new DocumentState(id, loaded.version, loaded.content);
            state.getHistory().addAll(loaded.history);
            return state;
        });
    }

    public ApplyOpResult applyClientOp(String docId, String clientId, int baseVersion, Operation op) {
        DocumentState doc = getOrLoadDocument(docId);
        doc.getLock().lock();
        try {
            int currentVer = doc.getVersion();
            if (baseVersion < 0 || baseVersion > currentVer) {
                baseVersion = Math.max(0, Math.min(baseVersion, currentVer));
            }

            Operation transformedOp = op.clone();
            transformedOp.setClientId(clientId);
            transformedOp.setDocId(docId);

            // Transform against concurrent operations in history
            if (baseVersion < currentVer) {
                List<Operation> history = doc.getHistory();
                for (int v = baseVersion; v < currentVer; v++) {
                    if (v < history.size()) {
                        Operation histOp = history.get(v);
                        OperationalTransformation.TransformResult tr =
                            OperationalTransformation.transform(transformedOp, histOp, false);
                        transformedOp = tr.op1Prime;
                    }
                }
            }

            // Apply transformed op to authoritative state
            String newContent = OperationalTransformation.applyOperation(doc.getContent(), transformedOp);
            int newVersion = currentVer + 1;

            transformedOp.setBaseVersion(currentVer);
            transformedOp.setServerVersion(newVersion);

            doc.setContent(newContent);
            doc.setVersion(newVersion);
            doc.getHistory().add(transformedOp);

            // Write-Ahead Log
            storage.appendWal(docId, newVersion, transformedOp);

            // Periodic snapshot checkpoint
            doc.incrementSnapshotCounter();
            if (doc.getSnapshotCounter() >= Config.SNAPSHOT_INTERVAL_OPS) {
                storage.saveSnapshot(docId, newVersion, newContent, null);
                doc.setSnapshotCounter(0);
            }

            return new ApplyOpResult(transformedOp, newVersion, newContent);
        } finally {
            doc.getLock().unlock();
        }
    }

    public HistorySyncResult getHistorySince(String docId, int fromVersion) {
        DocumentState doc = getOrLoadDocument(docId);
        doc.getLock().lock();
        try {
            int curVer = doc.getVersion();
            int from = Math.max(0, Math.min(fromVersion, curVer));
            List<Operation> history = doc.getHistory();
            List<Operation> missing = new ArrayList<>();
            for (int v = from; v < curVer; v++) {
                if (v < history.size()) {
                    missing.add(history.get(v).clone());
                }
            }
            return new HistorySyncResult(missing, curVer, doc.getContent());
        } finally {
            doc.getLock().unlock();
        }
    }

    public void forceSnapshot(String docId) {
        DocumentState doc = getOrLoadDocument(docId);
        doc.getLock().lock();
        try {
            storage.saveSnapshot(docId, doc.getVersion(), doc.getContent(), null);
        } finally {
            doc.getLock().unlock();
        }
    }
}
