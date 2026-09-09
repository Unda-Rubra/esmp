package xdu.undarubra.esmp.core.storage;

import java.sql.Connection;

/** Database-only work. Never retain the connection or access Paper objects from this callback. */
@FunctionalInterface
public interface SqlWork<T> {
    T run(Connection connection) throws Exception;
}
