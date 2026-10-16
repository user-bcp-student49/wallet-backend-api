# wallet-app

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/wallet-app-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

## Alta con Google (MVP)

El endpoint `POST /v1/auth/google` usa `MockGoogleIdentityProvider` únicamente
en los perfiles `dev` y `test`; no realiza llamadas a Google. Para probarlo,
envía un `idToken` con el prefijo `mock-google-token-` y un sufijo no vacío,
junto con un `deviceId`. La identidad resultante es determinista para cada
token.

La sesión requiere una clave JWT configurada fuera del repositorio mediante
`wallet.auth.jwt.secret` o la variable de entorno `WALLET_AUTH_JWT_SECRET`.
La clave debe tener al menos 32 bytes. La aplicación no tiene una clave por
defecto; si falta o es débil, el inicio falla. No uses la clave de pruebas en
desarrollo compartido ni producción.

El mock no se registra en el perfil `prod`. La construcción de producción falla
si no hay un proveedor de identidad real configurado.
