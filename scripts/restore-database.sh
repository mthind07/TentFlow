#!/bin/sh
set -eu

#restore is intentionally guarded because --clean replaces current data
backup_file=${1:?Usage: RESTORE_CONFIRM=restore ./scripts/restore-database.sh BACKUP.dump}

[ "${RESTORE_CONFIRM:-}" = "restore" ] || {
    echo "Set RESTORE_CONFIRM=restore after confirming the target database." >&2
    exit 2
}

[ -f "$backup_file" ] && [ -r "$backup_file" ] || {
    echo "Backup file is not readable: $backup_file" >&2
    exit 2
}

[ -z "$(docker compose --profile full ps --quiet --status running application)" ] || {
    echo "Stop the application before restoring: docker compose --profile full stop application" >&2
    exit 2
}

echo "Restoring $backup_file into the Compose database..."

docker compose exec -T database sh -c \
    'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore --clean --if-exists --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    < "$backup_file"

echo "Restore completed. Restart the application and run the smoke test."