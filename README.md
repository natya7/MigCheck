# MigCheck

MigCheck tests whether a database migration can be **rolled back without losing
data** — something neither Flyway nor Liquibase checks for you. A migration that
applies cleanly can still be impossible to reverse safely, and that risk is usually
discovered in production. MigCheck catches it earlier, two ways:

- **Static analysis** — parse the migration SQL (without running it) and flag
  dangerous operations (`DROP TABLE`, `DROP COLUMN`, `TRUNCATE`, type changes),
  assigning a `HIGH` / `MEDIUM` / `LOW` risk.
- **Dynamic testing** — run the migration on a real, throwaway PostgreSQL database,
  seed representative data, then **apply → snapshot → roll back → re-apply →
  snapshot → compare** to see whether any data was actually lost.

It works with both **Flyway** and **Liquibase** behind a single abstraction, and
plugs into **JUnit 5** and **Gradle**.

## Why both static and dynamic?

They catch different things. Static analysis is instant and needs no database, but
it inspects the *forward* migration, which is only a rough proxy for *rollback*
safety. Dynamic testing measures rollback safety directly. The project's evaluation
makes the gap concrete:

| Migration | Static | Dynamic | Actually |
|---|---|---|---|
| rename a column | safe | preserved | preserved ✓ |
| **add a column** | **safe** | **data lost** | **data lost** — static misses it |
| narrow a column's type on rollback | unsafe | data lost | data lost ✓ |
| **drop a column** | **unsafe** | **preserved** | **preserved** — static false alarm |

Static analysis produces both false negatives (an `ADD COLUMN` looks harmless, but
rolling it back destroys the accumulated data) and false positives (a `DROP COLUMN`
looks dangerous, but its rollback loses nothing). Dynamic testing gets all of these
right. A thorough check uses both — static for a fast first pass, dynamic for the
definitive answer.

## Modules

| Module | Purpose |
|---|---|
| `core` | engine abstraction, snapshot + diff, FK-aware seeder, static analyzer, evaluation harness, JUnit 5 extension |
| `flyway` | Flyway implementation of the engine |
| `liquibase` | Liquibase implementation of the engine |
| `gradle-plugin` | `migrationSafety` Gradle plugin |

## Requirements

- JDK 17+
- A running Docker daemon (the dynamic tests start a PostgreSQL container via
  Testcontainers)

## Using the JUnit 5 extension

Annotate a test class with `@MigrationSafetyTest`; the extension starts a PostgreSQL
container and injects a configured `MigrationTester` and `DataSource`. Describe the
migration as a `MigrationScenario` and assert the rollback verdict:

```java
@MigrationSafetyTest
class UsersMigrationTest {

    @Test
    void droppingTheScoreColumnLosesData(MigrationTester tester, DataSource ds) {
        MigrationScenario scenario = new MigrationScenario(
                "add score column",
                "classpath:db/migration",                 // the migrations to apply
                "INSERT INTO users (id, name, score) VALUES (1, 'Ada', 100)",
                "ALTER TABLE users DROP COLUMN score",     // the rollback under test
                DynamicOutcome.DATA_LOST);

        SafetyReport report = tester.run(new FlywayEngine(scenario.migrationsLocation()), ds, scenario);

        assertThat(report.dynamicResult().outcome()).isEqualTo(DynamicOutcome.DATA_LOST);
    }
}
```

If the migrations have foreign keys, the seeder fills parent tables before child
tables automatically — no hand-written seed data needed (pass `null` for the seed).

## Using the Gradle plugin

```groovy
plugins {
    id 'io.migcheck.migration-safety'
}

migrationSafety {
    migrationDir = 'src/main/resources/db/migration'
}
```

Run the static check (fast, no database):

```
./gradlew migrationSafetyStatic
```

It prints a verdict per migration and fails the build on any `HIGH`-risk migration:

```
PASS     V1__create_users.sql
WARNING  V2__widen_email.sql
FAIL     V3__drop_orders.sql
```

For stricter CI, also fail on warnings:

```
./gradlew migrationSafetyStatic --fail-on-warning
```

## Building

```
./gradlew build
```

This compiles every module and runs the test suite, including the integration tests
against a real PostgreSQL container (Docker must be running).

## How the dynamic loop works

```
clean → migrate (UP) → seed data → snapshot A
      → rollback (DOWN) → migrate (UP) → snapshot B
      → compare(A, B)
```

The second UP restores the schema to the same shape as snapshot A, so the
comparison reflects only data loss, not schema differences. Because Flyway
Community has no native undo, the rollback runs the migration's down SQL directly
and un-tracks the migration so it re-applies; Liquibase uses the equivalent of its
own rollback. Either way, the test loop is identical across engines.
