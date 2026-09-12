package xdu.undarubra.esmp.core.storage;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/** Infrastructure-only schema lifecycle. Never synchronizes an unverified legacy database. */
public final class DatabaseSchema {
    public static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String BASELINE_CHANGELOG = "db/changelog/db.changelog-baseline.xml";

    private DatabaseSchema() {}

    static boolean open(Connection connection) throws Exception {
        Set<String> tables = tables(connection);
        if (tables.contains("databasechangelog")) {
            migrate(connection, false);
            verify(connection);
            return false;
        }
        // Vendor metadata exception: SQLite exposes this legacy version only through PRAGMA.
        int legacyVersion;
        try (var s = connection.createStatement();
                var r = s.executeQuery("PRAGMA user_version")) {
            r.next();
            legacyVersion = r.getInt(1);
        }
        if (tables.isEmpty() && legacyVersion == 0) {
            migrate(connection, false);
            return false;
        }
        if (legacyVersion < 0 || legacyVersion > 1)
            throw new SQLException("Unsupported legacy Core schema: " + legacyVersion);
        if (!tables.contains("world_definitions") || !tables.contains("world_instances"))
            throw new SQLException("Unrecognized partial legacy schema; refusing baseline");
        return true;
    }

    static void finish(Connection connection, boolean legacy) throws Exception {
        if (!legacy) {
            verify(connection);
            return;
        }
        verify(connection, true);
        migrate(connection, true);
        migrate(connection, false);
        verify(connection);
    }

    private static void migrate(Connection connection, boolean baseline) throws Exception {
        migrate(connection, baseline ? BASELINE_CHANGELOG : CHANGELOG, baseline);
    }

    private static void migrate(Connection connection, String changelog, boolean baseline)
            throws Exception {
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        var loader = DatabaseSchema.class.getClassLoader();
        try {
            // Paper's executor threads inherit a server loader; Liquibase SPI lives in this JAR.
            thread.setContextClassLoader(loader);
            liquibase.Scope.child(
                    Map.of(liquibase.Scope.Attr.classLoader.name(), loader),
                    () -> migrateScoped(connection, changelog, baseline));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static void migrateScoped(Connection connection, String changelog, boolean baseline)
            throws Exception {
        var database =
                DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        // Liquibase owns its migration transaction; do not close the worker-owned connection here.
        var resources = new ClassLoaderResourceAccessor(DatabaseSchema.class.getClassLoader());
        try {
            var liquibase = new Liquibase(changelog, resources, database);
            liquibase.validate();
            if (baseline) liquibase.changeLogSync(new Contexts(), new LabelExpression());
            else liquibase.update(new Contexts(), new LabelExpression());
        } finally {
            try {
                resources.close();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }

    /** Compare actual SQLite structure to the schema constructed by the canonical changelog. */
    public static void verify(Connection connection) throws Exception {
        verify(connection, false);
    }

    private static void verify(Connection connection, boolean baseline) throws Exception {
        var path = Files.createTempFile("esmp-schema-reference-", ".db");
        try {
            var config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            var source = new SQLiteDataSource(config);
            source.setUrl("jdbc:sqlite:" + path);
            try (var reference = source.getConnection()) {
                migrate(reference, baseline ? BASELINE_CHANGELOG : CHANGELOG, false);
                var expected = shape(reference);
                var actual = shape(connection);
                if (!expected.equals(actual)) {
                    var differences = new TreeSet<String>();
                    for (String key : expected.keySet())
                        if (!Objects.equals(expected.get(key), actual.get(key)))
                            differences.add(key);
                    for (String key : actual.keySet())
                        if (!expected.containsKey(key)) differences.add(key);
                    throw new SQLException(
                            "Database schema does not match the frozen bridge: " + differences);
                }
                // Deliberate migration-only checks: dynamic policy/account content is not guessed.
                marker(connection, "civilization_schema", "version", 3);
                marker(connection, "economy_metadata", "schema_version", 2);
                marker(connection, "land_value_metadata", "schema_version", 1);
                marker(connection, "claim_pricing_schema", "version", 1);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static void marker(Connection c, String table, String column, int expected)
            throws SQLException {
        // Identifiers are private fixed constants above, never user input.
        try (var s = c.createStatement();
                var r = s.executeQuery("SELECT " + column + " FROM " + table + " WHERE id=1")) {
            if (r.next() && r.getInt(1) != expected)
                throw new SQLException("Unsupported legacy marker: " + table);
        }
    }

    private static Set<String> tables(Connection c) throws SQLException {
        var result = new TreeSet<String>();
        try (var r = c.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (r.next()) {
                String name = r.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if (!name.startsWith("sqlite_")) result.add(name);
            }
        }
        return result;
    }

    private static Map<String, List<String>> shape(Connection c) throws SQLException {
        var result = new TreeMap<String, List<String>>();
        for (String table : tables(c)) {
            if (table.startsWith("databasechangelog")) continue;
            String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
            var columns = new ArrayList<String>();
            // Reviewed SQLite metadata exceptions: JDBC omits CHECK expressions and partial
            // indexes.
            try (var s = c.createStatement();
                    var r = s.executeQuery("PRAGMA table_info(" + quoted + ")")) {
                while (r.next())
                    columns.add(
                            r.getString("name")
                                    + ":"
                                    + affinity(r.getString("type"))
                                    + ":"
                                    + r.getInt("notnull")
                                    + ":"
                                    + normalize(r.getString("dflt_value"))
                                    + ":"
                                    + r.getInt("pk"));
            }
            result.put(table + ".columns", columns);
            var foreign = new ArrayList<String>();
            try (var s = c.createStatement();
                    var r = s.executeQuery("PRAGMA foreign_key_list(" + quoted + ")")) {
                while (r.next())
                    foreign.add(
                            r.getInt("seq")
                                    + ":"
                                    + r.getString("table")
                                    + ":"
                                    + r.getString("from")
                                    + ":"
                                    + r.getString("to")
                                    + ":"
                                    + r.getString("on_update")
                                    + ":"
                                    + r.getString("on_delete"));
            }
            Collections.sort(foreign);
            result.put(table + ".foreign", foreign);
            var indexes = new ArrayList<String>();
            try (var s = c.createStatement();
                    var r = s.executeQuery("PRAGMA index_list(" + quoted + ")")) {
                while (r.next()) {
                    String name = r.getString("name");
                    var fields = new ArrayList<String>();
                    try (var inner = c.createStatement();
                            var x =
                                    inner.executeQuery(
                                            "PRAGMA index_info(\""
                                                    + name.replace("\"", "\"\"")
                                                    + "\")")) {
                        while (x.next()) fields.add(x.getString("name"));
                    }
                    String expression = "";
                    try (var inner =
                            c.prepareStatement(
                                    "SELECT sql FROM sqlite_master WHERE type='index' AND"
                                            + " name=?")) {
                        inner.setString(1, name);
                        try (var x = inner.executeQuery()) {
                            if (x.next()) {
                                String sql = x.getString(1);
                                if (sql != null) {
                                    int where = sql.toLowerCase(Locale.ROOT).indexOf("where");
                                    if (where >= 0) expression = normalize(sql.substring(where));
                                }
                            }
                        }
                    }
                    indexes.add(r.getInt("unique") + ":" + fields + ":" + expression);
                }
            }
            Collections.sort(indexes);
            result.put(table + ".indexes", indexes);
            var checks = new ArrayList<String>();
            try (var q =
                    c.prepareStatement(
                            "SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
                q.setString(1, table);
                try (var r = q.executeQuery()) {
                    if (r.next()) checks.addAll(checks(r.getString(1)));
                }
            }
            Collections.sort(checks);
            result.put(table + ".checks", checks);
        }
        return result;
    }

    private static List<String> checks(String sql) {
        var output = new ArrayList<String>();
        var matcher = Pattern.compile("(?i)\\bCHECK\\s*\\(").matcher(sql);
        while (matcher.find()) {
            int start = matcher.end(), depth = 1;
            boolean string = false;
            for (int i = start; i < sql.length(); i++) {
                char ch = sql.charAt(i);
                if (ch == '\'') {
                    if (string && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        i++;
                        continue;
                    }
                    string = !string;
                }
                if (!string) {
                    if (ch == '(') depth++;
                    else if (ch == ')' && --depth == 0) {
                        output.add(normalize(sql.substring(start, i)));
                        break;
                    }
                }
            }
        }
        return output;
    }

    private static String affinity(String declared) {
        String type = declared.toUpperCase(Locale.ROOT);
        // SQLite type affinity, not vendor spelling: Liquibase renders REAL as FLOAT.
        if (type.contains("INT")) return "INTEGER";
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) return "TEXT";
        if (type.isEmpty() || type.contains("BLOB")) return "BLOB";
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) return "REAL";
        return "NUMERIC";
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.replaceAll("\\s+", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
