#!/bin/sh
# Crea la base del servicio clientes con su propio usuario (cada servicio es dueño de su base).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    CREATE ROLE ${CLIENTES_DB_USUARIO} WITH LOGIN PASSWORD '${CLIENTES_DB_CLAVE}';
    CREATE DATABASE clientes_db OWNER ${CLIENTES_DB_USUARIO};
SQL
