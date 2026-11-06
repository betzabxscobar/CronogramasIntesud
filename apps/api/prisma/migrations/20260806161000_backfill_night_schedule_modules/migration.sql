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
