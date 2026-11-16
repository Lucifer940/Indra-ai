# INDRA AI database

INDRA AI stores every entity as a JSON document with indexed columns for the
owner, a lookup key and the change timestamp. That keeps the schema stable while
the app evolves, and it works identically on SQLite (phone-sized deployments)
and PostgreSQL (multi-user deployments).

| File | Purpose |
|------|---------|
| `migrations/V1__init.sql` (in the backend resources) | schema created automatically on first start |
| `schema.postgres.sql` | the same schema for manual PostgreSQL provisioning |

## Choosing a store

| Configuration | Engine | Use it when |
|---------------|--------|-------------|
| *(nothing set)* | JSON files in `INDRA_DATA_DIR/store` | personal server, single user, no database server available |
| `INDRA_DB_URL=jdbc:sqlite:/path/indra.db` | SQLite | personal server that wants a single portable database file |
| `INDRA_DB_URL=jdbc:postgresql://host:5432/indra` + user/password | PostgreSQL | family/multi-user deployment with concurrent access |

The API, migrations and backup/restore commands are identical for all three.

## Backup and restore

```bash
java -jar indra-backend-all.jar backup ./backup-$(date +%F).json
java -jar indra-backend-all.jar restore ./backup-2026-11-16.json
```

Backups contain password hashes and encrypted API keys. Treat them as secrets.
