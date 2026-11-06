import { UnauthorizedException } from "@nestjs/common";
import { RecordStatus, UserRole } from "@prisma/client";
import { createHmac } from "node:crypto";
import { AuthService } from "./auth.service";

describe("AuthService", () => {
  const testSecret = "test-secret-with-more-than-32-characters";
  const prisma = {
    user: {
      findUnique: jest.fn(),
      update: jest.fn()
    },
    loginCode: {
      create: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn()
    },
    userSession: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn()
    },
    auditLog: {
      create: jest.fn()
    },
    $transaction: jest.fn().mockResolvedValue([])
  };
  const jwt = {
    signAsync: jest.fn().mockResolvedValue("access-token")
  };
  const config = {
    get: jest.fn((_key: string, fallback: unknown) => fallback),
    getOrThrow: jest.fn(() => testSecret)
  };
  const mail = {
    sendLoginCode: jest.fn().mockResolvedValue(true)
  };
  const service = new AuthService(
    prisma as never,
    jwt as never,
    config as never,
    mail as never
  );

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("responde igual cuando la cédula no está registrada", async () => {
    prisma.user.findUnique.mockResolvedValue(null);

    await expect(
      service.requestCode({ nationalId: "1712345678" })
    ).resolves.toEqual({
      message: "Si la cédula está autorizada, recibirás un código de acceso."
    });
    expect(mail.sendLoginCode).not.toHaveBeenCalled();
  });

  it("genera y envía un código de seis dígitos a un usuario activo", async () => {
    prisma.user.findUnique.mockResolvedValue({
      id: "146d2168-55a2-4384-bb66-7fc1ecbe4fc8",
      email: "ana@intesud.edu.ec",
      firstNames: "Ana",
      status: RecordStatus.ACTIVE,
      loginCodes: []
    });
    prisma.loginCode.updateMany.mockResolvedValue({ count: 0 });
    prisma.loginCode.create.mockResolvedValue({
      id: "38f5475e-a0bd-4ee2-971e-500f64ac1f6d"
    });
    prisma.auditLog.create.mockResolvedValue({});

    await service.requestCode({ nationalId: "1712345678" });

    expect(mail.sendLoginCode).toHaveBeenCalledWith(
      "ana@intesud.edu.ec",
      "Ana",
      expect.stringMatching(/^\d{6}$/)
    );
    expect(prisma.loginCode.create).toHaveBeenCalled();
  });

  it("rechaza códigos incorrectos e incrementa los intentos", async () => {
    prisma.user.findUnique.mockResolvedValue({
      id: "146d2168-55a2-4384-bb66-7fc1ecbe4fc8",
      status: RecordStatus.ACTIVE,
      loginCodes: [
        {
          id: "38f5475e-a0bd-4ee2-971e-500f64ac1f6d",
          codeHash: hashCode("123456"),
          attempts: 0,
          expiresAt: new Date(Date.now() + 60_000)
        }
      ]
    });
    prisma.loginCode.update.mockResolvedValue({});

    await expect(
      service.verifyCode({ nationalId: "1712345678", code: "654321" })
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(prisma.loginCode.update).toHaveBeenCalled();
  });

  it("crea una sesión al verificar un código válido", async () => {
    prisma.user.findUnique.mockResolvedValue({
      id: "146d2168-55a2-4384-bb66-7fc1ecbe4fc8",
      nationalId: "1712345678",
      firstNames: "Ana",
      lastNames: "Coordinadora",
      email: "ana@intesud.edu.ec",
      role: UserRole.COORDINATOR,
      status: RecordStatus.ACTIVE,
      careers: [{ careerId: "dca0406d-247b-48a3-8a52-905f27bbb39d" }],
      loginCodes: [
        {
          id: "38f5475e-a0bd-4ee2-971e-500f64ac1f6d",
          codeHash: hashCode("123456"),
          attempts: 0,
          expiresAt: new Date(Date.now() + 60_000)
        }
      ]
    });
    prisma.userSession.create.mockResolvedValue({
      id: "6e40135f-864d-46b4-bf57-37d5cfcb1f04"
    });
    prisma.auditLog.create.mockResolvedValue({});

    const result = await service.verifyCode({
      nationalId: "1712345678",
      code: "123456"
    });

    expect(result.accessToken).toBe("access-token");
    expect(result.user.role).toBe(UserRole.COORDINATOR);
    expect(result.refreshToken).toContain(
      "6e40135f-864d-46b4-bf57-37d5cfcb1f04."
    );
  });

  function hashCode(code: string) {
    return createHmac("sha256", testSecret).update(code).digest("hex");
  }
});
