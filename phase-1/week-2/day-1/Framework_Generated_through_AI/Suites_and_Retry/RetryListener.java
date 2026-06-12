package com.api.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * RetryListener — applies RetryAnalyzer globally to every @Test method
 * without needing to annotate each test individually.
 *
 * Registered in testng.xml as a listener (already included).
 * Also registered in Maven Surefire plugin configuration.
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        // Apply RetryAnalyzer to every @Test automatically
        if (annotation.getRetryAnalyzerClass() == null) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
        }
    }
}
