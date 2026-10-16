# Proposal

Issue de referencia: [HU-1: Alta de cuenta usando la sesión de Google](../../../backlog/issues/HU-1.md) (feature [Registro y activación del MVP de wallet](../../../backlog/epics/FEATURES-1.md)).

Baseline de comportamiento: [ENDPOINTS.md](../../../ENDPOINTS.md).

## Why

La persona que inicia el onboarding de la wallet necesita registrarse sin crear credenciales adicionales. El contrato ya define el paso de entrada, `POST /v1/auth/google`, que valida un ID token de Google y devuelve la sesión con el estado `BIOMETRIC_PENDING`. Sin requisitos observables para ese paso, cliente y backend no tienen un criterio común para:

- cuándo el alta se considera iniciada, exitosa, cancelada o fallida;
- qué estado de onboarding debe mostrarse después;
- que el alta con Google no active la wallet mientras falten pasos obligatorios (passkey y celular).

El Issue está en estado borrador y deja varias decisiones abiertas (datos de Google, cuentas existentes, mensajes, eventos analíticos). Esta propuesta fija solo el comportamiento que el contrato ya determina y deja explícito lo que queda pendiente.

## What Changes

- Definir el inicio del alta con Google desde el onboarding: la app obtiene el ID token y lo envía a `POST /v1/auth/google` sin JWT de aplicación.
- Definir la respuesta de alta exitosa: sesión (`AuthSession`) con `onboarding` en `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`, y continuación al paso biométrico obligatorio.
- Definir la reanudación de una identidad de Google que ya tiene registro, sin crear una cuenta duplicada.
- Establecer que la wallet solo se considera activa cuando el onboarding llega a `COMPLETED` / `OPEN_HOME`.
- Definir cancelación, errores de validación, errores de autenticación y límite de intentos (`429` con `Retry-After`), sin presentar el alta como exitosa.
- Fijar que ID token, JWT y demás credenciales no aparecen en eventos analíticos ni en mensajes al usuario.
- Usar un proveedor de identidad simulado (mock) en el MVP: el ID token y los datos de identidad (`id`, `email`, `name`) provienen de datos de prueba. No hay conexión real con Google.

Esta propuesta no contiene cambios de código, no define la implementación técnica ni incluye tareas.

## Capabilities

### New Capabilities
- `google-account-registration`: alta y reanudación de la cuenta de la persona mediante su sesión de Google, con el estado de onboarding resultante, cancelación, errores y restricciones de activación de la wallet.

### Modified Capabilities
Ninguna. `openspec/specs/` aún no contiene capacidades.

## Alcance

### Incluye

- Inicio del alta con Google desde el onboarding y envío del ID token a `POST /v1/auth/google`.
- Validación observable de la identidad mediante el proveedor mock (token válido o inválido según datos de prueba) y respuestas `200`, `401`, `422` y `429`.
- Creación de una cuenta nueva y reanudación de una identidad de Google ya registrada.
- Estado de onboarding tras el alta con Google y regla de no activación de la wallet.
- Cancelación y fallo de la autenticación en el dispositivo.
- Restricción de no exponer credenciales en eventos analíticos ni mensajes.

### Fuera de alcance

- Redefinir el protocolo de integración con Google. Se preserva el flujo ya fijado en `ENDPOINTS.md` (ID token enviado al backend).
- Requisitos de los pasos biométrico (passkey) y de celular. Esta propuesta solo fija que son pasos siguientes obligatorios; sus requisitos pertenecen a otras specs.
- Reglas de vinculación de cuentas existentes por correo u otra identidad distinta. Quedan pendientes de Producto y Seguridad.
- Conexión real con Google: verificación criptográfica de firma y audiencia contra Google, y configuración del cliente OAuth. En el MVP se usa un proveedor mock.
- Base de uso aprobada de los datos de Google para producción. En el MVP los datos son mocks; la aprobación real queda pendiente para la conexión con Google.
- Nombres, atributos y destino de los eventos analíticos de inicio y completitud. El Issue exige un contrato de eventos aprobado antes de especificarlos.
- Texto exacto de mensajes al usuario. La spec exige que se informe cómo reintentar, sin fijar la redacción.
- Comportamiento ante indisponibilidad del proveedor Google desde el backend. `ENDPOINTS.md` no define un código de error para ese caso.
- Inicio de sesión posterior (ingresos sucesivos). El contrato lo resuelve con Google, pero no forma parte del alta de HU-1.
- Tareas de implementación, diseño técnico y pruebas automatizadas.

## Comportamiento que debe preservarse

Estos puntos vienen de `ENDPOINTS.md` y las specs deben respetarlos:

1. `POST /v1/auth/google` es público: no requiere JWT de aplicación y el ID token viaja en el cuerpo JSON (`idToken`, mínimo 20 caracteres).
2. El backend valida el ID token antes de emitir sesión. En el MVP esa validación la realiza un proveedor mock; la verificación real de firma y audiencia queda diferida a la integración con Google. Este punto es una desviación controlada del contrato.
3. La respuesta `200` es un `AuthSession` con `accessToken`, `expiresIn`, `tokenType` = `Bearer`, `user.biometricRegistered` = `false` para cuentas nuevas y `onboarding` = `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`.
4. El orden de onboarding es Google → passkey obligatoria → celular → `OPEN_HOME`. La biometría no es método de inicio de sesión: los ingresos posteriores vuelven a usar Google.
5. Google "crea o reanuda" la sesión y el registro: no se crean cuentas duplicadas para la misma identidad.
6. Los errores usan `application/problem+json` con `type`, `title`, `status`, `code` y `detail`. Los códigos usados por este cambio son `401`, `422` y `429` (con `Retry-After`).
7. La wallet no se activa antes de `COMPLETED` / `OPEN_HOME`.
8. No se asume deduplicación en el backend: el contrato no define `Idempotency-Key`.
9. El rostro o la plantilla biométrica nunca se envían a la API.

## Diferencias entre el Issue y el contrato

| Tema | HU-1 | ENDPOINTS.md | Tratamiento en esta propuesta |
|---|---|---|---|
| Mecanismo con Google | Excluye definir el mecanismo o protocolo | Define que la app obtiene un ID token y lo envía al backend | Se preserva el contrato; el Issue se interpreta como exclusión de rediseñar el protocolo |
| Paso siguiente tras Google | "Continuar el onboarding" | Passkey obligatoria antes del celular | Se fija `BIOMETRIC_PENDING` como estado siguiente |
| Cuenta existente | Reglas pendientes de Producto y Seguridad | "Crea o reanuda" la sesión y el registro | Se especifica solo la reanudación de la misma identidad; la vinculación por correo queda fuera de alcance |
| Datos de Google | Pregunta abierta | `AuthSession.user` expone `id`, `email` y `name`; el contrato exige validar firma y audiencia | En el MVP se usan datos mock. **Desviación del contrato:** la validación real de firma y audiencia queda diferida a la integración con Google |
| Cancelación y error | Informar al usuario | Sin endpoint para cancelación; `401`, `422`, `429` | La cancelación es local al cliente: no hay request al backend |
| Analítica | Eventos acordados sin credenciales | Sin contrato de eventos | Solo se especifica la restricción de no incluir credenciales; nombres y atributos quedan pendientes |

## Impact

- **Contrato de integración:** se toma `POST /v1/auth/google` como entrada del alta. Su comportamiento queda descrito por la nueva spec `google-account-registration`.
- **Cliente (app):** debe obtener el ID token de Google, mostrar el paso biométrico tras `BIOMETRIC_PENDING` y tratar cancelación y errores según la spec.
- **Backend:** debe validar el ID token contra el proveedor mock y responder según los escenarios. No se define aquí cómo.
- **Documentación:** `ENDPOINTS.md` y `openapi.yaml` no cambian en esta propuesta.
- **Dependencias externas:** aprobación de Producto y Seguridad sobre datos solicitados y reglas de identidad (Issue, sección de validación). Sin ella, los escenarios de cuentas existentes y de datos quedan bloqueados.
- **Código y dependencias:** sin cambios.
