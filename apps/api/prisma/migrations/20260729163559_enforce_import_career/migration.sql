-- AddForeignKey
ALTER TABLE "import_jobs" ADD CONSTRAINT "import_jobs_career_id_fkey" FOREIGN KEY ("career_id") REFERENCES "careers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
