-- Esquema base: equivale a las migraciones de Prisma hasta 20261002140000.
-- En una base ya creada por Prisma no se ejecuta (baseline-on-migrate).

-- ===== 20260729162737_init =====
-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('ADMIN', 'COORDINATOR');

-- CreateEnum
CREATE TYPE "RecordStatus" AS ENUM ('ACTIVE', 'INACTIVE');

-- CreateTable
CREATE TABLE "users" (
    "id" UUID NOT NULL,
    "national_id" TEXT NOT NULL,
    "first_names" TEXT NOT NULL,
    "last_names" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "password_hash" TEXT NOT NULL,
    "role" "UserRole" NOT NULL,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "careers" (
    "id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "careers_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "coordinator_careers" (
    "user_id" UUID NOT NULL,
    "career_id" UUID NOT NULL,
    "assigned_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "coordinator_careers_pkey" PRIMARY KEY ("user_id","career_id")
);

-- CreateIndex
CREATE UNIQUE INDEX "users_national_id_key" ON "users"("national_id");

-- CreateIndex
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

-- CreateIndex
CREATE UNIQUE INDEX "careers_name_key" ON "careers"("name");

-- CreateIndex
CREATE UNIQUE INDEX "careers_slug_key" ON "careers"("slug");

-- AddForeignKey
ALTER TABLE "coordinator_careers" ADD CONSTRAINT "coordinator_careers_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "coordinator_careers" ADD CONSTRAINT "coordinator_careers_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;


-- ===== 20260729163419_expand_academic_domain =====
-- CreateEnum
CREATE TYPE "AcademicPeriodStatus" AS ENUM ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED');

-- CreateEnum
CREATE TYPE "Shift" AS ENUM ('DAY', 'NIGHT');

-- CreateEnum
CREATE TYPE "ScheduleStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "SpaceType" AS ENUM ('CLASSROOM', 'LABORATORY');

-- CreateEnum
CREATE TYPE "SpaceStatus" AS ENUM ('AVAILABLE', 'UNAVAILABLE', 'MAINTENANCE', 'INACTIVE');

-- CreateEnum
CREATE TYPE "ReservationType" AS ENUM ('CLASS', 'EVENT', 'MAINTENANCE', 'ADMINISTRATIVE_BLOCK');

-- CreateEnum
CREATE TYPE "DayOfWeek" AS ENUM ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY');

-- CreateEnum
CREATE TYPE "ImportStatus" AS ENUM ('UPLOADED', 'VALIDATING', 'INVALID', 'READY', 'IMPORTED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "ImportRowStatus" AS ENUM ('VALID', 'WARNING', 'ERROR');

-- AlterTable
ALTER TABLE "users" ADD COLUMN     "last_login_at" TIMESTAMP(3);

-- CreateTable
CREATE TABLE "user_sessions" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "refresh_token_hash" TEXT NOT NULL,
    "ip_address" TEXT,
    "user_agent" TEXT,
    "expires_at" TIMESTAMP(3) NOT NULL,
    "revoked_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "user_sessions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "file_assets" (
    "id" UUID NOT NULL,
    "storage_key" TEXT NOT NULL,
    "original_name" TEXT NOT NULL,
    "mime_type" TEXT NOT NULL,
    "size_bytes" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "file_assets_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "professors" (
    "id" UUID NOT NULL,
    "national_id" TEXT,
    "first_names" TEXT NOT NULL,
    "last_names" TEXT NOT NULL,
    "professional_title" TEXT,
    "academic_biography" VARCHAR(800),
    "public_profile_enabled" BOOLEAN NOT NULL DEFAULT true,
    "photo_asset_id" UUID,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "professors_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "professor_careers" (
    "professor_id" UUID NOT NULL,
    "career_id" UUID NOT NULL,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',
    "linked_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "professor_careers_pkey" PRIMARY KEY ("professor_id","career_id")
);

-- CreateTable
CREATE TABLE "professor_availabilities" (
    "id" UUID NOT NULL,
    "professor_id" UUID NOT NULL,
    "day_of_week" "DayOfWeek" NOT NULL,
    "start_time" TIME(0) NOT NULL,
    "end_time" TIME(0) NOT NULL,
    "available" BOOLEAN NOT NULL DEFAULT true,

    CONSTRAINT "professor_availabilities_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "academic_periods" (
    "id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE NOT NULL,
    "status" "AcademicPeriodStatus" NOT NULL DEFAULT 'DRAFT',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "academic_periods_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "levels" (
    "id" UUID NOT NULL,
    "career_id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT "levels_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "subjects" (
    "id" UUID NOT NULL,
    "career_id" UUID NOT NULL,
    "level_id" UUID,
    "code" TEXT,
    "name" TEXT NOT NULL,
    "weekly_hours" INTEGER,
    "requires_individual_computer" BOOLEAN NOT NULL DEFAULT false,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT "subjects_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "academic_courses" (
    "id" UUID NOT NULL,
    "period_id" UUID NOT NULL,
    "level_id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "student_count" INTEGER,
    "status" "RecordStatus" NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT "academic_courses_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "academic_spaces" (
    "id" UUID NOT NULL,
    "name" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "location" TEXT,
    "type" "SpaceType" NOT NULL,
    "people_capacity" INTEGER,
    "student_computers" INTEGER,
    "teacher_computers" INTEGER,
    "has_projector" BOOLEAN NOT NULL DEFAULT false,
    "has_digital_board" BOOLEAN NOT NULL DEFAULT false,
    "status" "SpaceStatus" NOT NULL DEFAULT 'AVAILABLE',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "academic_spaces_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "space_careers" (
    "space_id" UUID NOT NULL,
    "career_id" UUID NOT NULL,

    CONSTRAINT "space_careers_pkey" PRIMARY KEY ("space_id","career_id")
);

-- CreateTable
CREATE TABLE "space_unavailabilities" (
    "id" UUID NOT NULL,
    "space_id" UUID NOT NULL,
    "starts_at" TIMESTAMP(3) NOT NULL,
    "ends_at" TIMESTAMP(3) NOT NULL,
    "reason" TEXT NOT NULL,

    CONSTRAINT "space_unavailabilities_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "schedules" (
    "id" UUID NOT NULL,
    "course_id" UUID NOT NULL,
    "shift" "Shift" NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "schedules_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "schedule_versions" (
    "id" UUID NOT NULL,
    "schedule_id" UUID NOT NULL,
    "version" INTEGER NOT NULL,
    "status" "ScheduleStatus" NOT NULL DEFAULT 'DRAFT',
    "reason" TEXT,
    "created_by_id" UUID NOT NULL,
    "modified_by_id" UUID NOT NULL,
    "published_by_id" UUID,
    "published_at" TIMESTAMP(3),
    "unpublished_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "schedule_versions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "schedule_blocks" (
    "id" UUID NOT NULL,
    "version_id" UUID NOT NULL,
    "subject_id" UUID NOT NULL,
    "professor_id" UUID NOT NULL,
    "space_id" UUID,
    "day_of_week" "DayOfWeek" NOT NULL,
    "start_time" TIME(0) NOT NULL,
    "end_time" TIME(0) NOT NULL,
    "valid_from" DATE,
    "valid_until" DATE,
    "module_number" INTEGER,
    "notes" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "schedule_blocks_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "space_reservations" (
    "id" UUID NOT NULL,
    "space_id" UUID NOT NULL,
    "career_id" UUID,
    "created_by_id" UUID NOT NULL,
    "type" "ReservationType" NOT NULL,
    "title" TEXT NOT NULL,
    "description" TEXT,
    "responsible" TEXT,
    "starts_at" TIMESTAMP(3) NOT NULL,
    "ends_at" TIMESTAMP(3) NOT NULL,
    "cancelled_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "space_reservations_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "import_jobs" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "career_id" UUID NOT NULL,
    "file_name" TEXT NOT NULL,
    "status" "ImportStatus" NOT NULL DEFAULT 'UPLOADED',
    "total_rows" INTEGER NOT NULL DEFAULT 0,
    "valid_rows" INTEGER NOT NULL DEFAULT 0,
    "warning_rows" INTEGER NOT NULL DEFAULT 0,
    "error_rows" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "import_jobs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "import_rows" (
    "id" UUID NOT NULL,
    "import_job_id" UUID NOT NULL,
    "sheet_name" TEXT NOT NULL,
    "row_number" INTEGER NOT NULL,
    "status" "ImportRowStatus" NOT NULL,
    "raw_data" JSONB NOT NULL,
    "messages" JSONB NOT NULL,

    CONSTRAINT "import_rows_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "audit_logs" (
    "id" UUID NOT NULL,
    "user_id" UUID,
    "action" TEXT NOT NULL,
    "entity_type" TEXT NOT NULL,
    "entity_id" TEXT,
    "reason" TEXT,
    "previous_data" JSONB,
    "new_data" JSONB,
    "ip_address" TEXT,
    "user_agent" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "audit_logs_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "user_sessions_user_id_expires_at_idx" ON "user_sessions"("user_id", "expires_at");

-- CreateIndex
CREATE UNIQUE INDEX "file_assets_storage_key_key" ON "file_assets"("storage_key");

-- CreateIndex
CREATE UNIQUE INDEX "professors_national_id_key" ON "professors"("national_id");

-- CreateIndex
CREATE UNIQUE INDEX "professors_photo_asset_id_key" ON "professors"("photo_asset_id");

-- CreateIndex
CREATE INDEX "professors_last_names_first_names_idx" ON "professors"("last_names", "first_names");

-- CreateIndex
CREATE INDEX "professor_careers_career_id_status_idx" ON "professor_careers"("career_id", "status");

-- CreateIndex
CREATE INDEX "professor_availabilities_professor_id_day_of_week_idx" ON "professor_availabilities"("professor_id", "day_of_week");

-- CreateIndex
CREATE UNIQUE INDEX "academic_periods_name_key" ON "academic_periods"("name");

-- CreateIndex
CREATE UNIQUE INDEX "levels_career_id_name_key" ON "levels"("career_id", "name");

-- CreateIndex
CREATE UNIQUE INDEX "levels_career_id_order_key" ON "levels"("career_id", "order");

-- CreateIndex
CREATE INDEX "subjects_career_id_level_id_status_idx" ON "subjects"("career_id", "level_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "subjects_career_id_code_key" ON "subjects"("career_id", "code");

-- CreateIndex
CREATE INDEX "academic_courses_period_id_status_idx" ON "academic_courses"("period_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "academic_courses_period_id_level_id_key" ON "academic_courses"("period_id", "level_id");

-- CreateIndex
CREATE UNIQUE INDEX "academic_spaces_code_key" ON "academic_spaces"("code");

-- CreateIndex
CREATE INDEX "academic_spaces_type_status_idx" ON "academic_spaces"("type", "status");

-- CreateIndex
CREATE INDEX "space_careers_career_id_idx" ON "space_careers"("career_id");

-- CreateIndex
CREATE INDEX "space_unavailabilities_space_id_starts_at_ends_at_idx" ON "space_unavailabilities"("space_id", "starts_at", "ends_at");

-- CreateIndex
CREATE UNIQUE INDEX "schedules_course_id_shift_key" ON "schedules"("course_id", "shift");

-- CreateIndex
CREATE INDEX "schedule_versions_schedule_id_status_idx" ON "schedule_versions"("schedule_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "schedule_versions_schedule_id_version_key" ON "schedule_versions"("schedule_id", "version");

-- CreateIndex
CREATE INDEX "schedule_blocks_version_id_day_of_week_start_time_end_time_idx" ON "schedule_blocks"("version_id", "day_of_week", "start_time", "end_time");

-- CreateIndex
CREATE INDEX "schedule_blocks_professor_id_day_of_week_start_time_end_tim_idx" ON "schedule_blocks"("professor_id", "day_of_week", "start_time", "end_time");

-- CreateIndex
CREATE INDEX "schedule_blocks_space_id_day_of_week_start_time_end_time_idx" ON "schedule_blocks"("space_id", "day_of_week", "start_time", "end_time");

-- CreateIndex
CREATE INDEX "space_reservations_space_id_starts_at_ends_at_idx" ON "space_reservations"("space_id", "starts_at", "ends_at");

-- CreateIndex
CREATE INDEX "space_reservations_career_id_starts_at_idx" ON "space_reservations"("career_id", "starts_at");

-- CreateIndex
CREATE INDEX "import_jobs_career_id_created_at_idx" ON "import_jobs"("career_id", "created_at");

-- CreateIndex
CREATE INDEX "import_rows_import_job_id_status_idx" ON "import_rows"("import_job_id", "status");

-- CreateIndex
CREATE INDEX "audit_logs_user_id_created_at_idx" ON "audit_logs"("user_id", "created_at");

-- CreateIndex
CREATE INDEX "audit_logs_entity_type_entity_id_created_at_idx" ON "audit_logs"("entity_type", "entity_id", "created_at");

-- CreateIndex
CREATE INDEX "coordinator_careers_career_id_idx" ON "coordinator_careers"("career_id");

-- AddForeignKey
ALTER TABLE "user_sessions" ADD CONSTRAINT "user_sessions_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "professors" ADD CONSTRAINT "professors_photo_asset_id_fkey" FOREIGN KEY ("photo_asset_id") REFERENCES "file_assets"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "professor_careers" ADD CONSTRAINT "professor_careers_professor_id_fkey" FOREIGN KEY ("professor_id") REFERENCES "professors"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "professor_careers" ADD CONSTRAINT "professor_careers_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "professor_availabilities" ADD CONSTRAINT "professor_availabilities_professor_id_fkey" FOREIGN KEY ("professor_id") REFERENCES "professors"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "levels" ADD CONSTRAINT "levels_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subjects" ADD CONSTRAINT "subjects_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "subjects" ADD CONSTRAINT "subjects_level_id_fkey" FOREIGN KEY ("level_id") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "academic_courses" ADD CONSTRAINT "academic_courses_period_id_fkey" FOREIGN KEY ("period_id") REFERENCES "academic_periods"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "academic_courses" ADD CONSTRAINT "academic_courses_level_id_fkey" FOREIGN KEY ("level_id") REFERENCES "levels"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_careers" ADD CONSTRAINT "space_careers_space_id_fkey" FOREIGN KEY ("space_id") REFERENCES "academic_spaces"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_careers" ADD CONSTRAINT "space_careers_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_unavailabilities" ADD CONSTRAINT "space_unavailabilities_space_id_fkey" FOREIGN KEY ("space_id") REFERENCES "academic_spaces"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedules" ADD CONSTRAINT "schedules_course_id_fkey" FOREIGN KEY ("course_id") REFERENCES "academic_courses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_versions" ADD CONSTRAINT "schedule_versions_schedule_id_fkey" FOREIGN KEY ("schedule_id") REFERENCES "schedules"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_versions" ADD CONSTRAINT "schedule_versions_created_by_id_fkey" FOREIGN KEY ("created_by_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_versions" ADD CONSTRAINT "schedule_versions_modified_by_id_fkey" FOREIGN KEY ("modified_by_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_versions" ADD CONSTRAINT "schedule_versions_published_by_id_fkey" FOREIGN KEY ("published_by_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_version_id_fkey" FOREIGN KEY ("version_id") REFERENCES "schedule_versions"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_subject_id_fkey" FOREIGN KEY ("subject_id") REFERENCES "subjects"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_professor_id_fkey" FOREIGN KEY ("professor_id") REFERENCES "professors"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "schedule_blocks" ADD CONSTRAINT "schedule_blocks_space_id_fkey" FOREIGN KEY ("space_id") REFERENCES "academic_spaces"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_reservations" ADD CONSTRAINT "space_reservations_space_id_fkey" FOREIGN KEY ("space_id") REFERENCES "academic_spaces"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_reservations" ADD CONSTRAINT "space_reservations_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "space_reservations" ADD CONSTRAINT "space_reservations_created_by_id_fkey" FOREIGN KEY ("created_by_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "import_jobs" ADD CONSTRAINT "import_jobs_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "import_rows" ADD CONSTRAINT "import_rows_import_job_id_fkey" FOREIGN KEY ("import_job_id") REFERENCES "import_jobs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "audit_logs" ADD CONSTRAINT "audit_logs_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE SET NULL ON UPDATE CASCADE;


-- ===== 20260729163559_enforce_import_career =====
-- AddForeignKey
ALTER TABLE "import_jobs" ADD CONSTRAINT "import_jobs_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;


-- ===== 20260729200325_passwordless_email_login =====
-- AlterTable
ALTER TABLE "users" ALTER COLUMN "password_hash" DROP NOT NULL;

-- CreateTable
CREATE TABLE "login_codes" (
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "code_hash" TEXT NOT NULL,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "expires_at" TIMESTAMP(3) NOT NULL,
    "consumed_at" TIMESTAMP(3),
    "request_ip" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "login_codes_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "login_codes_user_id_created_at_idx" ON "login_codes"("user_id", "created_at");

-- CreateIndex
CREATE INDEX "login_codes_expires_at_idx" ON "login_codes"("expires_at");

-- AddForeignKey
ALTER TABLE "login_codes" ADD CONSTRAINT "login_codes_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;


-- ===== 20260803193000_add_professor_schedule_color =====
ALTER TABLE "professors"
ADD COLUMN "schedule_color" VARCHAR(7) NOT NULL DEFAULT '#A9CBE4';


-- ===== 20260806160000_add_night_schedule_modules =====
CREATE TABLE "schedule_modules" (
    "id" UUID NOT NULL,
    "schedule_id" UUID NOT NULL,
    "module_number" INTEGER NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "schedule_modules_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "schedule_modules_schedule_id_module_number_key"
ON "schedule_modules"("schedule_id", "module_number");

ALTER TABLE "schedule_modules"
ADD CONSTRAINT "schedule_modules_schedule_id_fkey"
FOREIGN KEY ("schedule_id") REFERENCES "schedules"("id") ON DELETE CASCADE ON UPDATE CASCADE;


-- ===== 20260806161000_backfill_night_schedule_modules =====
INSERT INTO "schedule_modules" (
  "id", "schedule_id", "module_number", "start_date", "end_date", "created_at", "updated_at"
)
SELECT
  gen_random_uuid(),
  schedule."id",
  module_number,
  period."start_date" + ((module_number - 1) * INTERVAL '21 days'),
  period."start_date" + (((module_number - 1) * 21 + 18) * INTERVAL '1 day'),
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM "schedules" AS schedule
JOIN "academic_courses" AS course ON course."id" = schedule."course_id"
JOIN "academic_periods" AS period ON period."id" = course."period_id"
CROSS JOIN generate_series(1, 6) AS module_number
WHERE schedule."shift" = 'NIGHT'
ON CONFLICT ("schedule_id", "module_number") DO NOTHING;


-- ===== 20261002120000_add_subject_schedule_color =====
-- Color con el que se pinta cada asignatura en el horario diurno del libro de
-- horarios. Columna nullable: las asignaturas que ya existen se quedan sin color
-- y se les asigna uno estable al exportar, de modo que este cambio no obliga a
-- repintar nada a mano ni puede alterar los datos que ya habia.
ALTER TABLE "subjects" ADD COLUMN "color" VARCHAR(7);

-- ===== 20261002140000_drop_subject_schedule_color =====
-- El color de una clase lo da el docente (professors.schedule_color), que es lo
-- que se ve en pantalla y lo que se edita en la plataforma. La columna
-- "color" en subjects se habia anadido para llevar el color del libro antiguo,
-- pero creaba una segunda fuente de color que no corresponde a ninguna decision
-- del sistema, asi que se retira. Todas sus filas ya estaban en NULL.
ALTER TABLE "subjects" DROP COLUMN "color";
