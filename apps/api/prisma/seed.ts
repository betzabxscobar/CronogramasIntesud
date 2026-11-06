import { SpaceType, PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

const careers = [
  { name: "Gastronomía", slug: "gastronomia" },
  { name: "Administración de Empresas", slug: "administracion-de-empresas" },
  { name: "Desarrollo de Software", slug: "desarrollo-de-software" },
  { name: "Protección del Medio Ambiente", slug: "proteccion-del-medio-ambiente" },
  { name: "Estética Integral", slug: "estetica-integral" },
  { name: "Administración Turística", slug: "administracion-turistica" },
  { name: "Marketing", slug: "marketing" },
  { name: "Cosmiatría y Tricología", slug: "cosmiatria-y-tricologia" },
  { name: "Guía Nacional de Turismo", slug: "guia-nacional-de-turismo" }
] as const;

const classrooms = [
  ...["A", "B", "C", "D", "E", "F"].map((letter) => ({
    name: `Aula 1-${letter}`,
    code: `AULA-1-${letter}`,
    location: "Primer piso"
  })),
  ...["A", "B", "C", "D", "E", "F", "G"].map((letter) => ({
    name: `Aula 2-${letter}`,
    code: `AULA-2-${letter}`,
    location: "Segundo piso"
  })),
  { name: "Aula 2-Tutorías", code: "AULA-2-TUTORIAS", location: "Segundo piso" }
] as const;

const laboratories = [
  { name: "Laboratorio de Informática 1", code: "LAB-INF-01", location: "Aula 3-A", studentComputers: 8, hasProjector: true, hasDigitalBoard: false },
  { name: "Laboratorio de Informática 2", code: "LAB-INF-02", location: "Aula 3-B", studentComputers: 8, hasProjector: true, hasDigitalBoard: false },
  { name: "Laboratorio de Informática 3", code: "LAB-INF-03", location: "Aula 3-C", studentComputers: 6, hasProjector: true, hasDigitalBoard: false },
  { name: "Laboratorio de Informática 4", code: "LAB-INF-04", location: "Aula 3-D", studentComputers: 14, hasProjector: false, hasDigitalBoard: true },
  { name: "Laboratorio de Electrónica", code: "LAB-ELEC-01", location: "Aula 3-E", studentComputers: 10, hasProjector: false, hasDigitalBoard: true }
] as const;

const softwareCurriculum = [
  {
    order: 1,
    name: "Primer nivel",
    subjects: [
      "Matemática Básica",
      "Fundamentos de Programación",
      "Metodología de la Investigación",
      "Informática",
      "Estructura de Datos",
      "Sistemas Operativos",
      "Ofimática",
      "Matemática Aplicada"
    ]
  },
  {
    order: 2,
    name: "Segundo nivel",
    subjects: [
      "Electrónica Digital",
      "Redes de Computadoras I",
      "Arquitectura de Computadoras",
      "Base de Datos I",
      "Programación Orientada a Objetos",
      "Matemática Discreta"
    ]
  },
  {
    order: 3,
    name: "Tercer nivel",
    subjects: [
      "Base de Datos II",
      "Programación Visual",
      "Programación Web",
      "Desarrollo de Sistemas Microcontrolados",
      "Desarrollo de Aplicaciones para Móviles",
      "Redes de Computadoras II"
    ]
  },
  {
    order: 4,
    name: "Cuarto nivel",
    subjects: [
      "Administración de Redes",
      "Programación Avanzada",
      "Seguridad Informática",
      "Inteligencia Artificial",
      "Gestión de Proyectos de Software",
      "Análisis y Diseño de Sistemas",
      "Ética Profesional"
    ]
  },
  {
    order: 5,
    name: "Quinto nivel",
    subjects: [
      "Fundamentos de Programación y Base de Datos",
      "Fundamentos de Redes",
      "Vinculación con la Sociedad",
      "Ofimática - GA",
      "Introducción a la Ciberseguridad"
    ]
  }
] as const;

async function main() {
  for (const career of careers) {
    await prisma.career.upsert({
      where: { name: career.name },
      update: { slug: career.slug, status: "ACTIVE" },
      create: career
    });
  }

  const registeredCareers = await prisma.career.findMany({ select: { id: true } });
  const careerLinks = registeredCareers.map(({ id }) => ({ careerId: id }));

  for (const classroom of classrooms) {
    await prisma.academicSpace.upsert({
      where: { code: classroom.code },
      update: {
        name: classroom.name,
        location: classroom.location,
        type: SpaceType.CLASSROOM,
        careers: { deleteMany: {}, create: careerLinks }
      },
      create: {
        ...classroom,
        type: SpaceType.CLASSROOM,
        careers: { create: careerLinks }
      }
    });
  }

  for (const laboratory of laboratories) {
    await prisma.academicSpace.upsert({
      where: { code: laboratory.code },
      update: {
        ...laboratory,
        teacherComputers: 1,
        type: SpaceType.LABORATORY,
        careers: { deleteMany: {}, create: careerLinks }
      },
      create: {
        ...laboratory,
        teacherComputers: 1,
        type: SpaceType.LABORATORY,
        careers: { create: careerLinks }
      }
    });
  }

  const softwareCareer = await prisma.career.findUnique({
    where: { name: "Desarrollo de Software" },
    select: { id: true }
  });

  if (softwareCareer) {
    for (const curriculumLevel of softwareCurriculum) {
      const level = await prisma.level.upsert({
        where: {
          careerId_order: {
            careerId: softwareCareer.id,
            order: curriculumLevel.order
          }
        },
        update: { name: curriculumLevel.name, status: "ACTIVE" },
        create: {
          careerId: softwareCareer.id,
          name: curriculumLevel.name,
          order: curriculumLevel.order
        }
      });

      for (const subjectName of curriculumLevel.subjects) {
        const existingSubject = await prisma.subject.findFirst({
          where: { careerId: softwareCareer.id, name: subjectName },
          select: { id: true }
        });

        if (existingSubject) {
          await prisma.subject.update({
            where: { id: existingSubject.id },
            data: { levelId: level.id, status: "ACTIVE" }
          });
        } else {
          await prisma.subject.create({
            data: {
              careerId: softwareCareer.id,
              levelId: level.id,
              name: subjectName
            }
          });
        }
      }
    }
  }

  const currentPeriod = await prisma.academicPeriod.upsert({
    where: { name: "2026 - 2026" },
    update: {},
    create: {
      name: "2026 - 2026",
      startDate: new Date("2026-05-11T00:00:00.000Z"),
      endDate: new Date("2026-09-18T00:00:00.000Z"),
      status: "ACTIVE"
    }
  });

  const activeLevels = await prisma.level.findMany({
    where: { status: "ACTIVE" },
    select: { id: true }
  });
  for (const level of activeLevels) {
    await prisma.academicCourse.upsert({
      where: {
        periodId_levelId: {
          periodId: currentPeriod.id,
          levelId: level.id
        }
      },
      update: {},
      create: {
        periodId: currentPeriod.id,
        levelId: level.id,
        name: "Curso único"
      }
    });
  }
}

main()
  .then(async () => {
    await prisma.$disconnect();
  })
  .catch(async (error: unknown) => {
    console.error(error);
    await prisma.$disconnect();
    process.exit(1);
  });
