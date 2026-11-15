package ec.edu.intesud.crono.auth;

import ec.edu.intesud.crono.domain.UserRole;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Roles que pueden usar un controlador o un método (equivale al decorador @Roles de NestJS). Si
 * está en la clase y en el método, manda el del método.
 *
 * <p>Se comprueba en {@link RolesInterceptor}, antes de validar el cuerpo de la petición: quien
 * no tiene permiso recibe 403 aunque envíe datos inválidos.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Roles {
  UserRole[] value();
}
