package io.migcheck.junit;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

public class MigrationSafetyExtension implements BeforeAllCallback, ParameterResolver {

    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
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
        if (type == DataSource.class) {
            return dataSource();
        }
        if (type == MigrationTester.class) {
            return new MigrationTester(new SnapshotEngine(new PostgresDialect(), "public"),
                    new DataComparator());
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }

    private DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(CONTAINER.getJdbcUrl());
        ds.setUser(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        return ds;
    }
}
