# PostgreSQL monitoring

The custom Collector sends PostgreSQL server statistics and filesystem capacity
to VictoriaMetrics every 30 seconds. PostgreSQL client latency/pool metrics still
come from the backend's existing instrumentation. The dashboard definition is
[`../grafana/sync/postgresql-overview.json`](../grafana/sync/postgresql-overview.json).

## Scope and access

- `postgresql` collects native server/database/table/index metrics.
- `sqlquery/postgresql_health` adds autovacuum/autoanalyze counters and timestamps,
  table XID age, open/idle transactions, lock waiters and statement counters.
  Its pool is capped at one connection; it reads PostgreSQL statistics only.
- `hostmetrics/postgresql_volume` collects bytes and inodes only for
  `/var/lib/postgresql/data`, backed by the same `postgres_data` named volume as
  the database, mounted read-only in the Collector. No host-root mount or host
  PID namespace is required. Capacity belongs to the backing filesystem, not
  exclusively to PostgreSQL: other files on that filesystem consume it too.
  `include_virtual_filesystems: true` is required because the filesystem library
  otherwise skips Docker volume bind mounts, including real ext4 filesystems.
  The strict mount-point filter still excludes every mount except the DB volume.
- The read-only mount mode is a Collector access restriction, not an indication
  that the database's own mount is read-only. The Collector can read the volume
  contents, so retain its trusted-image/security boundary.
- Native SQL query-sample/top-query events and automatic EXPLAIN are disabled.
  Statement metrics use `public.pg_stat_statements(false)`: SQL text is not even
  retrieved. Only numeric `userid`/`queryid`, database and counters are exported.
- `pg_stat_statements` requires preload, a database restart and administrative
  extension provisioning. No backend change or application migration is added.

## Prepare each environment before deployment

Create the monitoring role independently in staging and production. Use an
administrative psql session connected to the application's database; do not
execute this through Grafana or use the application password for this role.

```sql
CREATE ROLE commonex_monitor LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
  NOREPLICATION CONNECTION LIMIT 5;
GRANT pg_monitor TO commonex_monitor;
GRANT CONNECT ON DATABASE postgres TO commonex_monitor;
SELECT current_database() AS monitoring_database \gset
GRANT CONNECT ON DATABASE :"monitoring_database" TO commonex_monitor;
ALTER ROLE commonex_monitor SET default_transaction_read_only = on;
ALTER ROLE commonex_monitor SET statement_timeout = '5s';
ALTER ROLE commonex_monitor SET lock_timeout = '1s';
\password commonex_monitor
```

`\password` prompts privately; keep the password out of SQL files, shell history
and logs. If the role already exists, inspect its attributes/grants rather than
dropping or overwriting it. The native receiver also connects to the `postgres`
maintenance database. `pg_monitor` grants statistics/settings visibility, not
application-table DML privileges; do not additionally grant table access.

Set matching GitHub Actions secrets:

- Production environment secret: `POSTGRES_MONITOR_PASSWORD`.
- Staging: `STAGING_POSTGRES_MONITOR_PASSWORD`.

The workflow writes the selected value into the private release environment as
`POSTGRES_MONITOR_PASSWORD`. The host wrapper's required-key contract is unchanged
and the new password is escaped for literal Compose dotenv parsing.
Older retained releases remain eligible for rollback. New workflow deliveries
reject an empty monitoring secret before creating the release inputs. Direct
local/full-stack runs must also provide this environment value. The backend's
DB-only test Compose does not require it.

The SQL receiver uses `PGPASSWORD` and `PGDATABASE` environment variables rather
than interpolating credentials into a DSN. The native receiver gets the same
dedicated password. Neither receives `POSTGRES_PASSWORD`.

When rotating the monitoring password, update the `commonex_monitor` role and
the matching GitHub environment secret together, then deliver a new application
release with a new Git SHA. Restarting the existing Collector does not update its
environment. Release inputs are immutable: rerunning delivery for an already
staged SHA cannot replace its `.env` with the rotated secret. An empty commit is
also insufficient to trigger this workflow because its push trigger filters
changed paths; use a reviewed change covered by the application workflow.

## Prepare pg_stat_statements before the release

This release changes the database startup command. The normal release wrapper
reconciles the full stack, so deployment will recreate PostgreSQL and interrupt
connections. Schedule a maintenance window, verify backups, and activate staging
before production. An ordinary `docker compose restart` cannot apply a changed
startup command. Do not restart production or change the active host release
files independently of the approved release workflow.

Before activation, inspect the current settings and extension in an administrative
psql session connected to the application database:

```sql
SHOW shared_preload_libraries;
SHOW compute_query_id;
SELECT e.extversion, n.nspname AS extension_schema
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
WHERE e.extname = 'pg_stat_statements';
```

The Compose command explicitly preloads `pg_stat_statements`. If the server
already preloads other modules, preserve them in the release command before
deployment; do not overwrite the list. The Collector expects the PostgreSQL 17
extension in `public`. If it already exists elsewhere or at an older version,
stop and review its schema/version rather than silently relocating/upgrading it.

For an absent extension, provision it before the release:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public;
GRANT USAGE ON SCHEMA public TO commonex_monitor;
GRANT EXECUTE ON FUNCTION public.pg_stat_statements(boolean) TO commonex_monitor;
```

Creating the extension before preload is supported; do not call its statistics
function until the restart has loaded the module. Existing volumes do not rerun
Docker init scripts, which is why provisioning is explicit rather than an init
mount. For a fresh database, create the role and extension after initialization;
Collector statement scrapes will report errors until they exist.

Startup settings use `compute_query_id=auto`, `track=top`, `track_planning=off`,
`track_utility=off` and a 5000-entry cluster-wide limit. Planning measurement and
utility commands such as COMMIT are excluded. Existing backend COMMIT/ROLLBACK
panels remain independent. Monitoring-user queries are filtered out; other
clients, including Grafana, can still contribute to statement counters.

Five cumulative counters are emitted for every visible statement identity in the
application database: calls, execution seconds, rows, shared block hits and reads.
There is no scrape-time top-N truncation. At most 25,000 statement datapoints per
scrape are produced at the configured limit, in addition to other metrics; check
Collector memory and scrape time after deployment. Historical series cardinality
can grow as statements are evicted or identities change.

Dashboards rank the top ten using rates over `$__rate_interval`. The mean is
execution-time rate divided by call rate, with inactive identities omitted, not
a lifetime mean or percentile. Block reads are buffer misses, not necessarily
physical disk reads. Resets/eviction can lose observations between scrapes; rates
handle observed counter decreases but cannot reconstruct missing executions.
Query IDs are not guaranteed stable across DDL, server architectures or major
versions. To correlate a query ID with SQL, inspect it in an authorized database
session; this dashboard deliberately does not expose SQL text. PostgreSQL itself
still retains representative statements, so protect database access and backups.

Rollback to a retained release without these startup flags also recreates the DB
and disables collection. The extension can remain installed; no DROP EXTENSION
or statistics reset is required. A rollback is not a zero-downtime operation.

## Validate and deploy

Tests are run by the repository owner:

```bash
python3 -m unittest infra.deploy.test_write_runtime_environment infra.deploy.test_docker_compose_prod
python3 -m unittest discover -s infra/deploy -p 'test_*.py'
# Optional runtime regression test with an already built custom Collector image:
COMMONEX_COLLECTOR_TEST_IMAGE=commonex-otel-db-monitoring:validation python3 -m unittest infra.deploy.test_postgresql_filesystem
```

The custom image build runs `otelcol-custom validate` with dummy values, without
connecting to a database. Validate Compose with the private runtime environment
using `config --quiet`; avoid printing rendered secrets.

After preparing both roles/secrets, use the normal immutable-image staging and
production release workflow. Do not edit the active host Compose/environment
outside that workflow and do not independently run a full-stack `up` against an
immutable release. The Collector image/config, environment and its read-only
volume/network wiring deploy together. PostgreSQL's changed startup command
requires the maintenance window described above. Dashboard JSON is reconciled
separately through Grafana Git Sync.

## Post-deployment checks

1. Confirm Collector health and check for receiver/authentication/scrape errors.
2. In VictoriaMetrics/Grafana Explore, verify `postgresql.db_size`,
   `postgresql.rows`, `commonex.postgresql.transaction.open`,
   `commonex.postgresql.autovacuum.count` and `system.filesystem.usage`.
3. For filesystem metrics, verify `mountpoint="/var/lib/postgresql/data"` and the
   device/type match the filesystem backing the DB volume. Compare free bytes
   and inodes with `df -B1` / `df -i` for the database volume inside a container;
   do not compare against the Collector's overlay root filesystem.
4. Native receiver labels use the default legacy resource model
   (`postgresql.database.name`, `postgresql.table.name`). Custom health labels
   are `database`, `schema`, `table`. No experimental semantic-convention gate is
   enabled. Check these labels before changing queries.
5. Allow at least two 30-second scrapes for rate/increase panels. A value of zero
   is valid when there is no activity; missing data must not be mapped to zero.
6. SQL tables on the dashboard remain current snapshots; their contents do not
   follow the selected historical time range. Historical transaction age is the
   oldest open transaction at scrape time, not completed transaction latency.
7. After the database restart, verify the preload/settings and call
   `SELECT count(*) FROM public.pg_stat_statements(false);` as `commonex_monitor`.
   Check `commonex.postgresql.statement.calls` and
   `commonex.postgresql.statement.execution.seconds` after application activity
   and at least two scrapes. An idle/new database may initially have no series.
   Inspect `SELECT * FROM public.pg_stat_statements_info;` administratively for
   reset timestamps and deallocations; do not reset statistics as a smoke check.

The last-autovacuum/autoanalyze age graphs omit tables whose timestamp is unknown
(exported as zero). Native live/dead tuple counts are estimates, not measured
bloat. Filesystem free percentage excludes reserved bytes from the numerator.

Still deferred: SQL-text presentation, completed transaction duration,
WAL-size history, and moving the existing Grafana PostgreSQL datasource off its
administrative role. No alert rules are installed by this change.

References: [PostgreSQL receiver v0.159.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.159.0/receiver/postgresqlreceiver/README.md),
[SQL receiver v0.159.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.159.0/receiver/sqlqueryreceiver/README.md),
[filesystem scraper v0.159.0](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.159.0/receiver/hostmetricsreceiver/internal/scraper/filesystemscraper/documentation.md),
[PostgreSQL monitoring roles](https://www.postgresql.org/docs/17/predefined-roles.html).
Statement collection: [PostgreSQL 17 pg_stat_statements](https://www.postgresql.org/docs/17/pgstatstatements.html),
[extension creation before preload](https://github.com/postgres/postgres/blob/REL_17_STABLE/contrib/pg_stat_statements/pg_stat_statements.c).
