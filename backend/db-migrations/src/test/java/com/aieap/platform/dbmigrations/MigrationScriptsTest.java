package com.aieap.platform.dbmigrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationScriptsTest {

    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Test
    void migrationDirectoryContainsOrderedVersionedScripts() throws IOException {
        assertTrue(Files.isDirectory(MIGRATION_DIR), "Migration directory should exist");

        List<String> fileNames = Files.list(MIGRATION_DIR)
            .filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
            .map(path -> path.getFileName().toString())
            .sorted(Comparator.naturalOrder())
            .toList();

        assertEquals(List.of(
            "V1__initial_schema.sql",
            "V2__seed_data.sql",
            "V3__workflow_tables.sql",
            "V4__workflow_enhancements.sql",
            "V5__chat_persistence.sql",
            "V6__seed_prompt_templates.sql",
            "V7__user_code_identity.sql",
            "V8__email_owner_isolation.sql"
        ), fileNames);
    }
}
