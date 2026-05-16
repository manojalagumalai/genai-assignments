package com.api.tests.users;

import com.api.framework.clients.UserApiClient;
import com.api.framework.utils.AllureUtils;
import com.api.framework.utils.Constants;
import com.api.framework.utils.ExcelDataReader;
import com.api.framework.validators.ResponseValidator;
import com.api.tests.base.BaseTest;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * UserApiTest — End-to-end API tests for the /users resource.
 *
 * Test Coverage:
 *  ✅ GET    /users           — list all users
 *  ✅ GET    /users/{id}      — get user by ID (positive + negative)
 *  ✅ POST   /users           — create user (Excel DataProvider)
 *  ✅ PUT    /users/{id}      — update user (Excel DataProvider)
 *  ✅ DELETE /users/{id}      — delete user (Excel DataProvider)
 *  ✅ GET    /users/{id}      — unauthorized access (401)
 *  ✅ GET    /users/{id}      — not found (404)
 *
 * Allure Annotations:
 *  @Epic     → top-level grouping in Allure report
 *  @Feature  → feature grouping
 *  @Story    → user story
 *  @Severity → test priority
 *  @TmsLink  → links to test management system ticket
 *  @Issue    → links to bug tracker
 */
@Epic("User Management API")
@Feature("User CRUD Operations")
public class UserApiTest extends BaseTest {

    private UserApiClient userApiClient;

    // ─── Setup ─────────────────────────────────────────────────────────────
    @BeforeClass
    public void setupClient() {
        userApiClient = new UserApiClient();
        log.info("UserApiClient initialized for class: [{}]", getClass().getSimpleName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════

    @DataProvider(name = "createUserData", parallel = false)
    public Object[][] createUserData() {
        return ExcelDataReader.getDataProvider(Constants.TEST_DATA_FILE, "CreateUser");
    }

    @DataProvider(name = "getUserData", parallel = false)
    public Object[][] getUserData() {
        return ExcelDataReader.getDataProvider(Constants.TEST_DATA_FILE, "GetUser");
    }

    @DataProvider(name = "updateUserData", parallel = false)
    public Object[][] updateUserData() {
        return ExcelDataReader.getDataProvider(Constants.TEST_DATA_FILE, "UpdateUser");
    }

    @DataProvider(name = "deleteUserData", parallel = false)
    public Object[][] deleteUserData() {
        return ExcelDataReader.getDataProvider(Constants.TEST_DATA_FILE, "DeleteUser");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET ALL USERS
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        groups   = {"smoke", "regression"},
        priority = 1,
        description = "Verify GET /users returns 200 with a non-empty list"
    )
    @Story("Retrieve Users")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("TC-001")
    public void testGetAllUsers() {
        log.info("Test: GET all users");

        Response response = userApiClient.getAllUsers();

        new ResponseValidator(response)
                .assertStatusCode(Constants.STATUS_OK)
                .assertContentType(Constants.APPLICATION_JSON)
                .assertResponseTime()
                .assertSchema("schemas/users-list-schema.json")
                .assertArrayNotEmpty("data")
                .assertFieldNotNull("total");

        AllureUtils.logStep("Verified response contains non-empty user list");
    }

    @Test(
        groups   = {"regression"},
        priority = 2,
        description = "Verify GET /users supports pagination query params"
    )
    @Story("Retrieve Users")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("TC-002")
    public void testGetUsersWithPagination() {
        log.info("Test: GET users with pagination params");

        Map<String, Object> params = new HashMap<>();
        params.put("page", 1);
        params.put("pageSize", 5);

        Response response = userApiClient.getUsersWithParams(params);

        new ResponseValidator(response)
                .assertStatusCode(Constants.STATUS_OK)
                .assertResponseTime()
                .assertFieldEquals("page", 1)
                .assertFieldEquals("pageSize", 5);

        AllureUtils.logStep("Pagination params respected in response");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GET USER BY ID
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        dataProvider = "getUserData",
        groups   = {"smoke", "regression"},
        priority = 3,
        description = "Verify GET /users/{id} using Excel-driven test data"
    )
    @Story("Retrieve User by ID")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("TC-003")
    public void testGetUserById(Map<String, String> data) {
        AllureUtils.attachTestData(data);
        log.info("Test [{}]: GET user by ID → {}",
                data.get("testCaseId"), data.get("userId"));

        int    userId         = Integer.parseInt(data.get("userId").replaceAll("[^0-9]", "0"));
        int    expectedStatus = Integer.parseInt(data.get("expectedStatus"));

        Response response = userApiClient.getUserById(userId);

        ResponseValidator validator = new ResponseValidator(response)
                .assertStatusCode(expectedStatus)
                .assertResponseTime();

        // Additional assertions for successful responses only
        if (expectedStatus == Constants.STATUS_OK) {
            validator
                .assertSchema("schemas/user-schema.json")
                .assertFieldNotNull("id")
                .assertFieldNotNull("email");

            if (!data.get("expectedName").isBlank()) {
                validator.assertFieldEquals("name", data.get("expectedName"));
            }
            if (!data.get("expectedEmail").isBlank()) {
                validator.assertFieldEquals("email", data.get("expectedEmail"));
            }
        }

        AllureUtils.logStep(String.format("[%s] Status %d verified",
                data.get("testCaseId"), expectedStatus));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST CREATE USER
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        dataProvider = "createUserData",
        groups   = {"regression"},
        priority = 4,
        description = "Verify POST /users creates a user with Excel-driven payloads"
    )
    @Story("Create User")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("TC-004")
    public void testCreateUser(Map<String, String> data) {
        AllureUtils.attachTestData(data);
        log.info("Test [{}]: POST create user → {}",
                data.get("testCaseId"), data.get("testDescription"));

        // Build request payload from Excel row
        Map<String, Object> payload = new HashMap<>();
        payload.put("name",     data.get("name"));
        payload.put("email",    data.get("email"));
        payload.put("password", data.get("password"));
        payload.put("role",     data.get("role"));
        payload.put("status",   data.get("status"));

        AllureUtils.attachJson("Request Payload", payload.toString());

        int expectedStatus = Integer.parseInt(data.get("expectedStatus"));

        Response response = userApiClient.createUser(payload);

        ResponseValidator validator = new ResponseValidator(response)
                .assertStatusCode(expectedStatus)
                .assertResponseTime();

        // For 201 Created — assert resource was returned with an ID
        if (expectedStatus == Constants.STATUS_CREATED) {
            validator
                .assertSchema("schemas/user-schema.json")
                .assertFieldNotNull("id")
                .assertFieldEquals("name",   data.get("name"))
                .assertFieldEquals("email",  data.get("email"))
                .assertFieldEquals("status", data.get("status"));

            // Capture created user ID for potential chain tests
            Integer createdId = validator.extract("id");
            log.info("✅ User created with ID: {}", createdId);
            Allure.addAttachment("Created User ID", String.valueOf(createdId));
        }

        AllureUtils.logStep(String.format("[%s] %s → status %d verified",
                data.get("testCaseId"), data.get("testDescription"), expectedStatus));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUT UPDATE USER
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        dataProvider = "updateUserData",
        groups   = {"regression"},
        priority = 5,
        description = "Verify PUT /users/{id} updates a user with Excel-driven payloads"
    )
    @Story("Update User")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("TC-005")
    public void testUpdateUser(Map<String, String> data) {
        AllureUtils.attachTestData(data);
        log.info("Test [{}]: PUT update user → {}",
                data.get("testCaseId"), data.get("testDescription"));

        int userId         = Integer.parseInt(data.get("userId").replaceAll("[^0-9]", "0"));
        int expectedStatus = Integer.parseInt(data.get("expectedStatus"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("name",   data.get("name"));
        payload.put("email",  data.get("email"));
        payload.put("role",   data.get("role"));
        payload.put("status", data.get("status"));

        Response response = userApiClient.updateUser(userId, payload);

        ResponseValidator validator = new ResponseValidator(response)
                .assertStatusCode(expectedStatus)
                .assertResponseTime();

        if (expectedStatus == Constants.STATUS_OK) {
            validator
                .assertSchema("schemas/user-schema.json")
                .assertFieldEquals("id",     userId)
                .assertFieldEquals("name",   data.get("name"))
                .assertFieldEquals("email",  data.get("email"))
                .assertFieldEquals("status", data.get("status"));
        }

        AllureUtils.logStep(String.format("[%s] Update → status %d verified",
                data.get("testCaseId"), expectedStatus));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DELETE USER
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        dataProvider = "deleteUserData",
        groups   = {"regression"},
        priority = 6,
        description = "Verify DELETE /users/{id} with Excel-driven test data"
    )
    @Story("Delete User")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("TC-006")
    public void testDeleteUser(Map<String, String> data) {
        AllureUtils.attachTestData(data);
        log.info("Test [{}]: DELETE user → {}",
                data.get("testCaseId"), data.get("testDescription"));

        int userId         = Integer.parseInt(data.get("userId").replaceAll("[^0-9]", "0"));
        int expectedStatus = Integer.parseInt(data.get("expectedStatus"));

        Response response = userApiClient.deleteUser(userId);

        new ResponseValidator(response)
                .assertStatusCode(expectedStatus)
                .assertResponseTime();

        // 204 No Content — body should be empty
        if (expectedStatus == Constants.STATUS_NO_CONTENT) {
            AllureUtils.logStep("Verified 204 No Content — user deleted successfully");
        }

        AllureUtils.logStep(String.format("[%s] Delete → status %d verified",
                data.get("testCaseId"), expectedStatus));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECURITY / NEGATIVE TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Test(
        groups   = {"smoke", "regression", "security"},
        priority = 7,
        description = "Verify GET /users/{id} returns 401 when no auth token is provided"
    )
    @Story("API Security")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("TC-007")
    @Issue("SEC-101")
    public void testGetUserWithoutAuth() {
        log.info("Test: GET user without auth token — expect 401");

        Response response = userApiClient.getUserWithoutAuth(1);

        new ResponseValidator(response)
                .assertStatusCode(Constants.STATUS_UNAUTHORIZED)
                .assertResponseTime()
                .assertBodyNotEmpty();

        AllureUtils.logStep("Verified 401 Unauthorized returned without auth token");
    }

    @Test(
        groups   = {"regression"},
        priority = 8,
        description = "Verify GET /users/{id} returns 404 for a non-existent user ID"
    )
    @Story("Retrieve User by ID")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("TC-008")
    public void testGetNonExistentUser() {
        log.info("Test: GET non-existent user — expect 404");
        int nonExistentId = 999999;

        Response response = userApiClient.getUserById(nonExistentId);

        new ResponseValidator(response)
                .assertStatusCode(Constants.STATUS_NOT_FOUND)
                .assertResponseTime()
                .assertBodyNotEmpty();

        AllureUtils.logStep("Verified 404 Not Found for non-existent user ID: " + nonExistentId);
    }

    @Test(
        groups   = {"regression"},
        priority = 9,
        description = "Verify response time SLA for critical GET /users endpoint"
    )
    @Story("Performance")
    @Severity(SeverityLevel.MINOR)
    @TmsLink("TC-009")
    public void testResponseTimeSLA() {
        log.info("Test: Response time SLA check for GET /users");

        Response response = userApiClient.getAllUsers();

        new ResponseValidator(response)
                .assertStatusCode(Constants.STATUS_OK)
                .assertResponseTime(2000L);  // strict 2s SLA for this test

        AllureUtils.logStep("Response time SLA verified — under 2000ms");
    }
}
