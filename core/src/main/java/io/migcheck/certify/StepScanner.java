package io.migcheck.certify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class StepScanner {

    private static final Pattern MIGRATION = Pattern.compile("V(.+?)__(.+)\\.sql");
    private static final Pattern ROLLBACK = Pattern.compile("U(.+?)__.+\\.sql");
    private static final Pattern RESTORE = Pattern.compile("R(.+?)__.+\\.sql");

    private StepScanner() {
    }

    public static List<MigrationStep> scan(Path migrationDir, Path rollbackDir) {
        Map<String, Path> rollbacks = new LinkedHashMap<>();
        Map<String, Path> restores = new LinkedHashMap<>();
        for (Path file : list(rollbackDir)) {
            String name = file.getFileName().toString();
            Matcher rollback = ROLLBACK.matcher(name);
            if (rollback.matches()) {
                rollbacks.put(rollback.group(1), file);
                continue;
            }
            Matcher restore = RESTORE.matcher(name);
            if (restore.matches()) {
                restores.put(restore.group(1), file);
            }
        }
        List<MigrationStep> steps = new ArrayList<>();
        for (Path file : list(migrationDir)) {
            Matcher m = MIGRATION.matcher(file.getFileName().toString());
            if (m.matches()) {
                steps.add(new MigrationStep(m.group(1), m.group(2), file,
                        rollbacks.get(m.group(1)), restores.get(m.group(1))));
            }
        }
        steps.sort(Comparator.comparing(MigrationStep::version, StepScanner::compareVersions));
        return steps;
    }

    private static int compareVersions(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        for (int i = 0; i < Math.max(as.length, bs.length); i++) {
            long av = i < as.length ? Long.parseLong(as[i]) : 0;
            long bv = i < bs.length ? Long.parseLong(bs[i]) : 0;
            if (av != bv) {
                return Long.compare(av, bv);
            }
        }
        return 0;
    }

    private static List<Path> list(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
