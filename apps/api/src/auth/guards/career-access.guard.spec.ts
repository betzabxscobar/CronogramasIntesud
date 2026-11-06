import { ForbiddenException } from "@nestjs/common";
import type { ExecutionContext } from "@nestjs/common";
import { UserRole } from "@prisma/client";
import { CareerAccessGuard } from "./career-access.guard";

describe("CareerAccessGuard", () => {
  const guard = new CareerAccessGuard();

  function context(role: UserRole, careerIds: string[], careerId: string) {
    return {
      switchToHttp: () => ({
        getRequest: () => ({
          params: { careerId },
          user: { role, careerIds }
        })
      })
    } as ExecutionContext;
  }

  it("permite al administrador acceder a cualquier carrera", () => {
    expect(
      guard.canActivate(context(UserRole.ADMIN, [], "career-1"))
    ).toBe(true);
  });

  it("permite al coordinador acceder a una carrera asignada", () => {
    expect(
      guard.canActivate(
        context(UserRole.COORDINATOR, ["career-1"], "career-1")
      )
    ).toBe(true);
  });

  it("rechaza carreras no asignadas al coordinador", () => {
    expect(() =>
      guard.canActivate(
        context(UserRole.COORDINATOR, ["career-1"], "career-2")
      )
    ).toThrow(ForbiddenException);
  });
});
