package su.knst.telegram.ai.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.config.DatabaseConfig;

@Singleton
public class Migrator {
    protected static final Logger log = LoggerFactory.getLogger(Migrator.class);

    protected DatabaseConfig config;
    protected Flyway flyway;

    @Inject
    public Migrator(ConfigWorker configWorker) {
        this.config = configWorker.database;
    }

    public void migrate() throws Exception {
        log.info("Migrating database...");

        flyway = Flyway.configure()
                .dataSource(config.url, config.user, config.password)
                .baselineVersion("1.0.0")
                .validateMigrationNaming(true)
                .sqlMigrationSuffixes(".sql")
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .loggers("slf4j")
                .load();

        flyway.migrate();

        log.info("Done");
    }
}
