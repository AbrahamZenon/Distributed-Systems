package edu.ptit.ds04;

import edu.ptit.ds04.core.OpType;
import edu.ptit.ds04.core.Operation;
import edu.ptit.ds04.core.OperationalTransformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OperationalTransformationTest {

    @Test
    public void testInsertInsertDisjoint() {
        String s0 = "HelloWorld";
        Operation op1 = new Operation(OpType.INSERT, 0, "A");
        Operation op2 = new Operation(OpType.INSERT, 5, "B");

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(op1, op2, true);

        // Path 1: S0 -> op1 -> op2'
        String res1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op1), tr.op2Prime);

        // Path 2: S0 -> op2 -> op1'
        String res2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op2), tr.op1Prime);

        assertEquals("AHelloBWorld", res1);
        assertEquals(res1, res2);
    }

    @Test
    public void testInsertInsertSamePositionPriority() {
        String s0 = "Text";
        Operation opAlice = new Operation(OpType.INSERT, 0, "Alice ");
        Operation opBob = new Operation(OpType.INSERT, 0, "Bob ");

        // Alice priority = true
        OperationalTransformation.TransformResult tr1 =
            OperationalTransformation.transform(opAlice, opBob, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opAlice), tr1.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opBob), tr1.op1Prime);

        assertEquals("Alice Bob Text", path1);
        assertEquals(path1, path2);

        // Bob priority = false (opBob goes first)
        OperationalTransformation.TransformResult tr2 =
            OperationalTransformation.transform(opAlice, opBob, false);

        String path1B = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opAlice), tr2.op2Prime);
        String path2B = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opBob), tr2.op1Prime);

        assertEquals("Bob Alice Text", path1B);
        assertEquals(path1B, path2B);
    }

    @Test
    public void testInsertDeleteDisjoint() {
        String s0 = "0123456789";
        Operation opDel = new Operation(OpType.DELETE, 2, 3); // deletes "234"
        Operation opIns = new Operation(OpType.INSERT, 8, "X");

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(opIns, opDel, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opIns), tr.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opDel), tr.op1Prime);

        assertEquals(path1, path2);
    }

    @Test
    public void testInsertDeleteInsideRangeIntentionPreservation() {
        String s0 = "ABCDE";
        // User 1 inserts 'X' at position 2
        Operation opIns = new Operation(OpType.INSERT, 2, "X");
        // User 2 deletes 'BCD' (pos 1, length 3)
        Operation opDel = new Operation(OpType.DELETE, 1, 3);

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(opIns, opDel, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opIns), tr.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, opDel), tr.op1Prime);

        // 'X' must be preserved, 'BCD' deleted -> "AXE"
        assertEquals("AXE", path1);
        assertEquals(path1, path2);
    }

    @Test
    public void testDeleteDeleteDisjoint() {
        String s0 = "0123456789";
        Operation op1 = new Operation(OpType.DELETE, 1, 2); // deletes "12"
        Operation op2 = new Operation(OpType.DELETE, 6, 2); // deletes "67"

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(op1, op2, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op1), tr.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op2), tr.op1Prime);

        assertEquals("034589", path1);
        assertEquals(path1, path2);
    }

    @Test
    public void testDeleteDeleteOverlapping() {
        String s0 = "0123456789";
        Operation op1 = new Operation(OpType.DELETE, 2, 4); // deletes "2345"
        Operation op2 = new Operation(OpType.DELETE, 4, 4); // deletes "4567"

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(op1, op2, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op1), tr.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op2), tr.op1Prime);

        // Overlap deleted once, combined deletes "234567" -> "0189"
        assertEquals("0189", path1);
        assertEquals(path1, path2);
    }

    @Test
    public void testDeleteDeleteIdentical() {
        String s0 = "HelloWorld";
        Operation op1 = new Operation(OpType.DELETE, 0, 5);
        Operation op2 = new Operation(OpType.DELETE, 0, 5);

        OperationalTransformation.TransformResult tr =
            OperationalTransformation.transform(op1, op2, true);

        String path1 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op1), tr.op2Prime);
        String path2 = OperationalTransformation.applyOperation(
            OperationalTransformation.applyOperation(s0, op2), tr.op1Prime);

        assertEquals("World", path1);
        assertEquals(path1, path2);
    }
}
