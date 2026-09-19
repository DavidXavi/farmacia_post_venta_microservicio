#!/bin/bash
# Crea las nueve bases, una por servicio, cada una con su propio usuario.
#
# La regla "una base por servicio" no se impone con disciplina del equipo sino con
# permisos del motor: el usuario de ms-catalogo no puede ni CONECTARSE a pg_ventas.
# Nadie va a "consultar rapidito la tabla del otro para salir del apuro" porque el
# motor no lo deja.
#
# ponytail: un solo contenedor Postgres con nueve bases, no nueve contenedores.
# El aislamiento logico es identico y el codigo no nota la diferencia (solo cambia la
# cadena de conexion, que ya viene por variable de entorno). Lo que se pierde es el
# aislamiento fisico, que en una laptop no aporta nada y cuesta 2 GB de RAM.
# En produccion son nueve instancias separadas: ahi si esta la ganancia de capacidad.
set -e

crear_base() {
    local base=$1
    local usuario=$2
    echo "  creando $base (usuario $usuario)"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE USER $usuario WITH PASSWORD '${POS_DB_PASSWORD:-posfarmacia}';
        CREATE DATABASE $base OWNER $usuario;
        REVOKE CONNECT ON DATABASE $base FROM PUBLIC;
        GRANT ALL PRIVILEGES ON DATABASE $base TO $usuario;
EOSQL
}

echo "Creando las nueve bases del sistema POS..."
crear_base pg_identidad    u_identidad
crear_base pg_catalogo     u_catalogo
crear_base pg_inventario   u_inventario
crear_base pg_clientes     u_clientes
crear_base pg_credito      u_credito
crear_base pg_promociones  u_promociones
crear_base pg_ventas       u_ventas
crear_base pg_facturacion  u_facturacion
crear_base pg_reportes     u_reportes

# pg_trgm lo necesita catalogo para la busqueda por nombre desde la caja.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname pg_catalogo \
    -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

echo "Listo. Ninguna base puede leer las tablas de otra: no hay GRANT cruzado."
