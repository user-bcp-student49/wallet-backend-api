# Endpoints — wallet-backend-api

Guía de integración para el flujo MVP definido en [`openapi.yaml`](./openapi.yaml).
Este documento describe el contrato esperado para el cliente; no confirma que
los endpoints estén implementados ni disponibles en un servidor.

## Datos generales

| Dato | Valor |
|---|---|
| Especificación | OpenAPI 3.1.0 |
| Sandbox | `https://sandbox.api.minka.pe` |
| Producción | `https://api.minka.pe` |
| Formato de requests y respuestas | `application/json`, salvo errores (`application/problem+json`) |
| Autenticación | JWT Bearer para los endpoints protegidos |
| Flujo | Google → registrar biometría/passkey → ingresar y verificar celular → pantalla principal |

Los endpoints marcados como públicos (`security: []`) no requieren el JWT de la
aplicación. Para los demás, enviar:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

No enviar el token en el endpoint público `POST /v1/auth/google`.

## Flujo de integración

### Registro inicial: Google y luego biometría

1. La app obtiene un ID token de Google y llama a `POST /v1/auth/google`.
2. La respuesta incluye un JWT para continuar el registro y el estado
   `BIOMETRIC_PENDING`, con la acción `REGISTER_BIOMETRIC`.
3. El registro biométrico/passkey es obligatorio: llamar a
   `POST /v1/auth/biometric/registration-challenges`, entregar sus opciones al
   autenticador del dispositivo y enviar la credencial resultante a
   `POST /v1/auth/biometric/registrations`.
4. Al registrarse la passkey, la respuesta avanza a `PHONE_PENDING`. La app ya
   puede continuar con el celular.
5. La biometría se solicita localmente por el autenticador del dispositivo. El
   rostro o su plantilla no se envían a esta API; el sistema operativo puede
   admitir otros métodos locales además del reconocimiento facial.

La biometría es obligatoria solo como paso del registro inicial. No es un método
de inicio de sesión en este MVP: en ingresos posteriores se vuelve a usar Google.

### Verificación del celular y acceso a la pantalla principal

Después de completar la passkey, consultar `GET /v1/onboarding` si se necesita
retomar el flujo. Enviar el celular con
`POST /v1/onboarding/phone/challenges`, verificar el código con
`POST /v1/onboarding/phone/verifications`. Si el código es válido, la respuesta
indica `COMPLETED` y `OPEN_HOME`; la app puede mostrar la pantalla principal. No
se requiere crear un PIN en este flujo.

---

## `POST /v1/auth/google`

### 1. Qué hace

Valida un ID token de Google y crea o reanuda la sesión y el registro de la
persona. Para una cuenta que aún no completó el registro biométrico, devuelve
`BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`; primero se debe registrar la passkey
antes de continuar al celular.

### 2. Autenticación

No requiere el JWT de la aplicación. El ID token de Google se envía en el
cuerpo. La especificación indica que el backend debe validar la firma y la
audiencia del token.

### 3. Request

`Content-Type: application/json`

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `idToken` | string | Sí | ID token de Google OpenID Connect. Mínimo 20 caracteres. |
| `deviceId` | string | Sí | Identificador estable enviado por la app; entre 1 y 128 caracteres. Se usa como clave del límite de intentos. |

Ejemplo:

```json
{
  "idToken": "<google-id-token>",
  "deviceId": "device-123456"
}
```

### 4. Respuesta exitosa — `200 OK`

Devuelve un objeto [`AuthSession`](#authsession):

| Campo | Tipo | Descripción |
|---|---|---|
| `accessToken` | string | JWT de acceso para usar los endpoints protegidos. |
| `expiresIn` | integer | Vigencia del token en segundos; el ejemplo del contrato es `900`. |
| `tokenType` | string | Constante `Bearer`. |
| `user` | object | Identidad básica e indicador `biometricRegistered`. |
| `onboarding` | object | Estado y siguiente acción; para un registro nuevo, `BIOMETRIC_PENDING` / `REGISTER_BIOMETRIC`. |

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | El ID token no se pudo verificar. |
| `422 Unprocessable Content` | El request no cumple el contrato. |
| `429 Too Many Requests` | Se excedió el límite de intentos para `deviceId`; revisar `Retry-After` (segundos). |

---

## `POST /v1/auth/biometric/registration-challenges`

### 1. Qué hace

Inicia el paso biométrico obligatorio del registro, que viene después de
autenticarse con Google y antes de ingresar el celular. Crea una passkey usando
el autenticador del dispositivo.

### 2. Autenticación

Requiere el JWT de registro obtenido después de iniciar sesión con Google y que
el paso actual sea `BIOMETRIC_PENDING`.

### 3. Request

No tiene cuerpo ni parámetros. Enviar el header `Authorization: Bearer
<accessToken>`.

### 4. Respuesta exitosa — `201 Created`

Devuelve un [`BiometricRegistrationChallenge`](#biometricregistrationchallenge):

| Campo | Tipo | Descripción |
|---|---|---|
| `challengeId` | string | Identificador del desafío de registro. |
| `publicKey` | object | Opciones WebAuthn que la app entrega al autenticador para crear la passkey. |
| `expiresAt` | string (date-time) | Fecha y hora de expiración del desafío. |

La app pasa `publicKey` a las APIs de credenciales/WebAuthn de la plataforma.
El autenticador del dispositivo puede verificar biometría facial cuando está
disponible, pero la API no recibe la cara ni su plantilla.

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | Falta el token o no es válido. |
| `409 Conflict` | El registro ya avanzó o la solicitud no corresponde al estado actual. |

---

## `POST /v1/auth/biometric/registrations`

### 1. Qué hace

Envía al backend la credencial WebAuthn creada por el dispositivo para verificar
el desafío y asociar la passkey con la cuenta autenticada con Google. Este paso
es obligatorio antes de continuar el registro.

### 2. Autenticación

Requiere el JWT de registro obtenido después de iniciar sesión con Google.

### 3. Request

`Content-Type: application/json`

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `challengeId` | string | Sí | Identificador devuelto al solicitar el desafío de registro. |
| `credential` | object | Sí | Credencial WebAuthn serializada, generada por el autenticador del dispositivo. |

Ejemplo esquemático (la estructura interna depende de WebAuthn y del cliente):

```json
{
  "challengeId": "<registration-challenge-id>",
  "credential": {
    "id": "<credential-id>",
    "type": "public-key",
    "response": {}
  }
}
```

### 4. Respuesta exitosa — `200 OK`

```json
{
  "registered": true,
  "onboarding": {
    "status": "PHONE_PENDING",
    "nextAction": "ENTER_PHONE",
    "phoneNumberMasked": null
  }
}
```

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | Falta el token o no es válido. |
| `409 Conflict` | La operación no corresponde al paso actual del registro. |
| `410 Gone` | El desafío de registro venció o ya fue utilizado. |
| `422 Unprocessable Content` | El request o la credencial no cumple el contrato. |

---

## `GET /v1/onboarding`

### 1. Qué hace

Consulta el estado actual del registro para que la app pueda abrir la pantalla
correcta al iniciar sesión o reanudar un flujo.

### 2. Autenticación

Requiere `Authorization: Bearer <accessToken>`.

### 3. Request

No tiene cuerpo ni parámetros.

### 4. Respuesta exitosa — `200 OK`

Devuelve [`Onboarding`](#onboarding):

| Campo | Tipo | Descripción |
|---|---|---|
| `status` | string | `BIOMETRIC_PENDING`, `PHONE_PENDING`, `PHONE_VERIFICATION_PENDING` o `COMPLETED`. |
| `nextAction` | string | `REGISTER_BIOMETRIC`, `ENTER_PHONE`, `VERIFY_PHONE` o `OPEN_HOME`. |
| `phoneNumberMasked` | string o null | Celular parcialmente oculto, si ya se ingresó. |

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | Falta el token o no es válido. |

---

## `POST /v1/onboarding/phone/challenges`

### 1. Qué hace

Inicia la verificación del celular: solicita el envío por SMS de un código de
seis dígitos. Solo puede llamarse después de completar el paso biométrico. Si
se genera un desafío nuevo, invalida el anterior.

### 2. Autenticación

Requiere `Authorization: Bearer <accessToken>`.

### 3. Request

`Content-Type: application/json`

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `phoneNumber` | string | Sí | Número peruano en formato E.164: `+519` seguido de ocho dígitos. |

Ejemplo:

```json
{
  "phoneNumber": "+51999999999"
}
```

### 4. Respuesta exitosa — `201 Created`

Devuelve [`PhoneChallenge`](#phonechallenge):

| Campo | Tipo | Descripción |
|---|---|---|
| `challengeId` | string | Identificador que se necesita para validar el código. |
| `phoneNumberMasked` | string | Celular parcialmente oculto. |
| `expiresAt` | string (date-time) | Vencimiento del código. |
| `resendAvailableAt` | string (date-time) | Momento desde el que se permite solicitar otro código. |

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | Falta el token o no es válido. |
| `409 Conflict` | El celular ya está asociado a otra cuenta o todavía no se completó la biometría. |
| `422 Unprocessable Content` | El número no tiene el formato esperado. |
| `429 Too Many Requests` | Se excedió el límite de solicitudes; revisar `Retry-After`. |

---

## `POST /v1/onboarding/phone/verifications`

### 1. Qué hace

Valida el código recibido por SMS. Si es correcto, marca el celular como
verificado, completa el registro y permite que la app continúe a la pantalla
principal.

### 2. Autenticación

Requiere `Authorization: Bearer <accessToken>`.

### 3. Request

`Content-Type: application/json`

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---:|---|
| `challengeId` | string | Sí | Identificador devuelto por `POST /v1/onboarding/phone/challenges`. |
| `code` | string | Sí | Código SMS de exactamente seis dígitos. Se trata como dato secreto de escritura. |

Ejemplo:

```json
{
  "challengeId": "<phone-challenge-id>",
  "code": "482913"
}
```

### 4. Respuesta exitosa — `200 OK`

Devuelve [`Onboarding`](#onboarding) con `status: "COMPLETED"` y
`nextAction: "OPEN_HOME"` cuando la verificación termina correctamente.

Ejemplo:

```json
{
  "status": "COMPLETED",
  "nextAction": "OPEN_HOME",
  "phoneNumberMasked": "+51 999 *** 999"
}
```

### 5. Respuestas de error

| Código | Cuándo ocurre |
|---|---|
| `401 Unauthorized` | Falta el token o no es válido. |
| `409 Conflict` | La biometría o el desafío de celular no corresponde al estado actual. |
| `410 Gone` | El código venció o ya fue utilizado. |
| `422 Unprocessable Content` | El código es incorrecto o el request no cumple el formato. |
| `429 Too Many Requests` | Se excedió el límite de intentos; revisar `Retry-After`. |

---

## Modelos compartidos

### `AuthSession`

```json
{
  "accessToken": "<application-jwt>",
  "expiresIn": 900,
  "tokenType": "Bearer",
  "user": {
    "id": "usr_example",
    "email": "persona@example.com",
    "name": "Valeria",
    "biometricRegistered": false
  },
  "onboarding": {
    "status": "BIOMETRIC_PENDING",
    "nextAction": "REGISTER_BIOMETRIC",
    "phoneNumberMasked": null
  }
}
```

`biometricRegistered` es `false` hasta que se completa el paso obligatorio de
registro de passkey.

### `BiometricRegistrationChallenge`

```json
{
  "challengeId": "reg_example",
  "publicKey": {},
  "expiresAt": "2026-10-16T18:00:00Z"
}
```

Después de completar el registro de la passkey, la respuesta incluye este estado:

### `Onboarding`

```json
{
  "status": "COMPLETED",
  "nextAction": "OPEN_HOME",
  "phoneNumberMasked": "+51 999 *** 999"
}
```

La app navega a la pantalla principal cuando recibe este estado tras verificar
el código SMS.

Los objetos `publicKey` de desafío se representan como `{}` en los ejemplos
porque sus valores dependen del servicio WebAuthn; las respuestas reales deben
contener las opciones requeridas por el autenticador.

### `PhoneChallenge`

```json
{
  "challengeId": "phc_example",
  "phoneNumberMasked": "+51 999 *** 999",
  "expiresAt": "2026-10-16T18:00:00Z",
  "resendAvailableAt": "2026-10-16T17:01:00Z"
}
```

### Error `Problem`

Los errores tienen el tipo de contenido `application/problem+json` y siguen el
modelo del contrato:

```json
{
  "type": "https://api.minka.pe/problems/invalid-credentials",
  "title": "No se pudo verificar la credencial",
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "detail": "Verifica tu acceso e inténtalo nuevamente."
}
```

El contrato requiere `type`, `title`, `status`, `code` y `detail`. El texto de
ejemplo ilustra la forma, no un mensaje garantizado para cada error.

---

## Aspectos que el contrato no especifica

- La duración concreta de los desafíos WebAuthn y del código SMS; solo expone
  `expiresAt`.
- El formato completo de los objetos de credenciales y opciones WebAuthn; debe
  integrarse conforme a la plataforma cliente y al servidor WebAuthn elegido.
- La autenticación WebAuthn confirma verificación local del usuario, pero no
  garantiza que todos los dispositivos usen específicamente el rostro; según
  el dispositivo, el sistema operativo podría permitir otro método local.
- La duración/configuración de la política de reenvío de SMS; solo expone
  `resendAvailableAt`.
- El formato de la respuesta `Problem` para cada error concreto, más allá del
  esquema común y los códigos HTTP definidos.

No hay `Idempotency-Key` definido en esta versión del OpenAPI. No asumir que el
backend deduplica solicitudes.

## Estado de implementación

Este archivo describe el contrato OpenAPI y no es evidencia de que el flujo esté
implementado. Antes de integrar la app, confirmar con el backend las URLs
disponibles, el estado de implementación de cada endpoint y la configuración
real de Google, SMS, JWT y WebAuthn.
