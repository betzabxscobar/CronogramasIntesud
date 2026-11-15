package ec.edu.intesud.crono.auth;

import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Aplica la anotación {@link Roles}. Equivale al RolesGuard de NestJS. */
@Component
public class RolesInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)) return true;

    Roles roles = method.getMethodAnnotation(Roles.class);
    if (roles == null) {
      roles = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), Roles.class);
    }
    if (roles == null) return true;

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Object principal = authentication == null ? null : authentication.getPrincipal();
    if (!(principal instanceof AuthenticatedUser user)
        || !Arrays.asList(roles.value()).contains(user.role())) {
      throw ApiException.forbidden(ApiExceptionHandler.FORBIDDEN_MESSAGE);
    }
    return true;
  }
}
