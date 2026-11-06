# CRONO-INTESUD

Aplicación web para la administración de profesores, horarios académicos y laboratorios de INTESUD.

## Componentes

- `apps/web`: interfaz React.
- `apps/api`: API REST NestJS.
- `apps/api/prisma`: modelo y migraciones PostgreSQL.

## Requisitos

- Node.js 24 o superior.
- pnpm 11.
- PostgreSQL 18 disponible localmente.

## Inicio local

1. Reemplazar `CHANGE_ME` dentro de `.env` con la contraseña local de PostgreSQL.
2. Crear la base de datos `crono_intesud` desde pgAdmin.
3. Ejecutar `pnpm db:generate`.
4. Ejecutar `pnpm db:migrate`.
5. Ejecutar `pnpm dev`.

La interfaz se sirve en `http://localhost:5173` y la API en `http://localhost:3000/api/v1`.
