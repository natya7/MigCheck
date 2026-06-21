package io.migcheck.dialect;

import java.util.List;

public record CompositeForeignKey(String childTable, String parentTable,
                                  List<String> childColumns, List<String> parentColumns) {
}
