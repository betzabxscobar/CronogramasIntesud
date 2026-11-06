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
