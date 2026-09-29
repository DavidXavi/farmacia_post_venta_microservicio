# Documentación

## Para quien decide

[`cliente/`](cliente/) explica el sistema sin jerga: qué hace, cómo funciona por dentro,
qué pasa cuando algo falla, cuánto aguanta y cuánto cuesta. Está escrito para que lo lea
quien aprueba el presupuesto, no solo quien programa.

## Para quien construye

| Documento | De qué trata |
|---|---|
| [ARQUITECTURA.md](ARQUITECTURA.md) | La estructura: capas, reparto de datos, mensajería, secuencias |
| [DECISIONES.md](DECISIONES.md) | Qué se decidió, contra qué alternativa, y qué lo cambiaría |
| [COMO_EJECUTAR.md](COMO_EJECUTAR.md) | Levantar, verificar y depurar, paso a paso |
| [ESTADO.md](ESTADO.md) | Punto de retomada: qué funciona verificado, qué falta, qué fallos ya se encontraron |
| [DIAGNOSTICO.md](DIAGNOSTICO.md) | En qué se rompe este sistema: los patrones que se repiten y qué revisar antes de tocar algo |

## Para exponer

| Archivo | Qué es |
|---|---|
| `../presentacion/arquitectura.html` | Diagrama interactivo con cuatro recorridos guiados |
| `../presentacion/exposicion.html` | 12 láminas en dos bloques (Juan y Javier), la que se expone |
| `../presentacion/diapositivas.html` | 28 diapositivas de respaldo, con guion por diapositiva |

## De dónde viene este proyecto

Evoluciona `arquitecttura_2_t5`: el mismo POS de farmacia, con Clean Architecture
monolítica, Kafka y RabbitMQ. Ninguna regla de negocio cambió. Lo que cambió es dónde vive
cada cosa y cómo se hablan entre sí.

El recorrido de los doce pasos de migración está en DECISIONES.md y en la diapositiva 19.
