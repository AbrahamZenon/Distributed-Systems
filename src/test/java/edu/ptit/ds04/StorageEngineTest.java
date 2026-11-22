package edu.ptit.ds04;

import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.storage.StorageEngine;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StorageEngineTest {

    @Test
    public void testWalAppendAndRecovery() throws Exception {
        File tempDir = Files.createTempDirectory("ds04_storage_test_").toFile();
        try {
            StorageEngine storage = new StorageEngine(tempDir.getAbsolutePath());
            String docId = "doc-wal-test";

            Operation op1 = new Operation(OpType.INSERT, 0, "Hello ");
            storage.appendWal(docId, 1, op1);

            Operation op2 = new Operation(OpType.INSERT, 6, "World!");
            storage.appendWal(docId, 2, op2);

            Operation op3 = new Operation(OpType.DELETE, 5, 1); // delete space
            storage.appendWal(docId, 3, op3);

            // Reconstruct after simulated crash
            StorageEngine restarted = new StorageEngine(tempDir.getAbsolutePath());
            StorageEngine.DocLoadResult loaded = restarted.loadDocument(docId);

            assertEquals(3, loaded.version);
            assertEquals("HelloWorld!", loaded.content);
            assertEquals(3, loaded.history.size());
        } finally {
            Files.walk(tempDir.toPath())
                .sorted(Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    public void testSnapshotCombinedWithWal() throws Exception {
        File tempDir = Files.createTempDirectory("ds04_snap_test_").toFile();
        try {
            StorageEngine storage = new StorageEngine(tempDir.getAbsolutePath());
            String docId = "doc-snap-test";

            // Save snapshot at version 10
            storage.saveSnapshot(docId, 10, "Base State. ", null);

            // Operations 11 and 12
            Operation op11 = new Operation(OpType.INSERT, 12, "V11. ");
            storage.appendWal(docId, 11, op11);

            Operation op12 = new Operation(OpType.INSERT, 17, "V12.");
            storage.appendWal(docId, 12, op12);

            // Crash & reload
            StorageEngine restarted = new StorageEngine(tempDir.getAbsolutePath());
            StorageEngine.DocLoadResult loaded = restarted.loadDocument(docId);

            assertEquals(12, loaded.version);
            assertEquals("Base State. V11. V12.", loaded.content);
            assertEquals(2, loaded.history.size());
        } finally {
            Files.walk(tempDir.toPath())
                .sorted(Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
        }
    }
}
