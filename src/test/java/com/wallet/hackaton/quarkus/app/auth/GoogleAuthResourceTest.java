package com.wallet.hackaton.quarkus.app.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class GoogleAuthResourceTest {
    @Test
    void acceptsPublicRequestAndReturnsNewAccountSession() {
        given()
                .contentType("application/json")
                .body("{\"idToken\":\"mock-google-token-01\",\"deviceId\":\"device-public-test\"}")
                .when().post("/v1/auth/google")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("expiresIn", equalTo(900))
                .body("tokenType", equalTo("Bearer"))
                .body("user.id", notNullValue())
                .body("user.email", notNullValue())
                .body("user.name", notNullValue())
                .body("user.biometricRegistered", equalTo(false))
                .body("onboarding.status", equalTo("BIOMETRIC_PENDING"))
                .body("onboarding.nextAction", equalTo("REGISTER_BIOMETRIC"))
                .body("onboarding.phoneNumberMasked", nullValue());
    }

    @Test
    void rejectsMissingAndTooShortIdTokenAsProblem() {
        assertValidationProblem("{\"deviceId\":\"device-validation-test\"}");
        assertValidationProblem("{\"idToken\":\"1234567890123456789\",\"deviceId\":\"device-validation-test\"}");
    }

    @Test
    void acceptsTwentyCharacterTokenWithoutValidationError() {
        given()
                .contentType("application/json")
                .body("{\"idToken\":\"mock-google-token-01\",\"deviceId\":\"device-minimum-test\"}")
                .when().post("/v1/auth/google")
                .then()
                .statusCode(200);
    }

    @Test
    void rejectsInvalidTokenWithoutReturningSessionToken() {
        given()
                .contentType("application/json")
                .body("{\"idToken\":\"invalid-google-token-value\",\"deviceId\":\"device-invalid-test\"}")
                .when().post("/v1/auth/google")
                .then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("code", equalTo("INVALID_CREDENTIALS"))
                .body("$", not(hasKey("accessToken")));
    }

    @Test
    void resumesExistingAccountWithSameUserId() {
        String request = "{\"idToken\":\"mock-google-token-returning-user\",\"deviceId\":\"device-return-test\"}";
        String userId = given()
                .contentType("application/json")
                .body(request)
                .when().post("/v1/auth/google")
                .then()
                .statusCode(200)
                .extract().path("user.id");

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/v1/auth/google")
                .then()
                .statusCode(200)
                .body("user.id", equalTo(userId));
    }

    private void assertValidationProblem(String body) {
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/v1/auth/google")
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("type", notNullValue())
                .body("title", notNullValue())
                .body("status", equalTo(422))
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("detail", notNullValue());
    }
}
