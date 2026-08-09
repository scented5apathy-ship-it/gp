#!/bin/bash
# Idempotent: tạo các DB nền tảng còn thiếu cho compose stack.
# Chạy SAU khi gp-postgres healthy. Postgres compose image chỉ tự tạo
# `genea_shared` (qua POSTGRES_DB); các DB khác phải tạo tay vì chart
# values (postgres.host=postgres, schema-per-service) expect chúng có sẵn.
set -e

USER="${POSTGRES_USER:-genealogy}"
DATABASES=(genea_apicurio genea_keycloak genea_openfga genea_temporal genea_flagsmith)

for db in "${DATABASES[@]}"; do
  EXIST=$(psql -U "$USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'")
  if [ "$EXIST" != "1" ]; then
    echo "creating database $db"
    psql -U "$USER" -d postgres -c "CREATE DATABASE $db"
  else
    echo "skip $db (already exists)"
  fi
done