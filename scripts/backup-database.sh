#!/bin/sh
set -eu

#creates a PostgreSQL custom-format backup without stopping the database
backup_directory=${1:-backups}
mkdir -p "$backup_directory"
backup_file="${backup_directory}/tentflow-$(date -u +%Y%m%dT%H%M%SZ).dump"

docker compose exec -T database sh -c \
    'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --format=custom --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    > "$backup_file"

test -s "$backup_file" || {
    echo "Backup was empty; removing it." >&2
    rm -f "$backup_file"
    exit 1
}

echo "Backup created: $backup_file"