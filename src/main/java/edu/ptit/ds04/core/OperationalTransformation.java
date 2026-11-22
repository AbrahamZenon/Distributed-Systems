package edu.ptit.ds04.core;

import java.util.ArrayList;
import java.util.List;

public class OperationalTransformation {

    public static class TransformResult {
        public final Operation op1Prime;
        public final Operation op2Prime;

        public TransformResult(Operation op1Prime, Operation op2Prime) {
            this.op1Prime = op1Prime;
            this.op2Prime = op2Prime;
        }
    }

    public static String applyOperation(String text, Operation op) {
        if (text == null) text = "";
        if (op == null) return text;

        if (op.getSubOps() != null && !op.getSubOps().isEmpty()) {
            String curr = text;
            for (Operation sub : op.getSubOps()) {
                curr = applyOperation(curr, sub);
            }
            return curr;
        }

        if (OpType.NOOP.equals(op.getOpType()) || op.getLength() == 0) {
            return text;
        }

        if (OpType.INSERT.equals(op.getOpType())) {
            int pos = Math.max(0, Math.min(text.length(), op.getPosition()));
            return text.substring(0, pos) + op.getText() + text.substring(pos);
        } else if (OpType.DELETE.equals(op.getOpType())) {
            int pos = Math.max(0, Math.min(text.length(), op.getPosition()));
            int end = Math.max(pos, Math.min(text.length(), pos + op.getLength()));
            return text.substring(0, pos) + text.substring(end);
        }

        return text;
    }

    public static TransformResult transform(Operation op1, Operation op2, boolean priority) {
        // Handle composite subOps in op1
        if (op1.getSubOps() != null && !op1.getSubOps().isEmpty()) {
            Operation currOp2 = op2.clone();
            List<Operation> resOp1Subs = new ArrayList<>();
            for (Operation sub1 : op1.getSubOps()) {
                TransformResult tr = transform(sub1, currOp2, priority);
                resOp1Subs.add(tr.op1Prime);
                currOp2 = tr.op2Prime;
            }
            Operation resOp1 = op1.clone();
            resOp1.setSubOps(resOp1Subs);
            return new TransformResult(resOp1, currOp2);
        }

        // Handle composite subOps in op2
        if (op2.getSubOps() != null && !op2.getSubOps().isEmpty()) {
            Operation currOp1 = op1.clone();
            List<Operation> resOp2Subs = new ArrayList<>();
            for (Operation sub2 : op2.getSubOps()) {
                TransformResult tr = transform(currOp1, sub2, priority);
                currOp1 = tr.op1Prime;
                resOp2Subs.add(tr.op2Prime);
            }
            Operation resOp2 = op2.clone();
            resOp2.setSubOps(resOp2Subs);
            return new TransformResult(currOp1, resOp2);
        }

        // Handle NOOP
        if (OpType.NOOP.equals(op1.getOpType()) || op1.getLength() == 0) {
            Operation noop = op1.clone();
            noop.setOpType(OpType.NOOP);
            noop.setLength(0);
            return new TransformResult(noop, op2.clone());
        }

        if (OpType.NOOP.equals(op2.getOpType()) || op2.getLength() == 0) {
            Operation noop = op2.clone();
            noop.setOpType(OpType.NOOP);
            noop.setLength(0);
            return new TransformResult(op1.clone(), noop);
        }

        // Case 1: INSERT vs INSERT
        if (OpType.INSERT.equals(op1.getOpType()) && OpType.INSERT.equals(op2.getOpType())) {
            return transformInsertInsert(op1, op2, priority);
        }

        // Case 2: INSERT vs DELETE
        if (OpType.INSERT.equals(op1.getOpType()) && OpType.DELETE.equals(op2.getOpType())) {
            return transformInsertDelete(op1, op2);
        }

        // Case 3: DELETE vs INSERT
        if (OpType.DELETE.equals(op1.getOpType()) && OpType.INSERT.equals(op2.getOpType())) {
            TransformResult tr = transformInsertDelete(op2, op1);
            return new TransformResult(tr.op2Prime, tr.op1Prime);
        }

        // Case 4: DELETE vs DELETE
        if (OpType.DELETE.equals(op1.getOpType()) && OpType.DELETE.equals(op2.getOpType())) {
            return transformDeleteDelete(op1, op2);
        }

        return new TransformResult(op1.clone(), op2.clone());
    }

    private static TransformResult transformInsertInsert(Operation op1, Operation op2, boolean priority) {
        int p1 = op1.getPosition();
        int p2 = op2.getPosition();
        int len1 = op1.getText().length();
        int len2 = op2.getText().length();

        Operation op1Prime = op1.clone();
        Operation op2Prime = op2.clone();

        if (p1 < p2) {
            op2Prime.setPosition(p2 + len1);
        } else if (p1 > p2) {
            op1Prime.setPosition(p1 + len2);
        } else {
            if (priority) {
                op2Prime.setPosition(p2 + len1);
            } else {
                op1Prime.setPosition(p1 + len2);
            }
        }

        return new TransformResult(op1Prime, op2Prime);
    }

    private static TransformResult transformInsertDelete(Operation ins, Operation d) {
        int pIns = ins.getPosition();
        int lenIns = ins.getText().length();
        int pDel = d.getPosition();
        int lenDel = d.getLength();

        Operation insPrime = ins.clone();
        Operation dPrime = d.clone();

        if (pIns <= pDel) {
            dPrime.setPosition(pDel + lenIns);
        } else if (pIns >= pDel + lenDel) {
            insPrime.setPosition(pIns - lenDel);
        } else {
            // Insert strictly inside delete range: pDel < pIns < pDel + lenDel
            insPrime.setPosition(pDel);

            int del1Len = pIns - pDel;
            int del2Len = (pDel + lenDel) - pIns;

            Operation del1 = d.clone();
            del1.setPosition(pDel);
            del1.setLength(del1Len);

            Operation del2 = d.clone();
            del2.setPosition(pDel + lenIns);
            del2.setLength(del2Len);

            List<Operation> subs = new ArrayList<>();
            subs.add(del1);
            subs.add(del2);
            dPrime.setSubOps(subs);
            dPrime.setOpType(OpType.DELETE);
        }

        return new TransformResult(insPrime, dPrime);
    }

    private static TransformResult transformDeleteDelete(Operation op1, Operation op2) {
        int p1 = op1.getPosition();
        int l1 = op1.getLength();
        int p2 = op2.getPosition();
        int l2 = op2.getLength();

        Operation op1Prime = op1.clone();
        Operation op2Prime = op2.clone();

        if (p1 + l1 <= p2) {
            op2Prime.setPosition(p2 - l1);
            return new TransformResult(op1Prime, op2Prime);
        }

        if (p2 + l2 <= p1) {
            op1Prime.setPosition(p1 - l2);
            return new TransformResult(op1Prime, op2Prime);
        }

        // Overlapping deletions
        if (p1 == p2 && l1 == l2) {
            op1Prime.setOpType(OpType.NOOP);
            op1Prime.setLength(0);
            op2Prime.setOpType(OpType.NOOP);
            op2Prime.setLength(0);
            return new TransformResult(op1Prime, op2Prime);
        }

        if (p1 < p2) {
            if (p1 + l1 >= p2 + l2) {
                // op1 encloses op2
                op2Prime.setOpType(OpType.NOOP);
                op2Prime.setLength(0);
                op1Prime.setLength(l1 - l2);
            } else {
                // op1 overlaps start of op2
                op1Prime.setLength(p2 - p1);
                op2Prime.setPosition(p1);
                op2Prime.setLength((p2 + l2) - (p1 + l1));
            }
        } else {
            if (p2 + l2 >= p1 + l1) {
                // op2 encloses op1
                op1Prime.setOpType(OpType.NOOP);
                op1Prime.setLength(0);
                op2Prime.setLength(l2 - l1);
            } else {
                // op2 overlaps start of op1
                op2Prime.setLength(p1 - p2);
                op1Prime.setPosition(p2);
                op1Prime.setLength((p1 + l1) - (p2 + l2));
            }
        }

        return new TransformResult(op1Prime, op2Prime);
    }
}
