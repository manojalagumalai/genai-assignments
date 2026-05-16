package com.api.framework.utils;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AllureUtils — helper methods for enriching Allure reports.
 *
 * Used across test classes and validators to attach:
 *  - Request/Response payloads
 *  - Test data used per test case
 *  - Custom labels (feature, story, severity)
 *  - Screenshots or text snippets as evidence
 */
public final class AllureUtils {

    private static final Logger log = LoggerFactory.getLogger(AllureUtils.class);

    private AllureUtils() {}

    // ─── Attachments ───────────────────────────────────────────────────────

    /** Attach any JSON string to the Allure report */
    public static void attachJson(String name, String json) {
        Allure.addAttachment(name, "application/json",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), ".json");
    }

    /** Attach plain text content (logs, messages) */
    public static void attachText(String name, String content) {
        Allure.addAttachment(name, "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), ".txt");
    }

    /** Attach test data map as readable text in report */
    public static void attachTestData(Map<String, String> testData) {
        StringBuilder sb = new StringBuilder("── Test Data ──\n");
        testData.forEach((k, v) -> sb.append(k).append(" : ").append(v).append("\n"));
        attachText("Test Data Used", sb.toString());
        log.debug("Test data attached to Allure report: {}", testData);
    }

    // ─── Labels ────────────────────────────────────────────────────────────

    /** Sets the Allure feature label (groups tests in report) */
    public static void setFeature(String feature) {
        Allure.label("feature", feature);
    }

    /** Sets the Allure story label */
    public static void setStory(String story) {
        Allure.label("story", story);
    }

    /** Sets the Allure severity label: blocker, critical, normal, minor, trivial */
    public static void setSeverity(String severity) {
        Allure.label("severity", severity);
    }

    /** Sets the Allure owner label */
    public static void setOwner(String owner) {
        Allure.label("owner", owner);
    }

    // ─── Step Logging ──────────────────────────────────────────────────────

    /** Logs a custom step with a description in the Allure report */
    public static void logStep(String stepDescription) {
        Allure.step(stepDescription);
        log.info("[STEP] {}", stepDescription);
    }

    /** Logs a step with PASSED status */
    public static void logStepPassed(String stepDescription) {
        Allure.step(stepDescription, Status.PASSED);
    }

    /** Logs a step with FAILED status */
    public static void logStepFailed(String stepDescription) {
        Allure.step(stepDescription, Status.FAILED);
    }
}
