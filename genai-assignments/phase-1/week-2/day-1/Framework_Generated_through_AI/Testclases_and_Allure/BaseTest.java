package com.api.tests.base;

import com.api.framework.config.ConfigLoader;
import com.api.framework.config.EnvironmentConfig;
import com.api.framework.utils.AllureUtils;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.*;

/**
 * BaseTest — parent class for all API test classes.
 *
 * Responsibilities:
 *  - Suite-level RestAssured global config (once per run)
 *  - Environment config loaded and logged before suite starts
 *  - Per-test logging: start, pass, fail, skip
 *  - Allure environment properties written to allure-results/
 *  - @AfterMethod hooks for attaching failure details to report
 *
 * All test classes extend BaseTest and inherit these lifecycle hooks.
 */
public class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);
    protected EnvironmentConfig config;

    // ─── Suite Level ───────────────────────────────────────────────────────
    @BeforeSuite(alwaysRun = true)
    public void globalSetup() {
        config = ConfigLoader.getInstance().getConfig();

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║       API Test Suite Starting                ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Environment : {}",        config.getEnv());
        log.info("║  Base URI    : {}",        config.getBaseUri());
        log.info("║  Base Path   : {}",        config.getBasePath());
        log.info("║  Auth Type   : {}",        config.getAuth().getType());
        log.info("╚══════════════════════════════════════════════╝");

        // Set RestAssured global base URI
        RestAssured.baseURI  = config.getBaseUri();
        RestAssured.basePath = config.getBasePath();
        RestAssured.port     = config.getPort();

        // Enable RestAssured request/response logging globally
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Write environment info to allure-results/environment.properties
        writeAllureEnvironment();
    }

    @AfterSuite(alwaysRun = true)
    public void globalTeardown() {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║       API Test Suite Completed               ║");
        log.info("╚══════════════════════════════════════════════╝");
        RestAssured.reset();
    }

    // ─── Test Level ────────────────────────────────────────────────────────
    @BeforeMethod(alwaysRun = true)
    public void beforeEachTest(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        log.info("▶ START → [{}]", testName);
        Allure.label("thread", Thread.currentThread().getName());
    }

    @AfterMethod(alwaysRun = true)
    public void afterEachTest(ITestResult result) {
        String testName   = result.getMethod().getMethodName();
        int    testStatus = result.getStatus();

        switch (testStatus) {
            case ITestResult.SUCCESS -> log.info("✅ PASS  → [{}]", testName);
            case ITestResult.FAILURE -> {
                log.error("❌ FAIL  → [{}]", testName);
                if (result.getThrowable() != null) {
                    AllureUtils.attachText(
                        "Failure Reason",
                        result.getThrowable().getMessage() != null
                            ? result.getThrowable().getMessage()
                            : result.getThrowable().toString()
                    );
                    log.error("   Cause: {}", result.getThrowable().getMessage());
                }
            }
            case ITestResult.SKIP -> log.warn("⏭ SKIP  → [{}]", testName);
        }
    }

    // ─── Allure Environment Properties ────────────────────────────────────
    /**
     * Writes environment.properties to allure-results/ so the Allure report
     * shows the environment info panel on the overview page.
     */
    private void writeAllureEnvironment() {
        try {
            java.io.File allureResultsDir = new java.io.File("allure-results");
            allureResultsDir.mkdirs();

            java.util.Properties props = new java.util.Properties();
            props.setProperty("Environment",  config.getEnv());
            props.setProperty("Base.URI",     config.getBaseUri());
            props.setProperty("Base.Path",    config.getBasePath());
            props.setProperty("Auth.Type",    config.getAuth().getType());
            props.setProperty("Java.Version", System.getProperty("java.version"));
            props.setProperty("OS",           System.getProperty("os.name"));

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(
                    new java.io.File(allureResultsDir, "environment.properties"))) {
                props.store(fos, "Allure Environment Properties");
            }
            log.info("Allure environment.properties written");
        } catch (Exception e) {
            log.warn("Could not write allure environment.properties: {}", e.getMessage());
        }
    }
}
