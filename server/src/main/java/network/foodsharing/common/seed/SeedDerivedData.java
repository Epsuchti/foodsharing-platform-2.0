package network.foodsharing.common.seed;

/**
 * Data a module derives from the seed rows at application start (e.g. the search index, built by
 * {@code SearchReindexJob.onStart}). The seed snapshot is taken right after Liquibase, before such start-up steps,
 * so the dev seed reset ({@code POST /api/dev/seed/reset}) calls every bean after restoring the database (A59).
 */
public interface SeedDerivedData {

    /** Short name for the reset log. */
    String derivedDataName();

    /** Rebuilds the derived data from the freshly restored seed rows. */
    void rebuildAfterSeedReset();
}
