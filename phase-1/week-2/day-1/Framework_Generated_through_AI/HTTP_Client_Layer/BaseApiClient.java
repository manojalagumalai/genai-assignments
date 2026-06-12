package com.api.framework.clients;

import com.api.framework.config.ConfigLoader;
import com.api.framework.config.EnvironmentConfig;
import com.api.framework.utils.Constants;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * BaseApiClient — the foundation for all API client classes.
 *
 * Responsibilities:
 *  - Builds a shared RequestSpecification wired from YAML config
 *  - Attaches Allure filter for request/response logging in reports
 *  - Provides authenticated and unauthenticated spec builders
 *  - Adds a unique X-Correlation-Id header per request for traceability
 *
 * All endpoint-specific client classes extend this class.
 */
public abstract class BaseApiClient {

    private static final Logger log = LoggerFactory.getLogger(BaseApiClient.class);

    protected final EnvironmentConfig config;
    protected final RequestSpecification requestSpec;
    protected final RequestSpecification unauthenticatedSpec;
    protected final ResponseSpecification responseSpec;

    // ─── Constructor ───────────────────────────────────────────────────────
    protected BaseApiClient() {
        this.config = ConfigLoader.getInstance().getConfig();
        this.requestSpec = buildAuthenticatedSpec();
        this.unauthenticatedSpec = buildUnauthenticatedSpec();
        this.responseSpec = buildResponseSpec();
        log.debug("BaseApiClient initialized for env: [{}]", config.getEnv());
    }

    // ─── Authenticated Request Spec ────────────────────────────────────────
    /**
     * Builds the default RequestSpecification with:
     * - Base URI + Base Path from YAML
     * - Bearer token authorization header
     * - Content-Type & Accept: application/json
     * - Allure filter for report logging
     * - Console request/response logging (LogDetail.ALL)
     * - Unique X-Correlation-Id per request
     */
    private RequestSpecification buildAuthenticatedSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(config.getBaseUri())
                .setBasePath(config.getBasePath())
                .setPort(config.getPort())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader(Constants.AUTHORIZATION,
                        Constants.BEARER_PREFIX + config.getAuth().getToken())
                .addHeader(Constants.X_API_VERSION,
                        config.getHeaders().getOrDefault(Constants.X_API_VERSION, "1.0"))
                .addHeader(Constants.X_CORRELATION_ID, generateCorrelationId())
                .addFilter(new AllureRestAssured()
                        .setRequestTemplate("allure-request.ftl")
                        .setResponseTemplate("allure-response.ftl"))
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL))
                .setRelaxedHTTPSValidation()   // Skip SSL cert validation (non-prod)
                .build();
    }

    // ─── Unauthenticated Request Spec ──────────────────────────────────────
    /**
     * Spec without Authorization header — used for:
     *  - Login / token generation endpoints
     *  - Public API endpoints
     *  - Negative auth tests
     */
    private RequestSpecification buildUnauthenticatedSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(config.getBaseUri())
                .setBasePath(config.getBasePath())
                .setPort(config.getPort())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader(Constants.X_CORRELATION_ID, generateCorrelationId())
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestLoggingFilter(LogDetail.ALL))
                .addFilter(new ResponseLoggingFilter(LogDetail.ALL))
                .setRelaxedHTTPSValidation()
                .build();
    }

    // ─── Response Spec ─────────────────────────────────────────────────────
    /**
     * Base ResponseSpecification — used to assert common response traits.
     * Endpoint clients can extend this with status-specific specs.
     */
    private ResponseSpecification buildResponseSpec() {
        return new ResponseSpecBuilder()
                .expectContentType(ContentType.JSON)
                .build();
    }

    // ─── Protected Helpers for Subclasses ─────────────────────────────────

    /**
     * Returns a base given() pre-loaded with the authenticated spec.
     * Endpoint clients call this to start building requests.
     *
     * Usage in subclass:
     *   return givenAuth()
     *       .pathParam("id", userId)
     *       .when().get("/users/{id}")
     *       .then().extract().response();
     */
    protected io.restassured.specification.RequestSender givenAuth() {
        return given().spec(requestSpec);
    }

    /**
     * Returns a base given() without auth headers.
     */
    protected io.restassured.specification.RequestSender givenNoAuth() {
        return given().spec(unauthenticatedSpec);
    }

    // ─── Utility ───────────────────────────────────────────────────────────
    /**
     * Generates a UUID-based correlation ID for request tracing.
     * Helps trace requests in server logs when debugging failures.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the configured response time threshold in milliseconds.
     * Used in ResponseValidator for performance assertions.
     */
    protected long getResponseTimeThreshold() {
        return config.getTimeouts().getResponseTimeThreshold();
    }
}
