import { PrismaClient, RecordStatus } from "@prisma/client";

const prisma = new PrismaClient();

const professors = [
  { firstNames: "Fernanda", lastNames: "Güilcapi", professionalTitle: "Magíster", scheduleColor: "#EDEDED" },
  { firstNames: "Jeremy", lastNames: "Rivera", professionalTitle: "Tecnólogo", scheduleColor: "#FAC2F6" },
  { firstNames: "Sharon", lastNames: "Palomo", professionalTitle: "Tecnóloga", scheduleColor: "#FBE5D6" },
  { firstNames: "Fabrizio", lastNames: "Villasis", professionalTitle: "Magíster", scheduleColor: "#8EA9DB" },
  { firstNames: "Gabriela", lastNames: "Morales", professionalTitle: "Tecnóloga", scheduleColor: "#E2F0D9" },
  { firstNames: "Pablo", lastNames: "Pasquel", professionalTitle: "Magíster", scheduleColor: "#808080" },
  { firstNames: "Mabel", lastNames: "Carrera", professionalTitle: "Magíster", scheduleColor: "#D9E2F3" },
  { firstNames: "Cristian", lastNames: "Salazar", professionalTitle: "Ingeniero/a", scheduleColor: "#BFBFBF" },
  { firstNames: "Juan José", lastNames: "Flores", professionalTitle: "Magíster", scheduleColor: "#FFFFFF" },
] as const;

const curriculum = [
  { levelOrder: 1, subjects: ["Matemática Básica", "Fundamentos de Programación", "Metodología de la Investigación", "Informática", "Estructura de Datos", "Sistemas Operativos", "Ofimática", "Matemática Aplicada"] },
  { levelOrder: 2, subjects: ["Electrónica Digital", "Redes de Computadoras I", "Arquitectura de Computadoras", "Base de Datos I", "Programación Orientada a Objetos", "Matemática Discreta"] },
  { levelOrder: 3, subjects: ["Base de Datos II", "Programación Visual", "Programación Web", "Desarrollo de Sistemas Microcontrolados", "Desarrollo de Aplicaciones para Móviles", "Redes de Computadoras II"] },
  { levelOrder: 4, subjects: ["Administración de Redes", "Programación Avanzada", "Seguridad Informática", "Inteligencia Artificial", "Gestión de Proyectos de Software", "Análisis y Diseño de Sistemas", "Ética Profesional"] },
  { levelOrder: 5, subjects: ["Fundamentos de Programación y Base de Datos", "Fundamentos de Redes", "Vinculación con la Sociedad", "Ofimática - GA", "Introducción a la Ciberseguridad"] },
] as const;

async function main() {
  const career = await prisma.career.findUnique({
    where: { name: "Desarrollo de Software" },
    select: { id: true },
  });
  if (!career) throw new Error("No existe la carrera Desarrollo de Software");

  let createdProfessors = 0;
  let updatedProfessors = 0;
  for (const source of professors) {
    const existing = await prisma.professor.findFirst({
      where: {
        firstNames: { equals: source.firstNames, mode: "insensitive" },
        lastNames: { equals: source.lastNames, mode: "insensitive" },
      },
      select: { id: true },
    });
    const professor = existing
      ? await prisma.professor.update({
          where: { id: existing.id },
          data: { ...source, status: RecordStatus.ACTIVE },
          select: { id: true },
        })
      : await prisma.professor.create({
          data: { ...source, status: RecordStatus.ACTIVE, publicProfileEnabled: true },
          select: { id: true },
        });
    existing ? updatedProfessors++ : createdProfessors++;
    await prisma.professorCareer.upsert({
      where: { professorId_careerId: { professorId: professor.id, careerId: career.id } },
      update: { status: RecordStatus.ACTIVE },
      create: { professorId: professor.id, careerId: career.id },
    });
  }

  let createdSubjects = 0;
  let updatedSubjects = 0;
  for (const section of curriculum) {
    const level = await prisma.level.findUnique({
      where: { careerId_order: { careerId: career.id, order: section.levelOrder } },
      select: { id: true },
    });
    if (!level) throw new Error(`No existe el nivel ${section.levelOrder} de Desarrollo de Software`);
    for (const name of section.subjects) {
      const existing = await prisma.subject.findFirst({
        where: { careerId: career.id, name: { equals: name, mode: "insensitive" } },
        select: { id: true },
      });
      if (existing) {
        await prisma.subject.update({ where: { id: existing.id }, data: { name, levelId: level.id, status: RecordStatus.ACTIVE } });
        updatedSubjects++;
      } else {
        await prisma.subject.create({ data: { careerId: career.id, levelId: level.id, name } });
        createdSubjects++;
      }
    }
  }

  const [totalProfessors, totalSubjects] = await Promise.all([
    prisma.professor.count({ where: { careers: { some: { careerId: career.id, status: RecordStatus.ACTIVE } }, status: RecordStatus.ACTIVE } }),
    prisma.subject.count({ where: { careerId: career.id, status: RecordStatus.ACTIVE } }),
  ]);
  console.log(JSON.stringify({ createdProfessors, updatedProfessors, createdSubjects, updatedSubjects, totalProfessors, totalSubjects }, null, 2));
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
