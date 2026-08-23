#!/bin/sh
set -eu

#render supplies a standard PostgreSQL URI. The PostgreSQL JDBC driver needs a jdbc:postgresql URI
#while the username and password remain in their own environment variables. Never print the incoming URI because it is secret
case "${TENTFLOW_DATABASE_URL:-}" in
    postgresql://*)
        database_address=${TENTFLOW_DATABASE_URL#postgresql://}
        database_host_and_path=${database_address##*@}
        export TENTFLOW_DATABASE_URL="jdbc:postgresql://${database_host_and_path}"
        ;;
    postgres://*)
        database_address=${TENTFLOW_DATABASE_URL#postgres://}
        database_host_and_path=${database_address##*@}
        export TENTFLOW_DATABASE_URL="jdbc:postgresql://${database_host_and_path}"
        ;;
    jdbc:postgresql://*|"")
        #local JVM runs already use JDBC form, and an empty value lets Spring use the component-based localhost fallback in application.properties
        ;;
    *)
        echo "TENTFLOW_DATABASE_URL must use postgres://, postgresql://, or jdbc:postgresql://." >&2
        exit 64
        ;;
esac

exec java -jar /app/tentflow.jar