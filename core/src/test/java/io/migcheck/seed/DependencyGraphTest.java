package io.migcheck.seed;

import io.migcheck.dialect.ForeignKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphTest {

    @Test
    void ordersParentsBeforeChildren() {
        List<String> tables = List.of("orders", "customers", "countries");
        List<ForeignKey> fks = List.of(
                new ForeignKey("customers", "countries", "country_id"),
                new ForeignKey("orders", "customers", "customer_id"));

        List<String> order = new DependencyGraph(tables, fks).topologicalOrder();

        assertThat(order).containsExactlyInAnyOrderElementsOf(tables);
        assertThat(order.indexOf("countries")).isLessThan(order.indexOf("customers"));
        assertThat(order.indexOf("customers")).isLessThan(order.indexOf("orders"));
    }

    @Test
    void detectsCycle() {
        List<String> tables = List.of("a", "b");
        List<ForeignKey> fks = List.of(
                new ForeignKey("a", "b", "b_id"),
                new ForeignKey("b", "a", "a_id"));

        assertThatThrownBy(() -> new DependencyGraph(tables, fks).topologicalOrder())
                .isInstanceOf(CyclicDependencyException.class)
                .hasMessageContaining("a")
                .hasMessageContaining("b");
    }

    @Test
    void allowsSelfReference() {
        List<String> tables = List.of("employee");
        List<ForeignKey> fks = List.of(new ForeignKey("employee", "employee", "manager_id"));

        assertThat(new DependencyGraph(tables, fks).topologicalOrder())
                .containsExactly("employee");
    }
}
