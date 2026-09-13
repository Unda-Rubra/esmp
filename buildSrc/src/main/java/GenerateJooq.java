import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;

import java.nio.file.Files;
import java.sql.DriverManager;

/** Canonical Liquibase changelogs migrate a disposable real SQLite database before codegen. */
@CacheableTask
public abstract class GenerateJooq extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMigrations();

    @Internal
    public abstract DirectoryProperty getRepositoryRoot();

    @Input
    public abstract Property<String> getTargetPackage();

    @Input
    public abstract Property<String> getIncludes();

    @Input
    public abstract Property<Boolean> getRealColumns();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws Exception {
        var databaseFile = getTemporaryDir().toPath().resolve("schema.sqlite");
        Files.deleteIfExists(databaseFile);
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + databaseFile;
        try (var connection = DriverManager.getConnection(url);
                var resources =
                        new DirectoryResourceAccessor(
                                getRepositoryRoot().dir("schema").get().getAsFile().toPath())) {
            var database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var liquibase =
                    new Liquibase("db/changelog/db.changelog-master.xml", resources, database);
            liquibase.validate();
            liquibase.update(new Contexts(), new LabelExpression());
        }
        String uuidColumns =
                ".*\\.(world_instance_id|bukkit_world_uuid|source_uuid|settlement_id|owner_uuid|player_uuid|actor_uuid|banned_by|offer_id|issuer_uuid|recipient_uuid|event_id|parcel_id|request_id|proposer_uuid|treasury_uuid|governor_uuid|promotion_id|transaction_id|operation_id|quote_id|payer_uuid|application_id|applicant_uuid|decided_by_uuid|acquisition_id|building_id|job_id|transfer_id|cycle_id|decay_operation_id)";
        var forcedTypes = new java.util.ArrayList<ForcedType>();
        forcedTypes.add(
                new ForcedType()
                        .withUserType("java.util.UUID")
                        .withConverter("xdu.undarubra.esmp.core.storage.UuidConverter")
                        .withIncludeExpression(uuidColumns));
        forcedTypes.add(new ForcedType().withName("BIGINT").withIncludeTypes("(?i:.*INT.*)"));
        if (getRealColumns().get())
            forcedTypes.add(
                    new ForcedType().withName("DOUBLE").withIncludeTypes("(?i:REAL|DOUBLE|FLOAT)"));
        GenerationTool.generate(
                new Configuration()
                        .withJdbc(new Jdbc().withDriver("org.sqlite.JDBC").withUrl(url))
                        .withGenerator(
                                new Generator()
                                        .withDatabase(
                                                new Database()
                                                        .withName("EsmpSQLiteDatabase")
                                                        .withIncludes(getIncludes().get())
                                                        .withExcludes("sqlite_.*")
                                                        .withForcedTypes(forcedTypes))
                                        .withGenerate(
                                                new Generate()
                                                        .withDaos(false)
                                                        .withPojos(false)
                                                        .withRecords(true)
                                                        .withGeneratedAnnotation(false)
                                                        .withNullableAnnotation(false)
                                                        .withNonnullAnnotation(false))
                                        .withTarget(
                                                new Target()
                                                        .withPackageName(getTargetPackage().get())
                                                        .withDirectory(
                                                                getOutputDirectory()
                                                                        .get()
                                                                        .getAsFile()
                                                                        .getAbsolutePath())
                                                        .withClean(true))));
    }
}
