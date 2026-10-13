package kr.cheongmo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureBoundaryTest {
  private List<Class<?>> components(Class<? extends java.lang.annotation.Annotation> annotation) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
    return scanner.findCandidateComponents("kr.cheongmo").stream()
        .<Class<?>>map(
            bean -> {
              try {
                return (Class<?>)
                    Class.forName(bean.getBeanClassName(), false, getClass().getClassLoader());
              } catch (ClassNotFoundException error) {
                throw new IllegalStateException(error);
              }
            })
        .toList();
  }

  @Test
  void controllersDelegatePersistenceThroughServices() {
    var controllers = components(RestController.class);
    assertThat(controllers).isNotEmpty();
    for (var controller : controllers)
      for (var field : controller.getDeclaredFields()) {
        assertThat(JdbcOperations.class.isAssignableFrom(field.getType()))
            .as("%s.%s must not access JDBC", controller.getSimpleName(), field.getName())
            .isFalse();
        assertThat(field.getType().isAnnotationPresent(Repository.class))
            .as("%s.%s must delegate to a service", controller.getSimpleName(), field.getName())
            .isFalse();
      }
  }

  @Test
  void servicesDoNotDependOnJdbcOrHttpControllers() {
    var services = components(Service.class);
    assertThat(services).isNotEmpty();
    for (var service : services)
      for (var field : service.getDeclaredFields()) {
        assertThat(JdbcOperations.class.isAssignableFrom(field.getType()))
            .as("%s.%s belongs in a repository", service.getSimpleName(), field.getName())
            .isFalse();
        assertThat(field.getType().isAnnotationPresent(RestController.class))
            .as("%s.%s reverses the request dependency", service.getSimpleName(), field.getName())
            .isFalse();
      }
  }
}

