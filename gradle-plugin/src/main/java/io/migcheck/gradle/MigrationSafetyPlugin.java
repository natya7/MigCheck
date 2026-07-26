package io.migcheck.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class MigrationSafetyPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        MigrationSafetyExtension extension = project.getExtensions()
                .create("migrationSafety", MigrationSafetyExtension.class);

        TaskProvider<MigrationSafetyStaticTask> staticTask = project.getTasks()
                .register("migrationSafetyStatic", MigrationSafetyStaticTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Analyzes migration SQL for rollback-safety risks");
                    task.getMigrationDir().set(project.provider(extension::getMigrationDir));
                    task.getFailOnWarning().convention(false);
                });

        TaskProvider<MigrationSafetyDynamicTask> dynamicTask = project.getTasks()
                .register("migrationSafetyTest", MigrationSafetyDynamicTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Runs the UP-DOWN-UP rollback safety check against a database");
                    task.getMigrationDir().set(project.provider(extension::getMigrationDir));
                    task.getJdbcUrl().set(project.provider(extension::getJdbcUrl));
                    task.getUsername().set(project.provider(extension::getUsername));
                    task.getPassword().set(project.provider(extension::getPassword));
                    task.getRollbackSql().set(project.provider(extension::getRollbackSql));
                    task.getDatabase().set(project.provider(extension::getDatabase));
                    task.getSchema().set(project.provider(extension::getSchema));
                    task.onlyIf("migrationSafety.jdbcUrl is configured",
                            t -> task.getJdbcUrl().isPresent());
                });

        project.getTasks()
                .register("migrationSafetyCertify", MigrationSafetyCertifyTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Certifies every migration's rollback across the history");
                    task.getMigrationDir().set(project.provider(extension::getMigrationDir));
                    task.getRollbackDir().set(project.provider(extension::getRollbackDir));
                    task.getJdbcUrl().set(project.provider(extension::getJdbcUrl));
                    task.getUsername().set(project.provider(extension::getUsername));
                    task.getPassword().set(project.provider(extension::getPassword));
                    task.getDatabase().set(project.provider(extension::getDatabase));
                    task.getSchema().set(project.provider(extension::getSchema));
                    task.onlyIf("migrationSafety.jdbcUrl is configured",
                            t -> task.getJdbcUrl().isPresent());
                });

        project.getTasks().register("migrationSafetyCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Runs all migration safety checks");
            task.dependsOn(staticTask);
            task.dependsOn(dynamicTask);
        });
    }
}
