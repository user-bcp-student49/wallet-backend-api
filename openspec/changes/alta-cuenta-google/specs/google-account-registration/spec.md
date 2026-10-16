# Spec Delta

## Purpose

Define cómo una persona inicia y completa el alta de su cuenta de wallet con su sesión de Google, qué estado de onboarding resulta y cuándo la wallet aún no se considera activa, según el contrato de `POST /v1/auth/google`.

## ADDED Requirements

### Requirement: Inicio del alta con Google
La app SHALL ofrecer el registro con Google dentro del onboarding. Al elegirlo, SHALL obtener un ID token (en el MVP, emitido por el proveedor mock) y enviarlo a `POST /v1/auth/google` en el campo `idToken` del cuerpo JSON, sin el JWT de la aplicación.

#### Scenario: Persona inicia el alta con Google
- **WHEN** una persona en el onboarding elige registrarse con Google y el proveedor mock entrega un ID token
- **THEN** la app envía `POST /v1/auth/google` con `Content-Type: application/json` y `idToken` en el cuerpo, sin header `Authorization`

#### Scenario: Request que no cumple el contrato
- **WHEN** el campo `idToken` está ausente o tiene menos de 20 caracteres
- **THEN** el backend responde `422 Unprocessable Content` con `application/problem+json` y no emite sesión ni registra el alta

### Requirement: Proveedor de identidad mock en el MVP
En el MVP, el backend MUST resolver el ID token y los datos de identidad (`id`, `email`, `name`) mediante un proveedor mock, sin realizar llamadas a Google. Los datos del proveedor MUST ser deterministas para un mismo ID token de prueba.

#### Scenario: Alta sin conexión a Google
- **WHEN** el backend procesa un ID token durante el MVP
- **THEN** no realiza ninguna llamada de red a Google y obtiene `user.id`, `user.email` y `user.name` del proveedor mock

### Requirement: Validación de la identidad de Google
El backend MUST validar el ID token mediante el proveedor mock antes de crear o reanudar la sesión. Si la validación falla, el backend MUST responder `401 Unauthorized` y MUST NOT emitir `accessToken`.

#### Scenario: ID token no verificable
- **WHEN** el proveedor mock indica que el ID token no es válido
- **THEN** el backend responde `401 Unauthorized` con un problem que incluye `type`, `title`, `status`, `code` y `detail`, y no devuelve `accessToken`

### Requirement: Sesión de una cuenta nueva
Cuando el ID token es válido y la identidad de Google no tiene registro previo, el backend MUST responder `200 OK` con un `AuthSession` que incluya `accessToken`, `expiresIn`, `tokenType` igual a `Bearer`, `user.biometricRegistered` igual a `false` y `onboarding` con `status` igual a `BIOMETRIC_PENDING` y `nextAction` igual a `REGISTER_BIOMETRIC`.

#### Scenario: Alta nueva exitosa
- **WHEN** llega un ID token válido de una identidad de Google sin registro previo
- **THEN** el backend responde `200 OK` con `tokenType` = `Bearer`, `user.biometricRegistered` = `false`, `onboarding.status` = `BIOMETRIC_PENDING` y `onboarding.nextAction` = `REGISTER_BIOMETRIC`

#### Scenario: La app continúa al paso biométrico
- **WHEN** la app recibe `onboarding.status` = `BIOMETRIC_PENDING`
- **THEN** la app presenta el registro biométrico obligatorio y no solicita el número de celular ni muestra la pantalla principal

### Requirement: Reanudación de una identidad registrada
Si el ID token corresponde a una identidad de Google que ya tiene registro, el backend MUST reanudar ese mismo registro y MUST NOT crear una cuenta duplicada. La respuesta SHALL reflejar el estado actual del onboarding de esa cuenta.

#### Scenario: Misma identidad vuelve a autenticarse
- **WHEN** una identidad de Google ya registrada envía un ID token válido
- **THEN** el backend responde `200 OK` con el mismo `user.id` que la cuenta existente y sin crear otra cuenta

#### Scenario: Identidad con passkey ya registrada
- **WHEN** la identidad registrada ya completó el registro de passkey
- **THEN** la respuesta incluye `user.biometricRegistered` = `true` y el `onboarding` vigente de esa cuenta

### Requirement: La wallet no se activa con el alta de Google
La cuenta creada o reanudada con Google MUST NOT considerarse activa mientras `onboarding.status` no sea `COMPLETED` con `onboarding.nextAction` igual a `OPEN_HOME`.

#### Scenario: Alta con Google con pasos obligatorios pendientes
- **WHEN** el alta con Google termina y la passkey o el celular siguen pendientes
- **THEN** la wallet no está activa y la app no muestra la pantalla principal

#### Scenario: Activación al completar los pasos obligatorios
- **WHEN** la verificación del celular responde `status` = `COMPLETED` y `nextAction` = `OPEN_HOME`
- **THEN** la wallet se considera activa y la app puede mostrar la pantalla principal

### Requirement: Cancelación del alta con Google
Si la persona cancela la autenticación con Google antes de obtener un ID token, la app MUST NOT enviar solicitud a `POST /v1/auth/google`, MUST NOT presentar el alta como exitosa y SHALL informar cómo volver a intentarlo.

#### Scenario: Persona cancela en Google
- **WHEN** la persona cierra o cancela la autenticación con Google antes de obtener un ID token
- **THEN** la app no envía ninguna solicitud al backend, no avanza en el onboarding y muestra un mensaje que explica cómo volver a intentarlo

### Requirement: Errores y límite de intentos
Si la autenticación con Google falla en el dispositivo o el backend responde un error, la app MUST NOT presentar el alta como exitosa y SHALL informar cómo volver a intentarlo. Si el backend responde `429 Too Many Requests`, la app MUST NOT reenviar la solicitud antes del tiempo indicado en `Retry-After`.

#### Scenario: Google no entrega un ID token por error
- **WHEN** la autenticación con Google termina con error en el dispositivo y no hay ID token
- **THEN** la app no envía solicitud al backend, no avanza en el onboarding y muestra un mensaje que explica cómo volver a intentarlo

#### Scenario: Backend rechaza el ID token
- **WHEN** el backend responde `401 Unauthorized`
- **THEN** la app no avanza en el onboarding y muestra un mensaje que explica cómo volver a intentarlo

#### Scenario: Límite de intentos excedido
- **WHEN** el backend responde `429 Too Many Requests` con `Retry-After`
- **THEN** la app no reenvía `POST /v1/auth/google` antes de transcurrido el tiempo de `Retry-After` y no muestra el alta como exitosa

### Requirement: Credenciales fuera de eventos y mensajes
Los eventos analíticos del alta con Google y los mensajes mostrados al usuario MUST NOT incluir el ID token de Google, el `accessToken` ni otro secreto de autenticación.

#### Scenario: Evento de inicio del alta
- **WHEN** la persona inicia el alta con Google
- **THEN** ningún evento analítico emitido en ese paso contiene `idToken`, `accessToken` ni otro secreto de autenticación

#### Scenario: Error mostrado al usuario
- **WHEN** la autenticación con Google o la llamada a `POST /v1/auth/google` falla
- **THEN** el mensaje mostrado al usuario no contiene el contenido del ID token ni del JWT
