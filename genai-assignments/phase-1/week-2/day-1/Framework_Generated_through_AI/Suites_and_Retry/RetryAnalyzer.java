package com.api.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * RetryAnalyzer — automatically retries flaky/failed tests.
 *
 * Max retry count is read from the system property 'retries'.
 * Default: 1 retry if not specified.
 *
 * Usage in @Test:
 *   @Test(retryAnalyzer = RetryAnalyzer.class)
 *
 * Or applied globally via RetryListener (see RetryListener.java).
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log  = LoggerFactory.getLogger(RetryAnalyzer.class);
    private static final int MAX_RETRIES;

    static {
        String retryProp = System.getProperty("retries", "1");
        MAX_RETRIES = Integer.parseInt(retryProp);
    }

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            log.warn("🔄 Retrying failed test [{}] — attempt {}/{}",
                    result.getName(), retryCount, MAX_RETRIES);
            return true;
        }
        log.error("❌ Test [{}] failed after {} retry attempt(s)",
                result.getName(), MAX_RETRIES);
        return false;
    }
}
