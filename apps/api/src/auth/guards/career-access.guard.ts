import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable
} from "@nestjs/common";
import { UserRole } from "@prisma/client";
import type { Request } from "express";
import type { AuthenticatedUser } from "../auth.types";

@Injectable()
export class CareerAccessGuard implements CanActivate {
  canActivate(context: ExecutionContext) {
    const request = context.switchToHttp().getRequest<
      Request & { user?: AuthenticatedUser }
    >();
    const careerParam = request.params.careerId;
    const careerId = Array.isArray(careerParam)
      ? careerParam[0]
      : careerParam;
    const user = request.user;

    if (!user) return false;
    if (user.role === UserRole.ADMIN) return true;
    if (!careerId || !user.careerIds.includes(careerId)) {
      throw new ForbiddenException(
        "No tiene acceso a la carrera solicitada"
      );
    }
    return true;
  }
}
