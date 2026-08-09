package kr.co.oruda.onsure.platform.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Database-level rollback capability: the schema-level counterpart to {@code ImprovementWorkflowService}'s
 * byte-exact, hash-verified source-file rollback. {@link #snapshot} captures every base table of a single
 * schema into a self-describing, content-hashed JSON manifest. {@link #restore} replays that manifest back
 * into the schema inside a single transaction, and only reports success after re-snapshotting the restored
 * schema and confirming its row counts and content hash match the original recording -- fail-closed, never
 * a silently partial restore.
 *
 * <p>Only text, numeric, boolean, timestamp-with-timezone/without-timezone and json/jsonb column types are
 * understood. Any other column type fails closed with the offending table and column named, rather than
 * silently dropping or guessing at the value.
 */
public final class DatabaseSnapshotService {
    public static final String SNAPSHOT_CONTRACT = "ONSURE_DATABASE_SNAPSHOT_V1";

    // USE_BIG_DECIMAL_FOR_FLOATS is required so that re-reading a written snapshot back into a JsonNode
    // tree (as restore() and the corruption tests do) reconstructs the exact same DecimalNode -- and
    // therefore the exact same serialized bytes -- as the tree that produced the recorded content hash.
    // Without it, a decimal literal like "87.50" parses back as a lossy DoubleNode ("87.5"), and the
    // content hash of a file never actually changes but is recomputed differently, which is exactly the
    // kind of silent drift this snapshot format exists to catch.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public record SnapshotResult(
            String schema, int tableCount, long rowCount, String contentSha256, Path snapshotFile) {}

    public record RestoreResult(
            String schema, int tableCount, long rowCount, String contentSha256, Path snapshotFile) {}

    private record ColumnSpec(String name, int sqlTypeCode, String sqlTypeName) {}

    private record TableSnapshot(String tableName, JsonNode node) {}

    private record SelfReference(List<String> sourceColumns, List<String> referencedColumns) {}

    public SnapshotResult snapshot(Connection connection, String schema, Path snapshotFile) throws Exception {
        requireSchema(schema);
        List<String> tableNames = listBaseTables(connection, schema);

        ArrayNode tablesNode = MAPPER.createArrayNode();
        long totalRows = 0;
        for (String table : tableNames) {
            List<String> orderColumns = orderColumns(connection, schema, table);
            String sql = "SELECT * FROM " + quoteIdent(schema) + "." + quoteIdent(table)
                    + (orderColumns.isEmpty() ? "" : " ORDER BY "
                            + orderColumns.stream().map(DatabaseSnapshotService::quoteIdent)
                                    .collect(Collectors.joining(", ")));

            ObjectNode tableNode = MAPPER.createObjectNode();
            tableNode.put("table_name", table);
            ArrayNode columnsNode = MAPPER.createArrayNode();
            ArrayNode orderByNode = MAPPER.createArrayNode();
            orderColumns.forEach(orderByNode::add);
            ArrayNode rowsNode = MAPPER.createArrayNode();

            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<ColumnSpec> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    ColumnSpec spec = new ColumnSpec(meta.getColumnName(i), meta.getColumnType(i), meta.getColumnTypeName(i));
                    columns.add(spec);
                    ObjectNode columnNode = MAPPER.createObjectNode();
                    columnNode.put("name", spec.name());
                    columnNode.put("sql_type_code", spec.sqlTypeCode());
                    columnNode.put("sql_type_name", spec.sqlTypeName());
                    columnsNode.add(columnNode);
                }
                long rowCount = 0;
                while (rs.next()) {
                    ObjectNode rowNode = MAPPER.createObjectNode();
                    for (int i = 1; i <= columnCount; i++) {
                        ColumnSpec spec = columns.get(i - 1);
                        rowNode.set(spec.name(), readValue(rs, i, spec, table));
                    }
                    rowsNode.add(rowNode);
                    rowCount++;
                }
                totalRows += rowCount;
                tableNode.set("columns", columnsNode);
                tableNode.set("order_by", orderByNode);
                tableNode.put("row_count", rowCount);
                tableNode.set("rows", rowsNode);
            }
            tablesNode.add(tableNode);
        }

        // Canonicalize by round-tripping through text once: Jackson's parser normalizes some numeric
        // literals differently than its tree-node factory methods do (e.g. trailing-zero stripping on
        // BigDecimal), so the in-memory tree built above is not guaranteed to serialize identically to
        // how it will re-serialize after restore() reads it back from the file. Hashing and storing the
        // already-normalized (parsed-once) tree makes every future read-then-rehash of this file a fixed
        // point, instead of chasing every individual Jackson formatting quirk by hand.
        JsonNode canonicalTables = MAPPER.readTree(MAPPER.writeValueAsBytes(tablesNode));
        String contentHash = sha256(MAPPER.writeValueAsBytes(canonicalTables));

        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("contract", SNAPSHOT_CONTRACT);
        manifest.put("schema", schema);
        manifest.put("created_at", Instant.now().toString());
        manifest.put("table_count", tableNames.size());
        manifest.put("row_count", totalRows);
        manifest.put("content_sha256", contentHash);
        manifest.set("tables", canonicalTables);

        writeAtomic(snapshotFile, manifest);
        return new SnapshotResult(schema, tableNames.size(), totalRows, contentHash, snapshotFile);
    }

    public RestoreResult restore(Connection connection, String schema, Path snapshotFile) throws Exception {
        requireSchema(schema);
        JsonNode manifest = readManifest(snapshotFile);

        String manifestSchema = manifest.path("schema").asText(null);
        if (!schema.equals(manifestSchema)) {
            throw new IllegalStateException("SNAPSHOT_SCHEMA_MISMATCH:expected=" + schema + " snapshot=" + manifestSchema);
        }
        JsonNode tablesNode = manifest.path("tables");
        if (!tablesNode.isArray()) throw new IllegalStateException("SNAPSHOT_TABLES_MISSING");

        String recordedHash = manifest.path("content_sha256").asText(null);
        if (recordedHash == null || recordedHash.isBlank()) throw new IllegalStateException("SNAPSHOT_CONTENT_HASH_MISSING");
        String recomputedHash = sha256(MAPPER.writeValueAsBytes(tablesNode));
        if (!recordedHash.equals(recomputedHash)) {
            throw new IllegalStateException("SNAPSHOT_CONTENT_HASH_MISMATCH:the snapshot file is corrupted or was tampered with");
        }
        long recordedRowCount = manifest.path("row_count").asLong(-1);
        if (recordedRowCount < 0) throw new IllegalStateException("SNAPSHOT_ROW_COUNT_MISSING");

        List<TableSnapshot> tables = new ArrayList<>();
        for (JsonNode tableNode : tablesNode) {
            String tableName = tableNode.path("table_name").asText(null);
            if (tableName == null || tableName.isBlank()) throw new IllegalStateException("SNAPSHOT_TABLE_NAME_MISSING");
            tables.add(new TableSnapshot(tableName, tableNode));
        }

        Set<String> currentTables = new LinkedHashSet<>(listBaseTables(connection, schema));
        for (TableSnapshot table : tables) {
            if (!currentTables.contains(table.tableName())) {
                throw new IllegalStateException("SNAPSHOT_TABLE_NOT_FOUND_IN_SCHEMA:" + table.tableName());
            }
        }

        Set<String> tableNameSet = new LinkedHashSet<>();
        for (TableSnapshot table : tables) tableNameSet.add(table.tableName());
        Map<String, Set<String>> parentsOf = foreignKeyParents(connection, schema, tableNameSet);
        List<String> insertOrder = topoSortTables(new ArrayList<>(tableNameSet), parentsOf);
        Map<String, TableSnapshot> byName = new LinkedHashMap<>();
        for (TableSnapshot table : tables) byName.put(table.tableName(), table);

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (!tables.isEmpty()) {
                String truncateList = tables.stream()
                        .map(t -> quoteIdent(schema) + "." + quoteIdent(t.tableName()))
                        .collect(Collectors.joining(", "));
                try (Statement statement = connection.createStatement()) {
                    statement.execute("TRUNCATE TABLE " + truncateList + " CASCADE");
                }
            }
            for (String tableName : insertOrder) {
                insertRows(connection, schema, byName.get(tableName));
            }
            connection.commit();
        } catch (Exception failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }

        Path verificationFile = Files.createTempFile(
                snapshotFile.toAbsolutePath().normalize().getParent(), "onsure-restore-verify-", ".json");
        try {
            SnapshotResult verification = snapshot(connection, schema, verificationFile);
            if (verification.rowCount() != recordedRowCount) {
                throw new IllegalStateException("RESTORE_ROW_COUNT_MISMATCH:expected=" + recordedRowCount
                        + " actual=" + verification.rowCount());
            }
            if (!verification.contentSha256().equals(recordedHash)) {
                throw new IllegalStateException("RESTORE_CONTENT_HASH_MISMATCH:restored schema does not match the snapshot exactly");
            }
        } finally {
            Files.deleteIfExists(verificationFile);
        }

        return new RestoreResult(schema, tables.size(), recordedRowCount, recordedHash, snapshotFile);
    }

    // ---- reading values ----

    private JsonNode readValue(ResultSet rs, int index, ColumnSpec spec, String table) throws SQLException {
        switch (spec.sqlTypeCode()) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> {
                String value = rs.getString(index);
                return value == null ? MAPPER.nullNode() : MAPPER.getNodeFactory().textNode(value);
            }
            case Types.NUMERIC, Types.DECIMAL -> {
                BigDecimal value = rs.getBigDecimal(index);
                return value == null ? MAPPER.nullNode() : MAPPER.getNodeFactory().numberNode(value);
            }
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                long value = rs.getLong(index);
                return rs.wasNull() ? MAPPER.nullNode() : MAPPER.getNodeFactory().numberNode(value);
            }
            case Types.BOOLEAN, Types.BIT -> {
                boolean value = rs.getBoolean(index);
                return rs.wasNull() ? MAPPER.nullNode() : MAPPER.getNodeFactory().booleanNode(value);
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                // pgjdbc reports both "timestamp" and "timestamptz" columns as Types.TIMESTAMP in
                // ResultSetMetaData; the type name is what actually distinguishes them.
                if (isTimestampWithTimeZoneType(spec.sqlTypeName())) {
                    OffsetDateTime value = rs.getObject(index, OffsetDateTime.class);
                    return value == null ? MAPPER.nullNode() : MAPPER.getNodeFactory().textNode(value.toInstant().toString());
                }
                LocalDateTime value = rs.getObject(index, LocalDateTime.class);
                return value == null ? MAPPER.nullNode() : MAPPER.getNodeFactory().textNode(value.toString());
            }
            case Types.OTHER -> {
                if (isJsonType(spec.sqlTypeName())) {
                    String value = rs.getString(index);
                    if (value == null) return MAPPER.nullNode();
                    try {
                        return MAPPER.readTree(value);
                    } catch (IOException malformed) {
                        throw new IllegalStateException(
                                "SNAPSHOT_UNREADABLE_JSON_COLUMN:" + table + "." + spec.name(), malformed);
                    }
                }
                throw new IllegalStateException(
                        "SNAPSHOT_UNSUPPORTED_COLUMN_TYPE:" + table + "." + spec.name() + ":" + spec.sqlTypeName());
            }
            default -> throw new IllegalStateException(
                    "SNAPSHOT_UNSUPPORTED_COLUMN_TYPE:" + table + "." + spec.name() + ":" + spec.sqlTypeName());
        }
    }

    // ---- writing values back ----

    private void insertRows(Connection connection, String schema, TableSnapshot table) throws Exception {
        List<ColumnSpec> columns = new ArrayList<>();
        for (JsonNode c : table.node().path("columns")) {
            columns.add(new ColumnSpec(c.path("name").asText(), c.path("sql_type_code").asInt(), c.path("sql_type_name").asText()));
        }
        List<JsonNode> rows = new ArrayList<>();
        table.node().path("rows").forEach(rows::add);
        if (columns.isEmpty()) {
            if (rows.isEmpty()) return;
            throw new IllegalStateException("SNAPSHOT_TABLE_COLUMNS_MISSING:" + table.tableName());
        }

        List<SelfReference> selfReferences = selfReferences(connection, schema, table.tableName());
        List<JsonNode> orderedRows = orderRowsForInsert(selfReferences, rows);

        String columnList = columns.stream().map(c -> quoteIdent(c.name())).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + quoteIdent(schema) + "." + quoteIdent(table.tableName())
                + " (" + columnList + ") VALUES (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonNode row : orderedRows) {
                for (int i = 0; i < columns.size(); i++) {
                    ColumnSpec spec = columns.get(i);
                    bindValue(statement, i + 1, spec, row.get(spec.name()), table.tableName());
                }
                statement.addBatch();
            }
            if (!orderedRows.isEmpty()) statement.executeBatch();
        }
    }

    private void bindValue(PreparedStatement statement, int index, ColumnSpec spec, JsonNode value, String table)
            throws Exception {
        boolean isNull = value == null || value.isMissingNode() || value.isNull();
        switch (spec.sqlTypeCode()) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> {
                if (isNull) statement.setNull(index, spec.sqlTypeCode());
                else statement.setString(index, value.asText());
            }
            case Types.NUMERIC, Types.DECIMAL -> {
                if (isNull) statement.setNull(index, spec.sqlTypeCode());
                else statement.setBigDecimal(index, value.decimalValue());
            }
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> {
                if (isNull) statement.setNull(index, spec.sqlTypeCode());
                else statement.setLong(index, value.asLong());
            }
            case Types.BOOLEAN, Types.BIT -> {
                if (isNull) statement.setNull(index, spec.sqlTypeCode());
                else statement.setBoolean(index, value.asBoolean());
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                if (isNull) {
                    statement.setNull(index, spec.sqlTypeCode());
                } else if (isTimestampWithTimeZoneType(spec.sqlTypeName())) {
                    statement.setObject(index, OffsetDateTime.ofInstant(Instant.parse(value.asText()), ZoneOffset.UTC));
                } else {
                    statement.setObject(index, LocalDateTime.parse(value.asText()));
                }
            }
            case Types.OTHER -> {
                if (!isJsonType(spec.sqlTypeName())) {
                    throw new IllegalStateException(
                            "RESTORE_UNSUPPORTED_COLUMN_TYPE:" + table + "." + spec.name() + ":" + spec.sqlTypeName());
                }
                // Standard-JDBC-only binding (no org.postgresql.* import): the driver is runtime-scoped only.
                if (isNull) statement.setNull(index, Types.OTHER);
                else statement.setObject(index, MAPPER.writeValueAsString(value), Types.OTHER);
            }
            default -> throw new IllegalStateException(
                    "RESTORE_UNSUPPORTED_COLUMN_TYPE:" + table + "." + spec.name() + ":" + spec.sqlTypeName());
        }
    }

    private static boolean isTimestampWithTimeZoneType(String sqlTypeName) {
        return sqlTypeName != null && sqlTypeName.toLowerCase(Locale.ROOT).contains("timestamptz");
    }

    private static boolean isJsonType(String sqlTypeName) {
        return "jsonb".equalsIgnoreCase(sqlTypeName) || "json".equalsIgnoreCase(sqlTypeName);
    }

    // ---- schema introspection ----

    private static List<String> listBaseTables(Connection connection, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private static List<String> primaryKeyColumns(Connection connection, String schema, String table) throws SQLException {
        Map<Short, String> bySequence = new TreeMap<>();
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(null, schema, table)) {
            while (rs.next()) bySequence.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        return new ArrayList<>(bySequence.values());
    }

    private static List<String> orderColumns(Connection connection, String schema, String table) throws SQLException {
        List<String> primaryKey = primaryKeyColumns(connection, schema, table);
        if (!primaryKey.isEmpty()) return primaryKey;
        String sql = "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return List.of(rs.getString(1));
            }
        }
        return List.of();
    }

    private static Map<String, Set<String>> foreignKeyParents(Connection connection, String schema, Set<String> tableNames)
            throws SQLException {
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        for (String table : tableNames) parents.put(table, new LinkedHashSet<>());
        String sql = """
                SELECT tc.table_name AS child_table, ccu.table_name AS parent_table
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name AND tc.constraint_schema = ccu.constraint_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String child = rs.getString("child_table");
                    String parent = rs.getString("parent_table");
                    if (child.equals(parent) || !tableNames.contains(child) || !tableNames.contains(parent)) continue;
                    parents.get(child).add(parent);
                }
            }
        }
        return parents;
    }

    private static List<String> topoSortTables(List<String> tableNames, Map<String, Set<String>> parentsOf) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String table : tableNames) visitTable(table, parentsOf, visited, visiting, result);
        return result;
    }

    private static void visitTable(
            String table, Map<String, Set<String>> parentsOf, Set<String> visited, Set<String> visiting, List<String> result) {
        if (visited.contains(table)) return;
        if (!visiting.add(table)) throw new IllegalStateException("SNAPSHOT_TABLE_DEPENDENCY_CYCLE:" + table);
        for (String parent : parentsOf.getOrDefault(table, Set.of())) {
            visitTable(parent, parentsOf, visited, visiting, result);
        }
        visiting.remove(table);
        visited.add(table);
        result.add(table);
    }

    /**
     * Self-referencing foreign keys on {@code table}, one {@link SelfReference} per constraint with its
     * source/referenced columns positionally paired via {@code pg_constraint.conkey}/{@code confkey}.
     * information_schema's key_column_usage/constraint_column_usage views do NOT positionally pair
     * composite foreign key columns -- joining them naively cross-products every source column against
     * every referenced column of the same constraint, which for a table like
     * {@code (project_id, target_id, previous_run_id) REFERENCES self(project_id, target_id, run_id)}
     * fabricates a bogus {@code project_id == project_id} "dependency" between any two rows that merely
     * share the same project_id -- exactly the kind of false edge that turns a benign insert order into a
     * reported dependency cycle. pg_constraint's conkey/confkey arrays are correctly ordered instead.
     */
    private static List<SelfReference> selfReferences(Connection connection, String schema, String table) throws SQLException {
        List<SelfReference> references = new ArrayList<>();
        String sql = """
                SELECT sa.attname AS source_column, ta.attname AS referenced_column, k.ord AS ord
                FROM pg_constraint c
                JOIN pg_class rel ON rel.oid = c.conrelid
                JOIN pg_namespace ns ON ns.oid = rel.relnamespace
                CROSS JOIN LATERAL unnest(c.conkey, c.confkey) WITH ORDINALITY AS k(srcattnum, refattnum, ord)
                JOIN pg_attribute sa ON sa.attrelid = c.conrelid AND sa.attnum = k.srcattnum
                JOIN pg_attribute ta ON ta.attrelid = c.confrelid AND ta.attnum = k.refattnum
                WHERE c.contype = 'f' AND c.confrelid = c.conrelid AND ns.nspname = ? AND rel.relname = ?
                ORDER BY c.conname, k.ord
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> sourceColumns = new ArrayList<>();
                List<String> referencedColumns = new ArrayList<>();
                while (rs.next()) {
                    int ord = rs.getInt("ord");
                    if (ord == 1 && !sourceColumns.isEmpty()) {
                        references.add(new SelfReference(List.copyOf(sourceColumns), List.copyOf(referencedColumns)));
                        sourceColumns = new ArrayList<>();
                        referencedColumns = new ArrayList<>();
                    }
                    sourceColumns.add(rs.getString("source_column"));
                    referencedColumns.add(rs.getString("referenced_column"));
                }
                if (!sourceColumns.isEmpty()) {
                    references.add(new SelfReference(List.copyOf(sourceColumns), List.copyOf(referencedColumns)));
                }
            }
        }
        return references;
    }

    /** Orders rows so a self-referencing foreign key always points at a row already earlier in the batch. */
    private static List<JsonNode> orderRowsForInsert(List<SelfReference> selfReferences, List<JsonNode> rows) {
        if (selfReferences.isEmpty() || rows.size() <= 1) return rows;
        int n = rows.size();
        List<Set<Integer>> dependsOn = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dependsOn.add(new LinkedHashSet<>());
        for (int i = 0; i < n; i++) {
            for (SelfReference reference : selfReferences) {
                List<String> sourceValues = tupleOf(rows.get(i), reference.sourceColumns());
                if (sourceValues == null) continue; // MATCH SIMPLE: any null component means the FK is not enforced
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    List<String> referencedValues = tupleOf(rows.get(j), reference.referencedColumns());
                    if (sourceValues.equals(referencedValues)) dependsOn.get(i).add(j);
                }
            }
        }
        List<JsonNode> ordered = new ArrayList<>(n);
        boolean[] visited = new boolean[n];
        boolean[] visiting = new boolean[n];
        for (int i = 0; i < n; i++) visitRow(i, rows, dependsOn, visited, visiting, ordered);
        return ordered;
    }

    /** The row's values for {@code columns}, in order, or {@code null} if any component is missing/null. */
    private static List<String> tupleOf(JsonNode row, List<String> columns) {
        List<String> values = new ArrayList<>(columns.size());
        for (String column : columns) {
            JsonNode value = row.path(column);
            if (value.isMissingNode() || value.isNull()) return null;
            values.add(value.asText());
        }
        return values;
    }

    private static void visitRow(
            int index, List<JsonNode> rows, List<Set<Integer>> dependsOn, boolean[] visited, boolean[] visiting, List<JsonNode> ordered) {
        if (visited[index]) return;
        if (visiting[index]) throw new IllegalStateException("SNAPSHOT_ROW_DEPENDENCY_CYCLE");
        visiting[index] = true;
        for (int dependency : dependsOn.get(index)) visitRow(dependency, rows, dependsOn, visited, visiting, ordered);
        visiting[index] = false;
        visited[index] = true;
        ordered.add(rows.get(index));
    }

    // ---- support ----

    private static void requireSchema(String schema) {
        if (schema == null || !schema.matches("[a-zA-Z_][a-zA-Z0-9_]{0,62}")) {
            throw new IllegalArgumentException("DATABASE_SNAPSHOT_SCHEMA_INVALID");
        }
    }

    private static String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static JsonNode readManifest(Path snapshotFile) throws IOException {
        if (!Files.isRegularFile(snapshotFile)) {
            throw new IllegalStateException("SNAPSHOT_FILE_MISSING:" + snapshotFile);
        }
        JsonNode manifest;
        try {
            manifest = MAPPER.readTree(snapshotFile.toFile());
        } catch (IOException malformed) {
            throw new IllegalStateException("SNAPSHOT_FILE_UNREADABLE:" + snapshotFile, malformed);
        }
        if (manifest == null || !SNAPSHOT_CONTRACT.equals(manifest.path("contract").asText(null))) {
            throw new IllegalStateException("SNAPSHOT_CONTRACT_INVALID");
        }
        return manifest;
    }

    private static void writeAtomic(Path file, ObjectNode content) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "onsure-snapshot-", ".tmp");
        try {
            Files.write(tmp, MAPPER.writeValueAsBytes(content));
            Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            Files.deleteIfExists(tmp);
            throw failure;
        }
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
