-- schema.prisma declara ImportJob.periodId, pero ninguna migración de Prisma crea la
-- columna. Se añade aquí de forma idempotente: si la base ya la tiene (por ejemplo,
-- creada con "prisma db push"), este script no cambia nada.
ALTER TABLE "import_jobs" ADD COLUMN IF NOT EXISTS "period_id" UUID;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'import_jobs_period_id_fkey'
  ) THEN
    ALTER TABLE "import_jobs"
      ADD CONSTRAINT "import_jobs_period_id_fkey"
      FOREIGN KEY ("period_id") REFERENCES "academic_periods"("id")
      ON DELETE RESTRICT ON UPDATE CASCADE;
  END IF;
END $$;
