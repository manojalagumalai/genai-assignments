package com.api.framework.clients;

import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * UserApiClient — Endpoint client for /users resource.
 *
 * Each method maps to one API operation and is annotated with:
 *  - @Step  → appears as a step in the Allure report
 *
 * Returns raw Response objects — assertions are handled
 * in the ResponseValidator layer, keeping this class clean.
 */
public class UserApiClient extends BaseApiClient {

    private static final Logger log = LoggerFactory.getLogger(UserApiClient.class);

    private static final String USERS_ENDPOINT       = "/users";
    private static final String USER_BY_ID_ENDPOINT  = "/users/{id}";

    // ─── Constructor ───────────────────────────────────────────────────────
    public UserApiClient() {
        super();
        log.debug("UserApiClient ready → base: {}{}", config.getBaseUri(), config.getBasePath());
    }

    // ─── GET All Users ─────────────────────────────────────────────────────
    @Step("GET all users")
    public Response getAllUsers() {
        log.info("→ GET {}", USERS_ENDPOINT);
        return given().spec(requestSpec)
                .when()
                    .get(USERS_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── GET All Users with Query Params ───────────────────────────────────
    @Step("GET users with query params: {queryParams}")
    public Response getUsersWithParams(Map<String, Object> queryParams) {
        log.info("→ GET {} | params: {}", USERS_ENDPOINT, queryParams);
        return given().spec(requestSpec)
                .queryParams(queryParams)
                .when()
                    .get(USERS_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── GET User by ID ────────────────────────────────────────────────────
    @Step("GET user by ID: {userId}")
    public Response getUserById(int userId) {
        log.info("→ GET {}/{}", USERS_ENDPOINT, userId);
        return given().spec(requestSpec)
                .pathParam("id", userId)
                .when()
                    .get(USER_BY_ID_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── POST Create User ──────────────────────────────────────────────────
    @Step("POST create user with payload: {payload}")
    public Response createUser(Map<String, Object> payload) {
        log.info("→ POST {} | body: {}", USERS_ENDPOINT, payload);
        return given().spec(requestSpec)
                .body(payload)
                .when()
                    .post(USERS_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── PUT Update User ───────────────────────────────────────────────────
    @Step("PUT update user ID: {userId}")
    public Response updateUser(int userId, Map<String, Object> payload) {
        log.info("→ PUT {}/{}", USERS_ENDPOINT, userId);
        return given().spec(requestSpec)
                .pathParam("id", userId)
                .body(payload)
                .when()
                    .put(USER_BY_ID_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── PATCH Partial Update ──────────────────────────────────────────────
    @Step("PATCH partial update user ID: {userId}")
    public Response patchUser(int userId, Map<String, Object> payload) {
        log.info("→ PATCH {}/{}", USERS_ENDPOINT, userId);
        return given().spec(requestSpec)
                .pathParam("id", userId)
                .body(payload)
                .when()
                    .patch(USER_BY_ID_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── DELETE User ───────────────────────────────────────────────────────
    @Step("DELETE user ID: {userId}")
    public Response deleteUser(int userId) {
        log.info("→ DELETE {}/{}", USERS_ENDPOINT, userId);
        return given().spec(requestSpec)
                .pathParam("id", userId)
                .when()
                    .delete(USER_BY_ID_ENDPOINT)
                .then()
                    .extract().response();
    }

    // ─── Negative: Unauthorized request ───────────────────────────────────
    @Step("GET user without auth token (expect 401)")
    public Response getUserWithoutAuth(int userId) {
        log.info("→ GET {} (no auth)", USER_BY_ID_ENDPOINT);
        return given().spec(unauthenticatedSpec)
                .pathParam("id", userId)
                .when()
                    .get(USER_BY_ID_ENDPOINT)
                .then()
                    .extract().response();
    }
}
