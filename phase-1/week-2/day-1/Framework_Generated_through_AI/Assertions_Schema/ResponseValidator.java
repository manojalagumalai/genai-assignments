package com.api.framework.validators;

import com.api.framework.config.ConfigLoader;
import com.api.framework.utils.Constants;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * ResponseValidator — centralises all assertion logic.
 *
 * Design principles:
 *  - Each validation is a discrete @Step (visible in Allure report)
 *  - Returns 'this' for fluent chaining: validator.assertStatus(200).assertSchema(...)
 *  - Attaches raw response body to the Allure report on every validation
 *  - All failures surface as clear TestNG assertion messages
 *
 * Usage:
 *   new ResponseValidator(response)
 *       .assertStatusCode(200)
 *       .assertResponseTime()
 *       .assertSchema("schemas/user-schema.json")
 *       .assertBodyContains("email", "john@example.com")
 *       .assertFieldNotNull("id");
 */
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    private final Response response;
    private final long responseTimeMs;

    // ─── Constructor ───────────────────────────────────────────────────────
    public ResponseValidator(Response response) {
        this.response = response;
        this.responseTimeMs = response.timeIn(TimeUnit.MILLISECONDS);
        attachResponseToAllure();
        log.debug("ResponseValidator created | status: {} | time: {}ms",
                response.statusCode(), responseTimeMs);
    }

    // ─── Status Code ───────────────────────────────────────────────────────
    @Step("Assert status code is {expectedStatusCode}")
    public ResponseValidator assertStatusCode(int expectedStatusCode) {
        int actualStatus = response.statusCode();
        log.info("Status assertion → expected: {} | actual: {}", expectedStatusCode, actualStatus);

        Assert.assertEquals(actualStatus, expectedStatusCode,
                String.format("❌ Status code mismatch | Expected: %d | Actual: %d | Body: %s",
                        expectedStatusCode, actualStatus, response.body().asString()));
        return this;
    }

    // ─── Response Time ─────────────────────────────────────────────────────
    @Step("Assert response time is under configured threshold")
    public ResponseValidator assertResponseTime() {
        long threshold = ConfigLoader.getInstance().getResponseTimeThreshold();
        log.info("Response time assertion → actual: {}ms | threshold: {}ms",
                responseTimeMs, threshold);

        Assert.assertTrue(responseTimeMs < threshold,
                String.format("❌ Response time exceeded | Actual: %dms | Threshold: %dms",
                        responseTimeMs, threshold));
        return this;
    }

    @Step("Assert response time is under {maxMilliseconds}ms")
    public ResponseValidator assertResponseTime(long maxMilliseconds) {
        log.info("Response time assertion → actual: {}ms | max: {}ms",
                responseTimeMs, maxMilliseconds);

        Assert.assertTrue(responseTimeMs < maxMilliseconds,
                String.format("❌ Response time exceeded | Actual: %dms | Max: %dms",
                        responseTimeMs, maxMilliseconds));
        return this;
    }

    // ─── JSON Schema Validation ────────────────────────────────────────────
    @Step("Assert response matches JSON schema: {schemaPath}")
    public ResponseValidator assertSchema(String schemaPath) {
        log.info("Schema validation → {}", schemaPath);

        InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(schemaPath);
        if (schemaStream == null) {
            Assert.fail("❌ Schema file not found on classpath: " + schemaPath);
        }

        try {
            response.then().assertThat()
                    .body(JsonSchemaValidator.matchesJsonSchema(schemaStream));
            log.info("✅ Schema validation passed: {}", schemaPath);
        } catch (AssertionError e) {
            log.error("❌ Schema validation failed: {}", e.getMessage());
            Allure.addAttachment("Schema Violation", e.getMessage());
            throw e;
        }
        return this;
    }

    // ─── Content Type ──────────────────────────────────────────────────────
    @Step("Assert Content-Type header is {expectedContentType}")
    public ResponseValidator assertContentType(String expectedContentType) {
        String actual = response.getContentType();
        log.info("Content-Type assertion → expected: {} | actual: {}", expectedContentType, actual);

        Assert.assertTrue(actual.contains(expectedContentType),
                String.format("❌ Content-Type mismatch | Expected: %s | Actual: %s",
                        expectedContentType, actual));
        return this;
    }

    // ─── JSONPath Field Assertions ─────────────────────────────────────────
    @Step("Assert field '{jsonPath}' equals '{expectedValue}'")
    public ResponseValidator assertFieldEquals(String jsonPath, Object expectedValue) {
        Object actual = response.jsonPath().get(jsonPath);
        log.info("Field assertion → path: {} | expected: {} | actual: {}", jsonPath, expectedValue, actual);

        Assert.assertEquals(actual, expectedValue,
                String.format("❌ Field mismatch | Path: %s | Expected: %s | Actual: %s",
                        jsonPath, expectedValue, actual));
        return this;
    }

    @Step("Assert field '{jsonPath}' is not null")
    public ResponseValidator assertFieldNotNull(String jsonPath) {
        Object value = response.jsonPath().get(jsonPath);
        log.info("Not-null assertion → path: {} | value: {}", jsonPath, value);

        Assert.assertNotNull(value,
                String.format("❌ Field is null | Path: %s", jsonPath));
        return this;
    }

    @Step("Assert field '{jsonPath}' is null or absent")
    public ResponseValidator assertFieldIsNull(String jsonPath) {
        Object value = response.jsonPath().get(jsonPath);
        Assert.assertNull(value,
                String.format("❌ Expected null at path: %s | Actual: %s", jsonPath, value));
        return this;
    }

    @Step("Assert body contains text: '{text}'")
    public ResponseValidator assertBodyContains(String text) {
        String body = response.body().asString();
        Assert.assertTrue(body.contains(text),
                String.format("❌ Body does not contain expected text: [%s]", text));
        return this;
    }

    @Step("Assert response body is not empty")
    public ResponseValidator assertBodyNotEmpty() {
        String body = response.body().asString();
        Assert.assertFalse(body == null || body.isBlank(),
                "❌ Response body is empty or null");
        return this;
    }

    // ─── Header Assertions ─────────────────────────────────────────────────
    @Step("Assert header '{headerName}' equals '{expectedValue}'")
    public ResponseValidator assertHeader(String headerName, String expectedValue) {
        String actual = response.getHeader(headerName);
        Assert.assertEquals(actual, expectedValue,
                String.format("❌ Header mismatch | %s | Expected: %s | Actual: %s",
                        headerName, expectedValue, actual));
        return this;
    }

    @Step("Assert header '{headerName}' is present")
    public ResponseValidator assertHeaderPresent(String headerName) {
        String value = response.getHeader(headerName);
        Assert.assertNotNull(value,
                String.format("❌ Header [%s] is missing from response", headerName));
        return this;
    }

    // ─── Array Assertions ──────────────────────────────────────────────────
    @Step("Assert array '{jsonPath}' has size {expectedSize}")
    public ResponseValidator assertArraySize(String jsonPath, int expectedSize) {
        int actualSize = response.jsonPath().getList(jsonPath).size();
        Assert.assertEquals(actualSize, expectedSize,
                String.format("❌ Array size mismatch | Path: %s | Expected: %d | Actual: %d",
                        jsonPath, expectedSize, actualSize));
        return this;
    }

    @Step("Assert array '{jsonPath}' is not empty")
    public ResponseValidator assertArrayNotEmpty(String jsonPath) {
        int size = response.jsonPath().getList(jsonPath).size();
        Assert.assertTrue(size > 0,
                String.format("❌ Expected non-empty array at path: %s", jsonPath));
        return this;
    }

    // ─── Convenience Full Validations ──────────────────────────────────────
    /**
     * Standard 200 OK validation shortcut.
     * Checks: status 200 + JSON content type + response time + body not empty
     */
    @Step("Assert standard 200 OK response")
    public ResponseValidator assertOkResponse() {
        return assertStatusCode(Constants.STATUS_OK)
                .assertContentType(Constants.APPLICATION_JSON)
                .assertResponseTime()
                .assertBodyNotEmpty();
    }

    /**
     * Standard 201 Created validation shortcut.
     */
    @Step("Assert standard 201 Created response")
    public ResponseValidator assertCreatedResponse() {
        return assertStatusCode(Constants.STATUS_CREATED)
                .assertContentType(Constants.APPLICATION_JSON)
                .assertResponseTime()
                .assertBodyNotEmpty();
    }

    // ─── Raw Response Access ───────────────────────────────────────────────
    /** Returns the raw Response for any custom assertions in the test class */
    public Response getResponse() {
        return response;
    }

    /** Extracts a field value from the response body using JSONPath */
    public <T> T extract(String jsonPath) {
        return response.jsonPath().get(jsonPath);
    }

    // ─── Allure Attachment ─────────────────────────────────────────────────
    private void attachResponseToAllure() {
        Allure.addAttachment("Response Body", "application/json",
                response.body().prettyPrint(), ".json");
        Allure.addAttachment("Response Status",
                String.valueOf(response.statusCode()));
        Allure.addAttachment("Response Time",
                responseTimeMs + " ms");
    }
}
