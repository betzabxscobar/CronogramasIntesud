import type { UserRole } from "@prisma/client";

export interface AccessTokenPayload {
  sub: string;
  type: "access";
}

export interface AuthenticatedUser {
  id: string;
  nationalId: string;
  firstNames: string;
  lastNames: string;
  email: string;
  role: UserRole;
  careerIds: string[];
}
