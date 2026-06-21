package io.migcheck.junit;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.MySqlDialect;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

public class MigrationSafetyExtension implements BeforeAllCallback, ParameterResolver {

    private static PostgreSQLContainer<?> postgres;
    private static MySQLContainer<?> mysql;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (database(context) == Database.MYSQL) {
            mysql();
        } else {
            postgres();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == DataSource.class || type == MigrationTester.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        Database database = database(extensionContext);
        if (type == DataSource.class) {
            return dataSource(database);
        }
        if (type == MigrationTester.class) {
            return tester(database);
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }

    private Database database(ExtensionContext context) {
        MigrationSafetyTest annotation =
                context.getRequiredTestClass().getAnnotation(MigrationSafetyTest.class);
        return annotation == null ? Database.POSTGRES : annotation.database();
    }

    private DataSource dataSource(Database database) {
        if (database == Database.MYSQL) {
            MysqlDataSource ds = new MysqlDataSource();
            ds.setUrl(mysql().getJdbcUrl());
            ds.setUser(mysql().getUsername());
            ds.setPassword(mysql().getPassword());
            return ds;
        }
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres().getJdbcUrl());
        ds.setUser(postgres().getUsername());
        ds.setPassword(postgres().getPassword());
        return ds;
    }

    private MigrationTester tester(Database database) {
        if (database == Database.MYSQL) {
            MySqlDialect dialect = new MySqlDialect();
            String schema = mysql().getDatabaseName();
            return new MigrationTester(new SnapshotEngine(dialect, schema),
                    new DataComparator(),
                    new AutoSeeder(dialect, schema, 3));
        }
        PostgresDialect dialect = new PostgresDialect();
        return new MigrationTester(new SnapshotEngine(dialect, "public"),
                new DataComparator(),
                new AutoSeeder(dialect, "public", 3));
    }

    private static synchronized PostgreSQLContainer<?> postgres() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
        return postgres;
    }

    private static synchronized MySQLContainer<?> mysql() {
        if (mysql == null) {
            mysql = new MySQLContainer<>("mysql:8.0");
            mysql.start();
        }
        return mysql;
    }
}
