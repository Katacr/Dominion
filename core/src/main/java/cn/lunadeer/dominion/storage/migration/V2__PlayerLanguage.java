package cn.lunadeer.dominion.storage.migration;

import cn.lunadeer.dominion.storage.DatabaseType;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;

/**
 * Adds the persistent player menu language used by localized UI renderers.
 */
public final class V2__PlayerLanguage extends AbstractJavaMigration {

    /**
     * Creates the migration for the active database dialect.
     */
    public V2__PlayerLanguage(DatabaseType type) {
        super(type);
    }

    /**
     * Adds an explicit onboarding state without guessing a language for existing players.
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumnIfMissing(connection, "player_name",
                "language " + text() + " NOT NULL DEFAULT 'NONE'");
    }
}
