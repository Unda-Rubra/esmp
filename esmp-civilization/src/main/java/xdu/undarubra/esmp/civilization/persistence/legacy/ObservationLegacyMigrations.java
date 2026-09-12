package xdu.undarubra.esmp.civilization.persistence.legacy;

import org.jooq.DSLContext;

import xdu.undarubra.esmp.core.storage.SqlMigrations;

import java.sql.SQLException;

/** Original observation migrations, used only before the Liquibase handoff. */
public final class ObservationLegacyMigrations {
    private ObservationLegacyMigrations() {}

    public static void activity(DSLContext context) throws SQLException {
        SqlMigrations.run(context, "db/civilization/V001__activity.sql");
    }

    public static void land(DSLContext context) throws SQLException {
        SqlMigrations.run(context, "db/civilization/V001__land_value.sql");
    }
}
