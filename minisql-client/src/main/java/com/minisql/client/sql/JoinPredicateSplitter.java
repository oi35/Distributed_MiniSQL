package com.minisql.client.sql;

import com.minisql.common.proto.ColumnSchema;
import com.minisql.common.proto.TableSchema;
import net.sf.jsqlparser.expression.Expression;

import java.util.ArrayList;
import java.util.List;

public final class JoinPredicateSplitter {

    private JoinPredicateSplitter() {}

    public static SplitResult split(Predicate predicate, TableSchema leftSchema, TableSchema rightSchema) {
        if (predicate == null || predicate instanceof Predicate.AlwaysTrue) {
            return new SplitResult(Predicate.alwaysTrue(), Predicate.alwaysTrue(), Predicate.alwaysTrue());
        }

        List<Predicate> leftOnly = new ArrayList<>();
        List<Predicate> rightOnly = new ArrayList<>();
        List<Predicate> postJoin = new ArrayList<>();

        collectLeaves(predicate, leftSchema, rightSchema, leftOnly, rightOnly, postJoin);

        return new SplitResult(
                combine(leftOnly),
                combine(rightOnly),
                combine(postJoin));
    }

    private static void collectLeaves(Predicate pred,
                                       TableSchema leftSchema,
                                       TableSchema rightSchema,
                                       List<Predicate> leftOnly,
                                       List<Predicate> rightOnly,
                                       List<Predicate> postJoin) {
        if (pred instanceof Predicate.And) {
            Predicate.And and = (Predicate.And) pred;
            collectLeaves(and.left, leftSchema, rightSchema, leftOnly, rightOnly, postJoin);
            collectLeaves(and.right, leftSchema, rightSchema, leftOnly, rightOnly, postJoin);
        } else if (pred instanceof Predicate.Comparison) {
            Predicate.Comparison cmp = (Predicate.Comparison) pred;
            if (hasColumn(leftSchema, cmp.column)) {
                leftOnly.add(pred);
            } else if (hasColumn(rightSchema, cmp.column)) {
                rightOnly.add(pred);
            } else {
                postJoin.add(pred);
            }
        } else if (pred instanceof Predicate.Or) {
            classifyOr((Predicate.Or) pred, leftSchema, rightSchema, leftOnly, rightOnly, postJoin);
        } else {
            postJoin.add(pred);
        }
    }

    private static void classifyOr(Predicate.Or or,
                                     TableSchema leftSchema,
                                     TableSchema rightSchema,
                                     List<Predicate> leftOnly,
                                     List<Predicate> rightOnly,
                                     List<Predicate> postJoin) {
        Owner leftOwner = classifyOwner(or.left, leftSchema, rightSchema);
        Owner rightOwner = classifyOwner(or.right, leftSchema, rightSchema);

        if (leftOwner == rightOwner && leftOwner == Owner.LEFT) {
            leftOnly.add(or);
        } else if (leftOwner == rightOwner && leftOwner == Owner.RIGHT) {
            rightOnly.add(or);
        } else {
            postJoin.add(or);
        }
    }

    private static Owner classifyOwner(Predicate pred, TableSchema leftSchema, TableSchema rightSchema) {
        if (pred instanceof Predicate.Comparison) {
            Predicate.Comparison cmp = (Predicate.Comparison) pred;
            if (hasColumn(leftSchema, cmp.column)) return Owner.LEFT;
            if (hasColumn(rightSchema, cmp.column)) return Owner.RIGHT;
            return Owner.BOTH;
        }
        if (pred instanceof Predicate.And) {
            Owner l = classifyOwner(((Predicate.And) pred).left, leftSchema, rightSchema);
            Owner r = classifyOwner(((Predicate.And) pred).right, leftSchema, rightSchema);
            return mergeOwner(l, r);
        }
        if (pred instanceof Predicate.Or) {
            Owner l = classifyOwner(((Predicate.Or) pred).left, leftSchema, rightSchema);
            Owner r = classifyOwner(((Predicate.Or) pred).right, leftSchema, rightSchema);
            return mergeOwner(l, r);
        }
        return Owner.BOTH;
    }

    private static Owner mergeOwner(Owner a, Owner b) {
        if (a == b) return a;
        return Owner.BOTH;
    }

    private static Predicate combine(List<Predicate> predicates) {
        if (predicates.isEmpty()) return Predicate.alwaysTrue();
        Predicate result = predicates.get(0);
        for (int i = 1; i < predicates.size(); i++) {
            result = Predicate.and(result, predicates.get(i));
        }
        return result;
    }

    private static boolean hasColumn(TableSchema schema, String name) {
        for (ColumnSchema col : schema.getColumnsList()) {
            if (col.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private enum Owner { LEFT, RIGHT, BOTH }

    public static final class SplitResult {
        public final Predicate leftOnly;
        public final Predicate rightOnly;
        public final Predicate postJoin;

        SplitResult(Predicate leftOnly, Predicate rightOnly, Predicate postJoin) {
            this.leftOnly = leftOnly;
            this.rightOnly = rightOnly;
            this.postJoin = postJoin;
        }
    }
}
