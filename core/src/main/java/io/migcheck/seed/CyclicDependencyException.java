package io.migcheck.seed;

import java.util.List;

public class CyclicDependencyException extends RuntimeException {

    private final List<String> tables;

    public CyclicDependencyException(List<String> tables) {
        super("Cyclic foreign key dependency among tables: " + tables);
        this.tables = List.copyOf(tables);
    }

    public List<String> tables() {
        return tables;
    }
}
