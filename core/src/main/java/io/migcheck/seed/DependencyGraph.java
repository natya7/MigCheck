package io.migcheck.seed;

import io.migcheck.dialect.ForeignKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraph {

    private final List<String> tables;
    private final Map<String, Set<String>> dependents = new LinkedHashMap<>();
    private final Map<String, Integer> inDegree = new LinkedHashMap<>();

    public DependencyGraph(Collection<String> tables, Collection<ForeignKey> foreignKeys) {
        this.tables = new ArrayList<>(tables);
        for (String table : this.tables) {
            dependents.put(table, new LinkedHashSet<>());
            inDegree.put(table, 0);
        }
        for (ForeignKey fk : foreignKeys) {
            if (fk.table().equals(fk.referencedTable())) {
                continue;
            }
            if (!inDegree.containsKey(fk.table()) || !inDegree.containsKey(fk.referencedTable())) {
                continue;
            }
            if (dependents.get(fk.referencedTable()).add(fk.table())) {
                inDegree.merge(fk.table(), 1, Integer::sum);
            }
        }
    }

    public List<String> topologicalOrder() {
        Map<String, Integer> remaining = new LinkedHashMap<>(inDegree);
        Deque<String> ready = new ArrayDeque<>();
        for (String table : tables) {
            if (remaining.get(table) == 0) {
                ready.add(table);
            }
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String table = ready.poll();
            order.add(table);
            for (String dependent : dependents.get(table)) {
                if (remaining.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() != tables.size()) {
            List<String> cyclic = new ArrayList<>();
            for (String table : tables) {
                if (remaining.get(table) > 0) {
                    cyclic.add(table);
                }
            }
            throw new CyclicDependencyException(cyclic);
        }
        return order;
    }
}
