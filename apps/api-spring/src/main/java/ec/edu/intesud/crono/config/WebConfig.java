package ec.edu.intesud.crono.config;

import ec.edu.intesud.crono.auth.RolesInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final RolesInterceptor rolesInterceptor;

  public WebConfig(RolesInterceptor rolesInterceptor) {
    this.rolesInterceptor = rolesInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(rolesInterceptor);
  }
}
