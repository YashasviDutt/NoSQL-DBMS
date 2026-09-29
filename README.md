# NoSQL DBMS (Java)

A lightweight, menu-driven NoSQL database written in plain Java. It stores schema-flexible key/value records in a flat file and supports CRUD, exact search, average-age aggregates, and a custom **Lucene-inspired** query engine (inverted indexes, fielded queries, boolean operators, wildcards, and scored results).

> No external dependencies. Apache Lucene is **not** used; the search engine is implemented from scratch and only borrows Lucene's ideas.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Web Version and Deployment](#web-version-and-deployment)
- [Project Structure](#project-structure)
- [Data Model](#data-model)
- [Command Reference](#command-reference)
  - [CRUD](#crud-commands)
  - [Exact Search](#exact-search)
  - [Lucene-Inspired Query Engine](#lucene-inspired-query-engine)
  - [Aggregate Queries](#aggregate-queries)
- [Try It: Command File](#try-it-command-file)
- [Complexity Analysis](#complexity-analysis)
- [Limitations](#limitations)

## Features

- **Schema-flexible records**: each main key can have its own set of sub-keys.
- **SQL-like CRUD commands**: create, insert, update, rename, and delete.
- **Exact search**: by main key, by one or more sub-key/value conditions, or list only main keys.
- **Indexed search**: `search query` supports fielded terms, free text, `AND` / `OR` / `NOT`, parentheses, quoted phrases, `*` wildcards, and ranked scores.
- **Aggregates**: average age overall, for selected keys, grouped by batch, or for a single batch.
- **Automatic re-indexing** after every create, update, rename, or delete.
- **Persistent**: every change is written back to a flat file.

## Requirements

- Java 8 or newer (JDK)
- Maven 3.x

## Quick Start

```bash
git clone https://github.com/YashasviDutt/NoSQL-DBMS.git
cd NoSQL-DBMS

# build
mvn -DskipTests package

# run
java -jar target/no-sql-dbms-minor-java-1.0-SNAPSHOT.jar
```

You can also run it without building a jar:

```bash
mvn compile exec:java
```

Run from the project root. The app locates `pom.xml` and reads/writes `src/main/resources/dbms_data`.

> **Heads up:** create/update/delete commands modify `dbms_data` in place. Use `git checkout src/main/resources/dbms_data` to restore the original dataset.

### Menu

```text
Press 1 for data reading
Press 2 for dbms tasks
Press 3 for exit
```

Choose `2` to enter commands, then type `quit` to return to the menu.

### Sample Session

```text
Enter SQL-like query or type 'quit' to exit:
search query "age:22 AND batch:b2"

Lucene-style query: age:22 AND batch:b2
Main Key: arjun (score: 10)
Main Key: x4 (score: 10)
```

## Web Version and Deployment

The project also ships with a small web front end (`WebServer.java`, built on the JDK's built-in HTTP server, so no extra dependencies).

| Endpoint | Purpose |
|---|---|
| `GET /` | Web UI with a command box, clickable examples, and output panel |
| `POST /api/query` | Body is one DBMS command; the response is its output |
| `POST /api/reset` | Restores the original sample dataset |
| `GET /health` | Health check |

Run it locally (after `mvn -DskipTests package`):

```bash
java -cp target/no-sql-dbms-minor-java-1.0-SNAPSHOT.jar WebServer
# open http://localhost:8080  (set PORT to change it)
```

Or with Docker:

```bash
docker build -t nosql-dbms .
docker run -p 8080:8080 nosql-dbms
```

### Deploy on Render

1. Push this repo to GitHub.
2. In Render, choose **New +** > **Blueprint** and select the repo. Render reads `render.yaml` and builds the `Dockerfile`.
   (Or create a **Web Service**, pick **Docker** as the runtime, and set the health check path to `/health`.)
3. Once deployed, open the `*.onrender.com` URL.

On the free plan the service sleeps after inactivity (the first request can take about a minute), and the filesystem is reset on every restart, so data returns to the sample dataset. Everyone shares one dataset; use **Reset data** to restore it.

## Project Structure

```text
.
├── pom.xml
├── Dockerfile                    # container build for deployment
├── render.yaml                   # Render blueprint
├── src/main/
│   ├── java/
│   │   ├── dbms.java             # DBMS + console menu
│   │   └── WebServer.java        # HTTP API + web UI server
│   └── resources/
│       ├── dbms_data             # the database (flat file)
│       ├── commands              # sample commands to try
│       └── web/index.html        # web UI
└── historical files/             # earlier versions kept for reference
```

`src/main/java/dbms.java` contains:

| Class | Responsibility |
|---|---|
| `DbmsFiles` | Locates the project root and resource files. |
| `Data_read` | Reads and prints the dataset file. |
| `Mapping_task` | Holds data in memory, parses commands, persists changes. |
| `LuceneLikeQueryEngine` | Builds inverted indexes and evaluates indexed queries. |
| `SearchResult` | A matched main key plus its score. |
| `dbms` | Menu-driven entry point. |

## Data Model

Each record has one **main key** and any number of **sub-key/value** fields, stored in `dbms_data`:

```text
main Key: HARVIJAY
    sub-Key: add, Value: New York"
    sub-Key: batch, Value: b1"
    sub-Key: age, Value: 29"
```

In memory it is a `HashMap<String, HashMap<String, String>>`, so different main keys can have different sub-keys.

## Command Reference

### CRUD Commands

| Action | Syntax |
|---|---|
| Create main key | `Create "main key : lucene_temp_student"` |
| Insert sub-keys | `insert into "main key : lucene_temp_student" values {"sub-Key : age, value : 25"}, {"sub-Key : add, value : dehradun"}` |
| Update sub-keys | `update "main Key: lucene_temp_student" values {"sub-Key : age, value : 26"}` |
| Rename main key | `update main key : "lucene_temp_student" to "lucene_temp_student_updated"` |
| Delete sub-key(s) | `delete from "main Key: lucene_temp_student_updated" {sub-Key : add}` |
| Delete main key | `delete "main Key: lucene_temp_student_updated"` |

Notes:

- **Create** adds a main key with no sub-keys.
- **Insert** adds sub-keys to an existing main key.
- **Update** updates or inserts sub-keys on an existing main key.
- **Rename** merges entries if the new main key already exists.
- **Delete main key** removes the key and all its sub-keys.

### Exact Search

```text
search:                                                            # print all records
search "main Key : HARVIJAY"                                       # by main key
search where {"sub-Key: age, value: 20"}                           # by one condition
search where {"sub-Key : age , value : 22"},{"sub-Key : batch , value : b2"}   # multiple conditions
search where {"sub-Key : batch, value : b3"} list mainKey          # main keys only
```

### Lucene-Inspired Query Engine

`search query "<query>"` runs against inverted indexes built from every main key and every sub-key/value pair (`mainKey`, `age`, `batch`, `add`, and any other sub-key in the data).

```text
search query "mainKey:HARVIJAY"
search query "age:20"
search query "age:22 AND batch:b2"
search query "batch:b2 AND NOT age:212" list mainKey
search query "add:\"New York\"" list mainKey
search query "dehradun OR mussoorie"
```

| Feature | Example |
|---|---|
| Fielded search | `age:20`, `batch:b2`, `mainKey:HARVIJAY` |
| Free-text search across all values | `dehradun` |
| Boolean operators | `AND`, `OR`, `NOT` |
| Grouping | `(age:20 OR age:22) AND batch:b2` |
| Quoted values | `add:"New York"` |
| Wildcards | `*` |
| Ranked results | each result shows a `score` |
| Compact output | append `list mainKey` |

### Aggregate Queries

```text
average age main Key:                              # all records
average age main Key: anjali, nikhil, sparsh       # selected main keys
average age group by batch                         # grouped by batch
average batch age "b2"                             # one batch
```

## Try It: Command File

`src/main/resources/commands` is a ready-made script covering exact search, indexed search, aggregates, and the full create → insert → update → rename → delete cycle (including index refresh after each change). Paste the commands into the `dbms tasks` prompt one at a time. The script cleans up after itself by deleting the temporary record it creates.

## Complexity Analysis

Let:

- `n` = number of main keys
- `m` = average sub-keys per main key
- `T` = total indexed tokens
- `u` = unique indexed terms/values
- `q` = query terms/operators
- `p` = postings touched by an indexed query
- `r` = results returned
- `c` = number of `search where` conditions
- `k` = sub-keys inserted/updated/deleted in one command
- `s` = selected main keys in an aggregate

### Storage

| Component | Space | Notes |
|---|---:|---|
| In-memory map | `O(n * m)` | Main keys and sub-key/value pairs. |
| Inverted indexes | `O(T)` | Token/field/value to main-key mappings. |
| File storage | `O(n * m)` | Flat file `dbms_data`. |
| **Total runtime** | `O(n * m + T)` | Database plus indexes. |

### Time

| Operation | Time | Notes |
|---|---:|---|
| Load dataset | `O(n * m)` | Reads all records. |
| Build/rebuild index | `O(T)` | Tokenizes and indexes all fields. |
| Print all records | `O(n * m)` | |
| Create main key | `O(1)` in memory, `O(n * m + T)` with persistence | Rewrites file, rebuilds index. |
| Insert / update / delete sub-keys | `O(k)` in memory, `O(n * m + T)` with persistence | |
| Rename / delete main key | `O(1)` avg in memory, `O(n * m + T)` with persistence | |
| Exact main-key search | `O(1 + m)` avg | HashMap lookup. |
| `search where` | `O(n * c + r * m)` | Scans all records. |
| Indexed field / boolean query | `O(q + p + r log r)` typical | Postings lookup, then sort by score. |
| Indexed wildcard query | `O(u + p + r log r)` | Scans indexed terms first. |
| Average age (all) | `O(n)` | |
| Average age (selected) | `O(s)` | |
| Average age by batch | `O(n)` | |

## Limitations

This is a minor/educational project that favors clarity over production performance:

- Every write rewrites the whole `dbms_data` file and rebuilds the full search index. A production system would use incremental persistence and index updates.
- Single-user and single-process: no concurrency control, transactions, or crash recovery.
- The whole dataset and all indexes live in memory.
- The DBMS itself lives in one source file (`dbms.java`).
- The web demo has no authentication and a single shared dataset.
