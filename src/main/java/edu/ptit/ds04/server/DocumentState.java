package edu.ptit.ds04.server;

import edu.ptit.ds04.core.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class DocumentState {
    private final String docId;
    private int version;
    private String content;
    private final List<Operation> history = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int snapshotCounter = 0;

    public DocumentState(String docId, int version, String content) {
        this.docId = docId;
        this.version = version;
        this.content = content != null ? content : "";
    }

    public String getDocId() { return docId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<Operation> getHistory() { return history; }
    public ReentrantLock getLock() { return lock; }
    public int getSnapshotCounter() { return snapshotCounter; }
    public void setSnapshotCounter(int snapshotCounter) { this.snapshotCounter = snapshotCounter; }
    public void incrementSnapshotCounter() { this.snapshotCounter++; }
}
