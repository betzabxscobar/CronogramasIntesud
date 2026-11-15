# CRONO-INTESUD · API Spring Boot

Reescritura en Java de `apps/api` (NestJS). Usa la **misma base de datos** y el **mismo `.env`**
de la raíz del repositorio, y responde con las mismas rutas y el mismo formato JSON, de modo que
`apps/web` (React) no necesita cambios.

- Spring Boot 4.1 · Java 21 · Maven (incluye Maven Wrapper, no hace falta instalar Maven)
- Spring Data JPA (Hibernate) sobre PostgreSQL
- Spring Security con JWT (HS256) compatible con los tokens de NestJS

## Abrir en Spring Tools for Eclipse

1. `File > Import... > Maven > Existing Maven Projects`.
2. En *Root Directory* elegir la carpeta `apps/api-spring` y pulsar *Finish*.
3. Esperar a que Maven descargue las dependencias.
4. En el *Boot Dashboard*, seleccionar `crono-api` y pulsar *Start*
   (o clic derecho en `CronoApiApplication.java` > `Run As > Spring Boot App`).

Antes de arrancar debe existir el archivo `.env` en la raíz del repositorio (ver `.env.example`)
con `DATABASE_URL` y `SESSION_SECRET` (mínimo 32 caracteres).

La API queda en `http://localhost:8080/api/v1`. El puerto se cambia con `API_SPRING_PORT` en `.env`.

## Línea de comandos

```bash
./mvnw spring-boot:run
```

```bash
./mvnw test
```

Las pruebas levantan un PostgreSQL desechable (no usan la base local ni Docker).

## Conectar el frontend

En `apps/web/vite.config.ts`, apuntar el proxy a esta API:

```ts
proxy: { "/api": "http://localhost:8080" }
```

Mientras dure la migración se pueden tener las dos API a la vez y enviar a Spring solo lo ya
migrado, por ejemplo:

```ts
proxy: {
  "/api/v1/auth": "http://localhost:8080",
  "/api": "http://localhost:3000"
}
```

Las sesiones son intercambiables entre ambas API porque comparten `SESSION_SECRET`.

## Estructura

| Paquete | Contenido | Equivalente en NestJS |
|---|---|---|
| `domain` | 22 entidades JPA y 11 enums | `prisma/schema.prisma` |
| `repository` | Repositorios Spring Data | `PrismaService` |
| `auth` | Login por código, JWT, sesión | `src/auth` |
| `mail` | Envío SMTP | `src/mail` |
| `config` | Seguridad, CORS, límite de peticiones, conexión | `main.ts`, `app.module.ts`, guards |
| `common` | Formato de errores, `/health` | filtros de excepción de Nest |

### Cómo migrar un módulo

1. Crear el paquete (por ejemplo `careers`) con `CareersController`, `CareersService` y sus DTO
   como `record` con anotaciones de validación (`@NotBlank`, `@Size`...).
2. Crear el repositorio en `repository` si aún no existe.
3. Traducir los decoradores:

| NestJS | Spring |
|---|---|
| `@Controller("careers")` | `@RestController @RequestMapping("/careers")` |
| `@Roles(UserRole.ADMIN)` | `@PreAuthorize("hasRole('ADMIN')")` |
| `@Public()` | añadir la ruta a `PUBLIC_ROUTES` en `SecurityConfig` |
| `@CurrentUser() actor` | `@AuthenticationPrincipal AuthenticatedUser actor` |
| `@Body() input: Dto` | `@Valid @RequestBody Dto input` |
| `throw new NotFoundException("...")` | `throw ApiException.notFound("...")` |
| `this.prisma.$transaction(...)` | `@Transactional` en el método del servicio |

## Base de datos y migraciones

- `spring.jpa.hibernate.ddl-auto=validate`: al arrancar se comprueba que las entidades coinciden
  con las tablas. Nunca se modifica el esquema.
- **Flyway está desactivado** mientras Prisma siga administrando el esquema. Cuando se retire
  NestJS, poner `FLYWAY_ENABLED=true` en `.env`: una base creada por Prisma se toma como línea
  base (`V1`) y solo se aplican las migraciones posteriores.
- `V2__import_jobs_period.sql` crea `import_jobs.period_id`. Esa columna está en `schema.prisma`
  pero en ninguna migración de Prisma; si la base local no la tiene, el arranque avisa con
  `missing column [period_id] in table [import_jobs]`.

## Pendiente

Módulos por migrar: `careers`, `coordinators`, `laboratories`, `academic-structure`,
`academic-periods`, `professors`, `schedules`, `exports`, `imports`. También los scripts
`seed.ts`, `create-admin.ts` e `import-ds-excel-catalog.ts`, y la documentación Swagger.
