package org.apache.phoenix.calcite;

import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.compile.ColumnProjector;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.RowProjector;
import org.apache.phoenix.iterate.ResultIterator;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PDate;
import org.apache.phoenix.schema.types.PDateArray;
import org.apache.phoenix.schema.types.PLong;
import org.apache.phoenix.schema.types.PLongArray;
import org.apache.phoenix.schema.types.PTime;
import org.apache.phoenix.schema.types.PTimeArray;
import org.apache.phoenix.schema.types.PTimestamp;
import org.apache.phoenix.schema.types.PTimestampArray;
import org.apache.phoenix.schema.types.PUnsignedDate;
import org.apache.phoenix.schema.types.PUnsignedDateArray;
import org.apache.phoenix.schema.types.PUnsignedTime;
import org.apache.phoenix.schema.types.PUnsignedTimeArray;
import org.apache.phoenix.schema.types.PUnsignedTimestamp;
import org.apache.phoenix.schema.types.PUnsignedTimestampArray;
import org.apache.phoenix.schema.types.PhoenixArray;

import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Methods used by code generated by Calcite.
 */
public class CalciteRuntime {
    public static Enumerable<Object> toEnumerable2(final ResultIterator iterator, final RowProjector rowProjector) {
        return new AbstractEnumerable<Object>() {
            @Override
            public Enumerator<Object> enumerator() {
                return toEnumerator(iterator, rowProjector);
            }
        };
    }

    public static Enumerable<Object> toEnumerable(final QueryPlan plan) {
        try {
            return toEnumerable2(plan.iterator(), plan.getProjector());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Enumerator<Object> toEnumerator(final ResultIterator iterator, final RowProjector rowProjector) {
        final int count = rowProjector.getColumnCount();
        return new Enumerator<Object>() {
            Object current;
            private final ImmutableBytesWritable ptr = new ImmutableBytesWritable();

            @Override
            public Object current() {
                return current;
            }

            @Override
            public boolean moveNext() {
                try {
                    final Tuple tuple = iterator.next();
                    if (tuple == null) {
                        current = null;
                        return false;
                    }
                    if (count == 1) {
                        ColumnProjector projector = rowProjector.getColumnProjector(0);
                        current = project(tuple, projector);
                        return true;
                    }
                    Object[] array = new Object[count];
                    for (int i = 0; i < count; i++) {
                        ColumnProjector projector = rowProjector.getColumnProjector(i);
                        array[i] = project(tuple, projector);
                    }
                    current = array;
                    return true;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
            private Object project(Tuple tuple, ColumnProjector projector) throws SQLException {
                @SuppressWarnings("rawtypes")
                PDataType type = projector.getExpression().getDataType();
                if (PDataType.equalsAny(
                        type,
                        PUnsignedDate.INSTANCE,
                        PDate.INSTANCE,
                        PUnsignedTime.INSTANCE,
                        PTime.INSTANCE)) {
                    type = PLong.INSTANCE;
                }else if (PDataType.equalsAny(
                        type,
                        PUnsignedDateArray.INSTANCE,
                        PDateArray.INSTANCE,
                        PUnsignedTimeArray.INSTANCE,
                        PTimeArray.INSTANCE)) {
                    type = PLongArray.INSTANCE;
                }
                Object value = projector.getValue(tuple, type, ptr);
                if (value != null) {
                    if (type.isArrayType()) {
                        value = ((PhoenixArray) value).getArray();
                    }
                    if (PDataType.equalsAny(
                                type,
                                PUnsignedTimestamp.INSTANCE,
                                PTimestamp.INSTANCE)) {
                        value = ((Timestamp) value).getTime();
                    } else if (PDataType.equalsAny(
                                type,
                                PUnsignedTimestampArray.INSTANCE,
                                PTimestampArray.INSTANCE)) {
                        Timestamp[] array = (Timestamp[]) value;
                        long[] newArray = new long[array.length];
                        for (int i = 0; i < array.length; i++) {
                            newArray[i] = array[i].getTime();
                        }
                        value = newArray;
                    }
                }
                
                return value;
            }

            @Override
            public void reset() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                try {
                    iterator.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}