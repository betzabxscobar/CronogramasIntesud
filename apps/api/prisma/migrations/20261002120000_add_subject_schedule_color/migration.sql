-- Color con el que se pinta cada asignatura en el horario diurno del libro de
-- horarios. Columna nullable: las asignaturas que ya existen se quedan sin color
-- y se les asigna uno estable al exportar, de modo que este cambio no obliga a
-- repintar nada a mano ni puede alterar los datos que ya habia.
ALTER TABLE "subjects" ADD COLUMN "color" VARCHAR(7);