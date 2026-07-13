package cn.lunadeer.dominion.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Limits JDBC metadata lookups to the database/schema selected by the connection.
 */
public final class DatabaseMetadataScope {

    private DatabaseMetadataScope() {
    }

    public static String catalog(Connection connection) throws SQLException {
        return connection.getCatalog();
    }

    public static String schema(Connection connection, DatabaseType type) throws SQLException {
        return type == DatabaseType.PGSQL ? connection.getSchema() : null;
    }
}
