-- El color de una clase lo da el docente (professors.schedule_color), que es lo
-- que se ve en pantalla y lo que se edita en la plataforma. La columna
-- "color" en subjects se habia anadido para llevar el color del libro antiguo,
-- pero creaba una segunda fuente de color que no corresponde a ninguna decision
-- del sistema, asi que se retira. Todas sus filas ya estaban en NULL.
ALTER TABLE "subjects" DROP COLUMN "color";