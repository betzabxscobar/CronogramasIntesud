import { Injectable, UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { JwtService } from "@nestjs/jwt";
import { RecordStatus } from "@prisma/client";
import {
  createHash,
  createHmac,
  randomBytes,
  randomInt,
  timingSafeEqual
} from "node:crypto";
import { MailService } from "../mail/mail.service";
import { PrismaService } from "../prisma/prisma.service";
import type { AccessTokenPayload, AuthenticatedUser } from "./auth.types";
import type { RequestCodeDto } from "./dto/request-code.dto";
import type { VerifyCodeDto } from "./dto/verify-code.dto";

const GENERIC_REQUEST_MESSAGE =
  "Si la cédula está autorizada, recibirás un código de acceso.";
const INVALID_CODE_MESSAGE = "Código inválido o vencido";
const CODE_TTL_MS = 5 * 60_000;
const RESEND_WAIT_MS = 60_000;
const MAX_CODE_ATTEMPTS = 5;

interface SessionMetadata {
  ipAddress?: string;
  userAgent?: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    private readonly mail: MailService
  ) {}

  async requestCode(input: RequestCodeDto, metadata: SessionMetadata = {}) {
    const nationalId = input.nationalId.trim();
    const user = await this.prisma.user.findUnique({
      where: { nationalId },
      select: {
        id: true,
        email: true,
        firstNames: true,
        status: true,
        loginCodes: {
          where: { createdAt: { gt: new Date(Date.now() - RESEND_WAIT_MS) } },
          take: 1
        }
      }
    });

    if (
      !user ||
      user.status !== RecordStatus.ACTIVE ||
      user.loginCodes.length > 0
    ) {
      this.hashCode("000000");
      return { message: GENERIC_REQUEST_MESSAGE };
    }

    const code = randomInt(0, 1_000_000).toString().padStart(6, "0");
    await this.prisma.loginCode.updateMany({
      where: { userId: user.id, consumedAt: null },
      data: { consumedAt: new Date() }
    });
    const loginCode = await this.prisma.loginCode.create({
      data: {
        userId: user.id,
        codeHash: this.hashCode(code),
        expiresAt: new Date(Date.now() + CODE_TTL_MS),
        requestIp: metadata.ipAddress
      }
    });

    const sent = await this.mail.sendLoginCode(
      user.email,
      user.firstNames,
      code
    );
    if (!sent) {
      await this.prisma.loginCode.update({
        where: { id: loginCode.id },
        data: { consumedAt: new Date() }
      });
    }

    await this.prisma.auditLog.create({
      data: {
        userId: user.id,
        action: sent ? "AUTH_CODE_SENT" : "AUTH_CODE_DELIVERY_FAILED",
        entityType: "User",
        entityId: user.id,
        ipAddress: metadata.ipAddress,
        userAgent: metadata.userAgent
      }
    });

    return { message: GENERIC_REQUEST_MESSAGE };
  }

  async verifyCode(input: VerifyCodeDto, metadata: SessionMetadata = {}) {
    const user = await this.prisma.user.findUnique({
      where: { nationalId: input.nationalId.trim() },
      include: {
        careers: { select: { careerId: true } },
        loginCodes: {
          where: { consumedAt: null },
          orderBy: { createdAt: "desc" },
          take: 1
        }
      }
    });
    const loginCode = user?.loginCodes[0];

    if (
      !user ||
      user.status !== RecordStatus.ACTIVE ||
      !loginCode ||
      loginCode.expiresAt <= new Date() ||
      loginCode.attempts >= MAX_CODE_ATTEMPTS
    ) {
      this.hashCode(input.code);
      throw new UnauthorizedException(INVALID_CODE_MESSAGE);
    }

    if (!this.matchesHash(this.hashCode(input.code), loginCode.codeHash)) {
      await this.prisma.loginCode.update({
        where: { id: loginCode.id },
        data: {
          attempts: { increment: 1 },
          consumedAt:
            loginCode.attempts + 1 >= MAX_CODE_ATTEMPTS
              ? new Date()
              : undefined
        }
      });
      throw new UnauthorizedException(INVALID_CODE_MESSAGE);
    }

    await this.prisma.$transaction([
      this.prisma.loginCode.update({
        where: { id: loginCode.id },
        data: { consumedAt: new Date() }
      }),
      this.prisma.user.update({
        where: { id: user.id },
        data: { lastLoginAt: new Date() }
      })
    ]);

    const accessToken = await this.createAccessToken(user.id);
    const refreshToken = await this.createSession(user.id, metadata);
    await this.prisma.auditLog.create({
      data: {
        userId: user.id,
        action: "AUTH_LOGIN",
        entityType: "User",
        entityId: user.id,
        ipAddress: metadata.ipAddress,
        userAgent: metadata.userAgent
      }
    });

    return {
      accessToken,
      refreshToken,
      user: this.toAuthenticatedUser(user)
    };
  }

  async refresh(rawToken: string | undefined) {
    const parsed = this.parseRefreshToken(rawToken);
    if (!parsed) {
      throw new UnauthorizedException("Sesión inválida o vencida");
    }

    const session = await this.prisma.userSession.findUnique({
      where: { id: parsed.sessionId },
      include: {
        user: {
          include: { careers: { select: { careerId: true } } }
        }
      }
    });

    if (
      !session ||
      session.revokedAt ||
      session.expiresAt <= new Date() ||
      session.user.status !== RecordStatus.ACTIVE ||
      !this.matchesHash(this.hashToken(parsed.secret), session.refreshTokenHash)
    ) {
      throw new UnauthorizedException("Sesión inválida o vencida");
    }

    const nextSecret = randomBytes(32).toString("base64url");
    await this.prisma.userSession.update({
      where: { id: session.id },
      data: { refreshTokenHash: this.hashToken(nextSecret) }
    });

    return {
      accessToken: await this.createAccessToken(session.user.id),
      refreshToken: `${session.id}.${nextSecret}`,
      user: this.toAuthenticatedUser(session.user)
    };
  }

  async logout(rawToken: string | undefined) {
    const parsed = this.parseRefreshToken(rawToken);
    if (parsed) {
      await this.prisma.userSession.updateMany({
        where: { id: parsed.sessionId, revokedAt: null },
        data: { revokedAt: new Date() }
      });
    }
  }

  private async createAccessToken(userId: string) {
    const payload: AccessTokenPayload = { sub: userId, type: "access" };
    const expiresIn = Number(
      this.config.get<string>("ACCESS_TOKEN_TTL_SECONDS", "900")
    );
    return this.jwt.signAsync(payload, { expiresIn });
  }

  private async createSession(userId: string, metadata: SessionMetadata) {
    const secret = randomBytes(32).toString("base64url");
    const hours = Number(this.config.get<string>("SESSION_HOURS", "8"));
    const expiresAt = new Date(Date.now() + hours * 3_600_000);
    const session = await this.prisma.userSession.create({
      data: {
        userId,
        refreshTokenHash: this.hashToken(secret),
        expiresAt,
        ipAddress: metadata.ipAddress,
        userAgent: metadata.userAgent
      }
    });
    return `${session.id}.${secret}`;
  }

  private parseRefreshToken(rawToken: string | undefined) {
    if (!rawToken) return null;
    const separator = rawToken.indexOf(".");
    if (separator < 1) return null;
    return {
      sessionId: rawToken.slice(0, separator),
      secret: rawToken.slice(separator + 1)
    };
  }

  private hashCode(code: string) {
    return createHmac(
      "sha256",
      this.config.getOrThrow<string>("SESSION_SECRET")
    )
      .update(code)
      .digest("hex");
  }

  private hashToken(token: string) {
    return createHash("sha256").update(token).digest("hex");
  }

  private matchesHash(receivedHash: string, expectedHash: string) {
    const received = Buffer.from(receivedHash, "hex");
    const expected = Buffer.from(expectedHash, "hex");
    return (
      received.length === expected.length && timingSafeEqual(received, expected)
    );
  }

  private toAuthenticatedUser(user: {
    id: string;
    nationalId: string;
    firstNames: string;
    lastNames: string;
    email: string;
    role: AuthenticatedUser["role"];
    careers: Array<{ careerId: string }>;
  }): AuthenticatedUser {
    return {
      id: user.id,
      nationalId: user.nationalId,
      firstNames: user.firstNames,
      lastNames: user.lastNames,
      email: user.email,
      role: user.role,
      careerIds: user.careers.map(({ careerId }) => careerId)
    };
  }
}
