# Design

Referencias: [proposal.md](./proposal.md), [spec](./specs/google-account-registration/spec.md), [HU-1](../../../backlog/issues/HU-1.md) y [ENDPOINTS.md](../../../ENDPOINTS.md).

## Contexto técnico

- El repositorio es una aplicación Quarkus (`quarkus-rest`, `quarkus-arc`) sobre Java 25, compilada con Gradle. Hoy solo existe `GreetingResource` (`GET /hello`).
- No hay persistencia, seguridad HTTP, emisión de JWT ni manejo de `application/problem+json` configurados.
- `openapi.yaml` declara `bearerAuth` como seguridad global. `POST /v1/auth/google` la sobrescribe con `security: []`. Cualquier endpoint nuevo queda protegido salvo que se declare público; el diseño debe respetar esa regla.
- En el MVP, la identidad de Google es un proveedor mock (decisión de la propuesta). La verificación real de firma y audiencia queda diferida y se registra como desviación controlada del contrato.

## Goals

1. Implementar `POST /v1/auth/google` según el contrato: `200` con `AuthSession`, `401`, `422` y `429`, todos los errores en `application/problem+json`.
2. Crear o reanudar una cuenta por identidad del proveedor, sin duplicados.
3. Devolver siempre `onboarding` `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC` para cuentas sin passkey, y nunca `COMPLETED` / `OPEN_HOME` desde este flujo.
4. Garantizar que ID token, `accessToken` y otros secretos no aparezcan en logs ni respuestas de error.
5. Mantener el mock aislado para que no pueda activarse en producción.

## Non-goals

- Verificación criptográfica real de Google, configuración de cliente OAuth o llamadas de red a Google.
- Endpoints de passkey, celular o `GET /v1/onboarding` (otras specs).
- Vinculación de cuentas por correo o reglas para cuentas existentes con otra identidad.
- Emisión de eventos analíticos (su contrato no está aprobado).
- Persistencia durable, migraciones o despliegue.
- Mensajes de UI y manejo de cancelación en el cliente (no pasan por el backend).

## Contratos protegidos

Estos comportamientos no deben cambiar. Cada uno se cubre con pruebas de contrato (ver sección de caracterización).

| Contrato | Fuente | Regla |
|---|---|---|
| Endpoint público | `openapi.yaml` `security: []` | `POST /v1/auth/google` no exige JWT de aplicación. |
| Request | `GoogleAuthRequest` | `idToken` obligatorio, mínimo 20 caracteres; si no cumple, `422`. |
| Respuesta `200` | `AuthSession` | `accessToken`, `expiresIn` (900 en el ejemplo), `tokenType` = `Bearer`, `user{id,email,name,biometricRegistered}`, `onboarding`. |
| Estado inicial | ENDPOINTS.md | Cuenta sin passkey: `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`, `biometricRegistered` = `false`. |
| Errores | `Problem` | `application/problem+json` con `type`, `title`, `status`, `code`, `detail`. Códigos de este flujo: `INVALID_CREDENTIALS` (401), validación (422), límite (429 con `Retry-After`). |
| Sin deduplicación | ENDPOINTS.md | No se asume `Idempotency-Key`; cada request se procesa. |
| Biometría | ENDPOINTS.md | El backend nunca recibe rostro ni plantilla. |
| Bearer global | `openapi.yaml` | Todo endpoint nuevo es protegido salvo declaración explícita de público. |
| `GET /hello` | código actual | Sigue respondiendo `200` con `text/plain` tras añadir seguridad y manejo de errores. |

## Componentes afectados

Todos son nuevos salvo `GreetingResource`, que no cambia.

| Componente | Responsabilidad |
|---|---|
| `GoogleAuthResource` (`POST /v1/auth/google`) | Recibe el request, valida el contrato de entrada y delega. Declara el endpoint como público. |
| `IdentityProvider` (interfaz) | Puerto que resuelve un ID token a identidad (`subject`, `email`, `name`) o rechaza. Desacopla la verificación real del MVP. |
| `MockGoogleIdentityProvider` | Adaptador mock, deterministas. Solo activo con configuración explícita de no producción. |
| `AccountRepository` (interfaz) y `InMemoryAccountRepository` | Busca o crea la cuenta por `(proveedor, subject)`. Almacena passkey y estado de onboarding. |
| `AuthSessionService` | Crea o reanuda la cuenta y arma `AuthSession` con su estado de onboarding. |
| `SessionTokenIssuer` | Emite el `accessToken` JWT de aplicación con vigencia de 900 s. |
| `LoginRateLimiter` | Aplica límite de intentos y calcula `Retry-After`. |
| `ProblemExceptionMapper` | Convierte errores de dominio y de validación en `application/problem+json`. |
| Configuración (`application.properties`) | Claves del JWT, perfil del mock, límites. Sin secretos en el repositorio. |

## Decisiones técnicas y alternativas

### D1. Puerto de identidad con adaptador mock
- **Elegido:** interfaz `IdentityProvider` con `MockGoogleIdentityProvider`. El servicio nunca depende del mock directamente.
- **Alternativa:** lógica de mock dentro del recurso REST. Descartada porque obliga a reescribir el flujo al integrar Google.
- **Consecuencia:** la integración real solo reemplaza el adaptador.

### D2. Mock acotado a no producción
- **Elegido:** el mock se activa solo con un perfil o propiedad explícita; en producción, el arranque debe fallar si no hay un `IdentityProvider` real (`fail_fast`, decidido por el usuario). Así no se puede activar por error.
- **Alternativa:** mock siempre activo con un flag en runtime. Descartada: un flag mal configurado aceptaría tokens falsos.
- **Riesgo residual:** el perfil es configuración; hay que cubrirlo con una prueba de arranque.

### D3. Identidad por `(proveedor, subject)`, no por correo
- **Elegido:** la cuenta se identifica por el `subject` del proveedor. El correo se guarda como dato, no como clave.
- **Alternativa:** buscar por correo. Descartada: la vinculación por correo está fuera de alcance y podría unir cuentas distintas.
- **Consecuencia:** dos identidades con el mismo correo generan dos cuentas. Es el comportamiento pendiente de Producto/Seguridad y debe quedar documentado.

### D4. Persistencia en memoria para el MVP
- **Elegido:** `InMemoryAccountRepository` detrás de la interfaz.
- **Alternativa:** base de datos desde el inicio. Descartada: añade migraciones y despliegue fuera del alcance del MVP.
- **Consecuencia:** al reiniciar se pierden cuentas y `user.id` cambia. La interfaz permite reemplazarla sin cambiar el servicio. Para pruebas de "misma identidad", la reanudación se verifica dentro de un mismo proceso.

### D5. Emisión del JWT de aplicación
- **Elegido:** JWT firmado con clave configurada fuera del repositorio, `expiresIn` = 900. La librería concreta (p. ej. SmallRye JWT, que se integra con Quarkus) se decide en la fase de implementación.
- **Alternativa:** token opaco guardado en memoria. Descartada: obliga a estado compartido para validar cada request, aunque sería más simple con una sola instancia.
- **Riesgo:** la clave de firma debe provenir de configuración segura; un valor por defecto en el código sería un fallo grave.

### D6. Límite de intentos (`429`)
- **Elegido:** limitador en proceso con ventana por clave, `Retry-After` en segundos.
- **Alternativa:** límite en gateway o proxy. Descartada para el MVP porque no hay gateway definido en el repositorio; debe confirmarse si existe.
- **Clave:** `device_id` (decidido por el usuario). La app envía un identificador de dispositivo y no hay `subject` antes de validar el token. El campo debe añadirse al contrato (ver tarea 6.1).
- **Trade-off:** el límite por proceso no es compartido entre instancias. Si hay varias instancias, la clave `device_id` no basta por sí sola y debe revisarse.

### D7. Errores como `Problem`
- **Elegido:** excepciones de dominio mapeadas a `ProblemExceptionMapper`.
- **Alternativa:** construir la respuesta en cada recurso. Descartada: duplica el formato y puede filtrar datos sensibles.

### D8. Analítica
- **Elegido:** este cambio no emite eventos. Solo garantiza que ningún log ni respuesta contenga secretos.
- **Alternativa:** definir eventos ahora. Descartada: el contrato de eventos no está aprobado (HU-1).

## Estrategia incremental

Cada paso debe quedar verde antes de avanzar. No se reemplaza comportamiento existente.

1. **Pruebas de caracterización de `GET /hello`** (ver siguiente sección). Establecen la línea base antes de añadir seguridad.
2. **Modelo y puerto:** `IdentityProvider`, cuenta, estados de onboarding y `AuthSession` sin expresión HTTP. Pruebas unitarias del servicio con el mock.
3. **Recurso `POST /v1/auth/google`** declarado público, con validación `422` y `Problem`. Sin JWT aún, para aislar el contrato de entrada.
4. **Emisión de JWT y `AuthSession` completa** con `200`, `expiresIn` = 900 y estado `BIOMETRIC_PENDING`.
5. **Limitador `429`** con `Retry-After`.
6. **Pruebas de seguridad transversales:** ausencia de secretos en logs y respuestas; perfil de producción sin mock.
7. **Revisión contra `ENDPOINTS.md` y la spec**, marcando la desviación de firma y audiencia.

Cada paso puede desplegarse de forma independiente porque el endpoint no está conectado al flujo de wallet activa.

## Pruebas de caracterización y de contrato

No hay comportamiento de autenticación previo que caracterizar. Las pruebas siguientes deben escribirse antes del cambio que las afecta.

**Antes de añadir seguridad o manejo de errores (paso 1):**
- `GET /hello` responde `200`, cuerpo `Hello from Quarkus REST`, `Content-Type` `text/plain`. Debe seguir igual después de cada paso.

**Contrato de entrada (paso 3):**
- Sin `idToken` → `422` con `application/problem+json`.
- `idToken` de 19 caracteres → `422`; de 20 → no es error de validación.
- La petición no lleva header `Authorization` y no se exige JWT.

**Contrato de salida (paso 4):**
- Cuenta nueva con mock válido → `200`, `tokenType` = `Bearer`, `biometricRegistered` = `false`, `onboarding` = `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`.
- Mismo `idToken` dos veces → mismo `user.id`, una sola cuenta.
- Token inválido en mock → `401` con `INVALID_CREDENTIALS` y sin `accessToken`.

**Límite (paso 5):**
- Exceso de intentos → `429` con `Retry-After` numérico.

**Seguridad de datos (paso 6):**
- El `idToken` y el `accessToken` no aparecen en logs capturados durante las pruebas.
- Con perfil de producción y sin proveedor real, el arranque falla.

## Riesgos y trade-offs

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El mock llega a producción | Cualquier token falso crea sesión | D2: perfil explícito, arranque fallido sin proveedor real, prueba de configuración. |
| Diferencia con el contrato (firma y audiencia) | Desviación de seguridad aceptada solo para el MVP | Registrada en la propuesta; la integración real la elimina. |
| Cuentas duplicadas por correo | Dos cuentas para la misma persona | D3 y decisión pendiente de Producto/Seguridad. |
| Pérdida de cuentas al reiniciar | `user.id` cambia; usuarios deben registrarse de nuevo | Aceptable solo en MVP; la interfaz permite persistencia. |
| Límite de intentos no compartido | Un atacante puede repartir intentos entre instancias | Confirmar si hay gateway; si no, persistir el contador en fase posterior. |
| Clave de firma JWT mal gestionada | Tokens falsificables | Clave solo por configuración externa; sin valor por defecto en código. |
| Endpoints nuevos heredan `bearerAuth` | Un endpoint público por error queda bloqueado | Solo `/v1/auth/google` declara público; prueba de contrato sobre seguridad por operación. |

### Decisiones pendientes (bloquean la implementación)

- **Mensaje al usuario ante `429`:** la spec pide informar, pero la redacción la define el cliente.
- **Comportamiento real de cuentas con mismo correo:** pendiente de Producto/Seguridad (ya registrado en la propuesta).

## Rollback

- **Código:** revertir el commit de la implementación elimina el endpoint. `GET /hello` no depende de este cambio.
- **Datos:** no hay almacenamiento durable, así que no hay migración que deshacer.
- **Configuración:** quitar las claves de JWT y del perfil del mock. Sin ellas el endpoint no arranca, lo que impide la activación accidental.
- **Operación:** si el endpoint debe retirarse en caliente, desactivar la ruta en configuración antes de revertir el código.
- **Clientes:** la app solo debe usar el flujo si el backend responde `200` o un `Problem` conocido; si la ruta se retira, el cliente recibe `404` y debe mostrar el mensaje de reintento.
