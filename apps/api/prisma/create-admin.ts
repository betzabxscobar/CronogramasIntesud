import { PrismaClient, RecordStatus, UserRole } from "@prisma/client";

const prisma = new PrismaClient();

function required(name: string) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Falta la variable ${name}`);
  return value;
}

async function main() {
  const nationalId = required("INITIAL_ADMIN_NATIONAL_ID");
  const firstNames = required("INITIAL_ADMIN_FIRST_NAMES");
  const lastNames = required("INITIAL_ADMIN_LAST_NAMES");
  const email = required("INITIAL_ADMIN_EMAIL").toLowerCase();

  const existing = await prisma.user.findFirst({
    where: { OR: [{ nationalId }, { email }] },
    select: { id: true }
  });
  if (existing) {
    throw new Error("Ya existe un usuario con esa cédula o correo");
  }

  await prisma.user.create({
    data: {
      nationalId,
      firstNames,
      lastNames,
      email,
      role: UserRole.ADMIN,
      status: RecordStatus.ACTIVE
    }
  });

  console.log("Administrador inicial creado correctamente");
}

main()
  .then(async () => {
    await prisma.$disconnect();
  })
  .catch(async (error: unknown) => {
    console.error(error instanceof Error ? error.message : error);
    await prisma.$disconnect();
    process.exit(1);
  });
