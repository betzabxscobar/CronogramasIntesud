import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException
} from "@nestjs/common";
import { JwtService } from "@nestjs/jwt";
import { Reflector } from "@nestjs/core";
import { RecordStatus } from "@prisma/client";
import type { Request } from "express";
import { PrismaService } from "../../prisma/prisma.service";
import type { AccessTokenPayload, AuthenticatedUser } from "../auth.types";
import { IS_PUBLIC_KEY } from "../decorators/public.decorator";

@Injectable()
export class AccessTokenGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly reflector: Reflector,
    private readonly prisma: PrismaService
  ) {}

  async canActivate(context: ExecutionContext) {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass()
    ]);
    if (isPublic) return true;

    const request = context.switchToHttp().getRequest<
      Request & { user?: AuthenticatedUser }
    >();
    const token = this.readBearerToken(request);
    if (!token) throw new UnauthorizedException("Acceso no autorizado");

    try {
      const payload = await this.jwt.verifyAsync<AccessTokenPayload>(token);
      if (payload.type !== "access") throw new Error("Invalid token type");

      const user = await this.prisma.user.findUnique({
        where: { id: payload.sub },
        include: { careers: { select: { careerId: true } } }
      });
      if (!user || user.status !== RecordStatus.ACTIVE) {
        throw new Error("Inactive user");
      }

      request.user = {
        id: user.id,
        nationalId: user.nationalId,
        firstNames: user.firstNames,
        lastNames: user.lastNames,
        email: user.email,
        role: user.role,
        careerIds: user.careers.map(({ careerId }) => careerId)
      };
      return true;
    } catch {
      throw new UnauthorizedException("Sesión inválida o vencida");
    }
  }

  private readBearerToken(request: Request) {
    const [type, token] = request.headers.authorization?.split(" ") ?? [];
    return type === "Bearer" ? token : undefined;
  }
}
