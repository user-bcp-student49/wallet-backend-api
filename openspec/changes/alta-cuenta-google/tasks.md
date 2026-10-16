# Tasks

Referencias: [proposal.md](./proposal.md), [spec](./specs/google-account-registration/spec.md) y [design.md](./design.md).

Orden: primero caracterización del comportamiento existente, luego implementación incremental con sus pruebas y documentación en cada grupo.

## 1. Caracterización del comportamiento existente

- [x] 1.1 Caracterizar `GET /hello`: una prueba verifica `200`, cuerpo `Hello from Quarkus REST` y `Content-Type` `text/plain`. Verificación: `gradlew.bat test` pasa sin cambios de código funcional. Evidencia: `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 1.2 Registrar la línea base de compilación y pruebas: `gradlew.bat build` termina en verde antes de cualquier cambio. Verificación: salida del comando sin fallos, anotada en la descripción del PR. Evidencia local previa al cambio: `gradlew.bat build --no-daemon` → `BUILD SUCCESSFUL` (16-oct-2026); transcribir al PR al crearlo.

## 2. Modelo de identidad y cuentas (sin HTTP)

- [x] 2.1 Crear la interfaz `IdentityProvider` y el adaptador `MockGoogleIdentityProvider` con datos deterministas. Verificación: prueba unitaria; el mismo ID token de prueba devuelve siempre el mismo `subject`, `email` y `name`, y un token inválido es rechazado. Evidencia: `MockGoogleIdentityProviderTest`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 2.2 Crear la interfaz `AccountRepository` y la implementación `InMemoryAccountRepository` que busca o crea por `(proveedor, subject)`. Verificación: prueba unitaria; dos llamadas con la misma identidad devuelven una sola cuenta, y el correo no se usa como clave. Evidencia: `InMemoryAccountRepositoryTest`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 2.3 Implementar `AuthSessionService` que crea o reanuda la cuenta y arma el estado de onboarding. Verificación: prueba unitaria; cuenta nueva da `biometricRegistered` = `false` y `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`; una identidad existente conserva el mismo `user.id`. Evidencia: `AuthSessionServiceTest`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 2.4 Documentar en el código solo lo que requiera aclaración (identidad por `subject` y no por correo). Verificación: revisión de código en el PR. Evidencia: aclaración junto a la clave de identidad en `InMemoryAccountRepository`.

## 3. Contrato de entrada de `POST /v1/auth/google`

- [x] 3.1 Crear `GoogleAuthResource` en `POST /v1/auth/google` declarado público, sin exigir `Authorization`. Verificación: prueba de integración que llama sin header y no recibe `401` por falta de JWT. Evidencia: `GoogleAuthResourceTest.acceptsPublicRequestAndReturnsNewAccountSession`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 3.2 Validar `idToken`: ausente o menor de 20 caracteres responde `422`; 20 caracteres no es error de validación. Verificación: pruebas de integración para 19, 20 y campo ausente. Evidencia: `GoogleAuthResourceTest`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 3.3 Crear `ProblemExceptionMapper` que responde `application/problem+json` con `type`, `title`, `status`, `code` y `detail`. Verificación: prueba de integración que valida el `Content-Type` y las cinco propiedades en un `422`. Evidencia: `GoogleAuthResourceTest.rejectsMissingAndTooShortIdTokenAsProblem`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.

## 4. Respuesta `200` con sesión

- [x] 4.1 Implementar `SessionTokenIssuer` con JWT de aplicación de 900 segundos. La clave de firma se lee de configuración externa y no tiene valor por defecto en el código. Verificación: prueba que el `expiresIn` es `900` y que el arranque falla si falta la clave. Evidencia: `SessionTokenIssuerTest` valida duración de 900 s y rechaza secretos vacíos/débiles; sin la propiedad, Quarkus informa `Failed to load config value ... wallet.auth.jwt.secret` e impide iniciar la aplicación.
- [x] 4.2 Responder `200` con `AuthSession` completo: `accessToken`, `expiresIn`, `tokenType` = `Bearer`, `user` y `onboarding`. Verificación: prueba de integración con cuenta nueva que valida cada campo del contrato. Evidencia: `GoogleAuthResourceTest.acceptsPublicRequestAndReturnsNewAccountSession`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 4.3 Responder `401` con `INVALID_CREDENTIALS` cuando el mock rechaza el token, sin devolver `accessToken`. Verificación: prueba de integración que valida el código y la ausencia de `accessToken`. Evidencia: `GoogleAuthResourceTest.rejectsInvalidTokenWithoutReturningSessionToken`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- [x] 4.4 Verificar la reanudación: dos llamadas con el mismo ID token devuelven el mismo `user.id` y crean una sola cuenta. Verificación: prueba de integración. Evidencia: `GoogleAuthResourceTest.resumesExistingAccountWithSameUserId`; `gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.

## 5. Configuración del mock

- [x] 5.1 Activar el mock solo con una propiedad explícita de no producción; sin proveedor real en producción, el arranque debe fallar. Verificación: prueba de arranque en perfil de producción sin proveedor real que falla con mensaje claro. Evidencia: mock limitado a `dev`/`test`; `quarkusBuild` de producción exitoso con clave fixture externa; ejecución del `quarkus-run.jar` sin proveedor sale en fallo con `No identity provider is configured for this profile`.
- [x] 5.2 Documentar en `README.md` las propiedades de configuración (clave JWT, activación del mock) sin incluir valores secretos. Verificación: revisión del README y prueba de 5.1 en verde. Evidencia: README documenta `wallet.auth.jwt.secret`, `WALLET_AUTH_JWT_SECRET`, mock en `dev`/`test` y fail-fast en `prod`; no incluye valor de clave.

## 6. Límite de intentos (`429`)

- [x] 6.1 Actualizar el contrato para incluir el identificador de dispositivo que usa la clave del límite. **Requiere aprobación antes de ejecutar**: cambia `ENDPOINTS.md` y `openapi.yaml`, y el nombre y la ubicación del campo se deciden en esta tarea. Verificación: diff aprobado y `openapi.yaml` válido. Evidencia: decisión del usuario de incluir `device_id` en OpenAPI; `deviceId` obligatorio definido en OpenAPI y sincronizado en `ENDPOINTS.md`; YAML parseado y validado (1..128 caracteres).
- [ ] 6.2 Implementar `LoginRateLimiter` con ventana por identificador de dispositivo. Verificación: prueba unitaria de la ventana y prueba de integración de la respuesta `429`.
- [ ] 6.3 Responder `429` con `Retry-After` numérico en segundos y `application/problem+json`. Verificación: prueba de integración que valida que `Retry-After` es un entero positivo.

## 7. Seguridad de datos

- [ ] 7.1 Garantizar que `idToken`, `accessToken` y otros secretos no aparecen en logs ni en respuestas de error. Verificación: prueba que captura logs durante `200`, `401` y `422` y busca los valores de prueba.

## 8. Validación de integración

- [ ] 8.1 Ejecutar la suite completa `gradlew.bat test` y `gradlew.bat build` con todas las pruebas de los grupos 1 a 7 en verde. Verificación: salida sin fallos.
- [ ] 8.2 Validar el cambio OpenSpec con `openspec validate alta-cuenta-google --strict`. Verificación: comando sin errores.
- [ ] 8.3 Revisar el resultado contra `ENDPOINTS.md` y la spec, y confirmar que la desviación de firma y audiencia queda registrada. Verificación: lista de escenarios de la spec cubiertos por una prueba.

## Workflow follow-up

- Archivar el cambio con `openspec archive alta-cuenta-google` después de completar las tareas y aprobar la revisión.
- Mantener la desviación de firma y audiencia visible hasta la integración con Google.
