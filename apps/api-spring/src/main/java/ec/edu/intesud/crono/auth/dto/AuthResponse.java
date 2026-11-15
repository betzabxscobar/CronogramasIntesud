package ec.edu.intesud.crono.auth.dto;

import ec.edu.intesud.crono.auth.AuthenticatedUser;

/** Respuesta de verify-code y refresh. El token de refresco viaja aparte, en una cookie. */
public record AuthResponse(String accessToken, AuthenticatedUser user) {}
