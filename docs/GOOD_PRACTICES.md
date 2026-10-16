# Buenas prácticas — Backend Quarkus

> Convenciones a las que debe converger el refactor. Se
> consultan durante el refactor, no después.

- **Capas separadas**: Resource (HTTP) → Service (reglas de negocio) →
  Repository (persistencia). Nada de lógica de negocio ni acceso a datos dentro
  del Resource.
- **DTOs propios**: la entidad de persistencia (`UserRecord`) no se devuelve tal
  cual en la respuesta HTTP; se mapea a un DTO de salida.
- **Contraseñas**: hash lento con salt (por ejemplo BCrypt). Nunca en texto
  plano, nunca en logs.
- **Tokens**: firmados, con expiración y claims explícitos. Nada de Base64
  casero con un secreto embebido en el código.
- **Configuración**: variables de entorno o `application.properties`. Ningún
  secreto hardcodeado en una clase Java.
- **SQL**: siempre `PreparedStatement` o parámetros nombrados. Nunca
  concatenación de strings.
- **Programación imperativa y virtual threads**: preferir un flujo imperativo,
  secuencial y fácil de seguir para la lógica de negocio; evitar mezclar estilos
  reactivos e imperativos sin necesidad. Usar virtual threads para atender
  operaciones bloqueantes cuando el endpoint o tarea esté configurado para ello,
  sin bloquear el event loop. Mantener límites explícitos para conexiones y
  llamadas a servicios externos: los virtual threads no aumentan la capacidad
  de esos recursos.
- **SOLID**: cada clase debe tener una responsabilidad clara; extender el
  comportamiento sin modificar código estable; respetar los contratos de las
  abstracciones; mantener interfaces pequeñas y específicas; y depender de
  abstracciones, no de implementaciones concretas. Evita clases o servicios que
  concentren responsabilidades no relacionadas.
- **Reintentos y tolerancia a fallas**: reintentar solo errores transitorios y
  operaciones idempotentes (o protegidas con una clave de idempotencia). Limitar
  los intentos, aplicar espera exponencial con jitter y un tiempo máximo; no
  reintentar errores de validación o autenticación. Registrar el fallo final y
  devolver una respuesta controlada, sin exponer detalles internos.
- **Errores**: mensajes genéricos al cliente. El detalle completo (stacktrace,
  clase de la excepción) va solo al log del servidor, nunca a la respuesta HTTP.
- **Dependencias**: si CodeQL o Dependency Review marca un CVE, se investiga y
  se corrige — no se silencia.
                                                                                            