# POS Farmacia en microservicios

Sistema de punto de venta para cadena de boticas, partido en nueve microservicios con base
de datos propia, saga con compensación, outbox transaccional y read model separado.

Evoluciona `arquitecttura_2_t5` (el mismo POS, Clean Architecture monolítica con Kafka y
RabbitMQ). Ninguna regla de negocio cambió: lo que cambió es dónde vive cada cosa y cómo se
hablan entre sí.

```
                  ┌─────────────┐
   caja POS  ───► │ api-gateway │  único puerto expuesto, valida JWT
                  └──────┬──────┘
                  ┌──────▼──────┐   síncrono, con presupuesto de latencia
                  │  ms-ventas  │──► catalogo 200ms  inventario 500ms
                  │  orquesta   │    clientes 300ms  credito    800ms
                  │   la saga   │    promociones 300ms
                  └──────┬──────┘
                  ┌──────▼──────────────┐
                  │  Kafka  (outbox)    │  asíncrono, hecho consumado
                  └──┬────────┬─────┬───┘
              inventario  facturacion  reportes    identidad (JWKS, auditoría)
              FEFO        CPE a SUNAT  read model
```

## Arrancar

Requisitos: Docker Desktop encendido. Nada más (Java y Maven van por wrapper).

```bash
cp .env.example .env          # completar DB_PASSWORD
docker compose up -d --build
```

Tres perfiles, para no levantar veinte contenedores cuando solo quieres probar una venta:

| Comando | Qué levanta | RAM |
|---|---|---|
| `docker compose up -d --build` | Camino de venta: Postgres, Kafka, Redis, gateway, identidad, catálogo, inventario, ventas, frontend | ~3.8 GB |
| `docker compose --profile completo up -d --build` | Los nueve servicios, Schema Registry, Kafka UI | ~4.2 GB |
| `docker compose --profile completo --profile observabilidad up -d --build` | Además Prometheus, Grafana, Tempo, Loki, OTel Collector | ~7.5 GB |

| Servicio | URL |
|---|---|
| Frontend | http://localhost:5175 |
| Gateway | http://localhost:8080 |
| Kafka UI | http://localhost:8092 |
| Grafana | http://localhost:3000 |
| Postgres | localhost:5452 |

Detalle paso a paso en [`docs/COMO_EJECUTAR.md`](docs/COMO_EJECUTAR.md).

## Estructura

```
arquitectura_3_t1/
├── pom.xml                  reactor de 13 módulos
├── contracts/               SOLO contratos: eventos y DTO entre servicios. Cero lógica.
├── plataforma/              outbox, idempotencia, JWT, cliente HTTP con timeout
├── services/
│   ├── api-gateway/         Spring Cloud Gateway, valida JWT y aplica rate limit
│   ├── ms-identidad/        usuarios, roles, locales, cajas, OAuth2, MFA, JWKS
│   ├── ms-catalogo/         productos y precios, caché de dos niveles
│   ├── ms-inventario/       stock, reservas con TTL, FEFO, movimientos
│   ├── ms-clientes/         clientes, seguros, convenios, recetas
│   ├── ms-credito/          línea de crédito y ledger append-only
│   ├── ms-promociones/      reglas y evaluación de descuentos
│   ├── ms-ventas/           venta, pagos y orquestación de la saga
│   ├── ms-facturacion/      comprobantes, notas de crédito, envío a SUNAT
│   └── ms-reportes/         read model CQRS e incentivos
├── frontend/                React + Vite, sin cambios salvo la URL base
├── infra/                   configuración de Postgres, Prometheus, OTel, Tempo
├── k8s/                     manifiestos con Kustomize, HPA y PodDisruptionBudget
├── docs/
│   ├── cliente/             la arquitectura explicada sin jerga
│   ├── ARQUITECTURA.md      el diseño y sus razones
│   ├── COMO_EJECUTAR.md     paso a paso, incluida la verificación funcional
│   └── DECISIONES.md        qué se decidió, por qué, y qué lo cambiaría
└── presentacion/
    ├── arquitectura.html    diagrama interactivo (Archify)
    └── diapositivas.html    presentación con guion de exposición
```

## Compilar y probar

```bash
./mvnw clean package          # los 13 módulos
./mvnw test                   # pruebas de dominio, sin Docker ni base de datos
./mvnw -pl services/ms-inventario -am test    # solo un servicio
```

## Las decisiones que definen el sistema

| Decisión | Por qué |
|---|---|
| Una base por servicio, sin GRANT cruzado | La separación la impone el motor, no la disciplina del equipo |
| Outbox transaccional | Escribir en Postgres y publicar en Kafka son dos sistemas; el outbox los vuelve uno |
| Reserva de stock con un UPDATE condicional | Un statement atómico, sin lock explícito, sin deadlock, contención acotada al local |
| Idempotencia en API y en consumidores | Un POS reintenta; un reintento que cobra dos veces es un problema legal |
| Saga con compensación, sin transacción distribuida | Un lock de dos fases sobre nueve bases a 200 ventas/s no termina bien |
| Solo stock y crédito pueden impedir una venta | Todo lo demás se degrada: negarse a cobrar le hace perder la venta a la botica |
| Facturación aislada | SUNAT se cae, y eso nunca puede parar una caja |

El razonamiento completo, incluido lo que deliberadamente no se hizo y cuándo entraría, en
[`docs/DECISIONES.md`](docs/DECISIONES.md).

## Kubernetes

Los manifiestos están en `k8s/`. Se activa el Kubernetes de Docker Desktop desde Settings
y después:

```bash
kubectl apply -k k8s/base
kubectl get pods -n pos-farmacia
```

## Presentación

- `presentacion/arquitectura.html`: diagrama interactivo, con cuatro recorridos guiados,
  tema claro y oscuro y exportación. Se abre con doble clic, no necesita internet.
- `presentacion/diapositivas.html`: 21 diapositivas con el botón "Cómo exponer", que
  muestra el guion y los puntos clave de cada una. Flechas para navegar, tecla N para las
  notas.
