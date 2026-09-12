import org.jooq.DSLContext;
import org.jooq.conf.ParseUnknownFunctions;
import org.jooq.meta.sqlite.SQLiteDatabase;

/** Retain SQLite/vendor function calls in CHECK expressions instead of dropping parsed keys. */
public final class EsmpSQLiteDatabase extends SQLiteDatabase {
    @Override protected DSLContext create0() {
        DSLContext context=super.create0();
        context.settings().setParseUnknownFunctions(ParseUnknownFunctions.IGNORE);
        return context;
    }
}
