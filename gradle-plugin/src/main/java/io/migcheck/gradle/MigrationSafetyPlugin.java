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
                    task.getMigrationDir().set(project.provider(extension::getMigrationDir));
                });

        project.getTasks().register("migrationSafetyCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Runs all migration safety checks");
            task.dependsOn(staticTask);
        });
    }
}
