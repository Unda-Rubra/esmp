package xdu.undarubra.esmp.core.storage;

import org.jooq.DSLContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** The only application entry point for resource SQL; gameplay queries use generated bindings. */
public final class SqlMigrations {
    private SqlMigrations() {}

    public static void run(DSLContext context, String resource) {
        String path = resource.startsWith("/") ? resource : "/" + resource;
        try (var input = SqlMigrations.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing migration " + path);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            // Xerial's SQLite executeUpdate uses sqlite3_exec for the complete script.
            // Keep native DDL/check constraints intact rather than translating vendor syntax.
            context.connection(
                    connection -> {
                        try (var statement = connection.createStatement()) {
                            statement.executeUpdate(script);
                        }
                    });
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read migration " + path, failure);
        }
    }
}
