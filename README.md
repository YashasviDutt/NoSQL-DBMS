# custom NoSQL DBMS 

A lightweight Java-based NoSQL DBMS simulation for schema-flexible key/value records. The project supports CRUD operations, exact search, aggregate age queries, and a custom Lucene-inspired query engine for indexed retrieval over dynamic NoSQL-style data.

This project does not depend on Apache Lucene. It implements a small custom search engine inspired by Lucene concepts such as inverted indexes, tokenization, fielded queries, boolean operators, and scored result ordering.

## Project Structure

```text
pom.xml
src/main/java/dbms.java
src/main/resources/dbms_data
src/main/resources/commands
```

`src/main/java/dbms.java` contains the full application:

- `DbmsFiles`: resolves project resource paths.
- `Data_read`: reads and prints the dataset file.
- `Mapping_task`: stores data in memory, processes commands, persists updates.
- `LuceneLikeQueryEngine`: builds inverted indexes and runs Lucene-style queries.
- `dbms`: menu-driven program entry point.

## Data Model

The database is stored in `src/main/resources/dbms_data` as a flat file.

Each record has one main key and any number of sub-key/value fields:

```text
main Key: HARVIJAY
    sub-Key: add, Value: New York"
    sub-Key: batch, Value: b1"
    sub-Key: age, Value: 29"
```

In memory, the data is represented as:

```java
HashMap<String, HashMap<String, String>>
```

This makes the data schema-flexible: different main keys can have different sub-keys.

## Build

```bash
mvn -DskipTests package
```

## Run

```bash
java -jar target/no-sql-dbms-minor-java-1.0-SNAPSHOT.jar
```

The app reads and writes this exact dataset path when run from the project root:

```text
src/main/resources/dbms_data
```

## Menu

```text
Press 1 for data reading
Press 2 for dbms tasks
Press 3 for exit
```

Choose `2` to enter SQL-like DBMS commands.

## Supported Commands

### Create

```text
Create "main key : lucene_temp_student"
```

Creates a new main key with an empty sub-key map.

### Insert

```text
insert into "main key : lucene_temp_student" values {"sub-Key : age, value : 25"}, {"sub-Key : add, value : dehradun"}, {"sub-Key : batch, value : b1"}
```

Adds sub-key/value pairs under an existing main key.

### Update Sub-Keys

```text
update "main Key: lucene_temp_student" values {"sub-Key : age, value : 26"}, {"sub-Key : add, value : kandoli"}
```

Updates or inserts sub-key/value pairs under an existing main key.

### Rename Main Key

```text
update main key : "lucene_temp_student" to "lucene_temp_student_updated"
```

Renames a main key. If the new main key already exists, entries are merged.

### Delete Sub-Key

```text
delete from "main Key: lucene_temp_student_updated" {sub-Key : add}
```

Deletes one or more sub-keys from a main key.

### Delete Main Key

```text
delete "main Key: lucene_temp_student_updated"
```

Deletes the entire main key and all of its sub-keys.

## Exact Search

### Print All Records

```text
search:
```

### Search by Main Key

```text
search "main Key : HARVIJAY"
```

### Search by Sub-Key/Value Conditions

```text
search where {"sub-Key: age, value: 20"}
search where {"sub-Key : age , value : 22"},{"sub-Key : batch , value : b2"}
```

### List Only Main Keys

```text
search where {"sub-Key : batch, value : b3"} list mainKey
```

## Lucene-Inspired Query Engine

The `search query` command uses a custom indexed retrieval engine.

### Indexed Fields

Every main key and sub-key/value pair is indexed:

- `mainKey`
- `age`
- `batch`
- `add`
- Any other dynamic sub-key that appears in the dataset

### Query Examples

```text
search query "mainKey:HARVIJAY"
search query "age:20"
search query "age:22 AND batch:b2"
search query "batch:b2 AND NOT age:212" list mainKey
search query "add:\"New York\"" list mainKey
search query "dehradun OR mussoorie"
```

### Supported Query Features

- Fielded search: `age:20`, `batch:b2`, `mainKey:HARVIJAY`
- Free-text search across all indexed values: `dehradun`
- Boolean operators: `AND`, `OR`, `NOT`
- Parentheses in query parsing
- Quoted values: `add:"New York"`
- Wildcard matching with `*`
- Result scores
- `list mainKey` output mode
- Automatic index rebuild after create, update, rename, and delete

### Example Output

```text
search query "age:22 AND batch:b2"

Lucene-style query: age:22 AND batch:b2
Main Key: arjun (score: 10)
Main Key: x4 (score: 10)
```

## Aggregate Queries

### Average Age of All Records

```text
average age main Key:
```

### Average Age of Selected Main Keys

```text
average age main Key: anjali, nikhil, sparsh
```

### Average Age Grouped by Batch

```text
average age group by batch
```

### Average Age for One Batch

```text
average batch age "b2"
```

## Command File

`src/main/resources/commands` contains a verification command set covering:

- Exact searches
- Lucene-style indexed searches
- Aggregate age queries
- Create
- Insert
- Update
- Rename
- Delete sub-key
- Delete main key
- Dynamic index refresh after mutations

## Complexity Analysis

Let:

- `n` = number of main keys.
- `m` = average number of sub-keys per main key.
- `T` = total indexed tokens across all main keys and values.
- `u` = number of unique indexed terms or values.
- `q` = number of query terms/operators.
- `p` = total postings touched by an indexed query.
- `r` = number of results returned.
- `c` = number of exact `search where` conditions.

### Storage Complexity

| Component | Space Complexity | Notes |
|---|---:|---|
| In-memory database map | `O(n * m)` | Stores main keys and sub-key/value pairs. |
| Lucene-like inverted indexes | `O(T)` | Stores token-to-main-key and field/value-to-main-key mappings. |
| File storage | `O(n * m)` | Flat-file representation in `dbms_data`. |
| Total runtime storage | `O(n * m + T)` | Database plus search indexes. |

### Operation Complexity

| Operation | Time Complexity | Notes |
|---|---:|---|
| Load dataset | `O(n * m)` | Reads all records and sub-keys from the file. |
| Build/rebuild search index | `O(T)` | Tokenizes and indexes all fields. |
| Print all records | `O(n * m)` | Reads every in-memory record. |
| Create main key | `O(1)` in memory, `O(n * m + T)` after persistence | Current implementation rewrites the file and rebuilds the index. |
| Insert sub-keys | `O(k)` in memory, `O(n * m + T)` after persistence | `k` is number of inserted sub-keys. |
| Update sub-keys | `O(k)` in memory, `O(n * m + T)` after persistence | Rewrites file and rebuilds index. |
| Rename main key | `O(1)` average in memory, `O(n * m + T)` after persistence | HashMap remove/put plus full rewrite/reindex. |
| Delete sub-keys | `O(k)` in memory, `O(n * m + T)` after persistence | Deletes `k` sub-keys, then rewrites/reindexes. |
| Delete main key | `O(1)` average in memory, `O(n * m + T)` after persistence | HashMap removal plus full rewrite/reindex. |
| Exact main-key search | `O(1 + m)` average | HashMap lookup plus printing sub-keys. |
| `search where` exact scan | `O(n * c + r * m)` | Scans records and prints matching results. |
| Lucene-style exact field query | `O(q + p + r log r)` typical | Uses inverted index postings, then sorts by score. |
| Lucene-style boolean query | `O(q + p + r log r)` typical | Evaluates postings with AND/OR/NOT. |
| Lucene-style wildcard query | `O(u + p + r log r)` | Wildcard scans indexed terms before collecting postings. |
| Aggregate all ages | `O(n)` | Checks age field for each main key. |
| Aggregate selected keys | `O(s)` | `s` selected main keys. |
| Aggregate by batch | `O(n)` | Scans records and groups by batch. |

### Important Note

The current project prioritizes clarity and demonstration over production-level write performance. Write operations update the in-memory map efficiently, but then the implementation rewrites the full `dbms_data` file and rebuilds the full search index. This keeps the code simple and correct for a minor project, but a production system would use incremental persistence and incremental index updates.

## Verification

The project was verified with:

```bash
mvn -q -DskipTests package
java -jar target/no-sql-dbms-minor-java-1.0-SNAPSHOT.jar
```

The command set in `src/main/resources/commands` was run through the DBMS menu and verified for CRUD, aggregate queries, exact search, and Lucene-inspired indexed search.
