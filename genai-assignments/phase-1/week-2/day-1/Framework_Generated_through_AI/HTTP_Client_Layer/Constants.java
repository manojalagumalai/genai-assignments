package com.api.framework.utils;

/**
 * Global constants used across the framework.
 */
public final class Constants {

    private Constants() {}

    // ─── HTTP Headers ──────────────────────────────────────────────────────
    public static final String CONTENT_TYPE       = "Content-Type";
    public static final String ACCEPT             = "Accept";
    public static final String AUTHORIZATION      = "Authorization";
    public static final String X_API_VERSION      = "X-Api-Version";
    public static final String X_CORRELATION_ID   = "X-Correlation-Id";

    // ─── Content Types ─────────────────────────────────────────────────────
    public static final String APPLICATION_JSON   = "application/json";
    public static final String APPLICATION_XML    = "application/xml";

    // ─── Auth ──────────────────────────────────────────────────────────────
    public static final String BEARER_PREFIX      = "Bearer ";

    // ─── Test Data ─────────────────────────────────────────────────────────
    public static final String TEST_DATA_FILE     = "testdata/testdata.xlsx";

    // ─── Schema Paths ──────────────────────────────────────────────────────
    public static final String SCHEMA_DIR         = "schemas/";
    public static final String USER_SCHEMA        = SCHEMA_DIR + "user-schema.json";

    // ─── HTTP Status Codes ─────────────────────────────────────────────────
    public static final int STATUS_OK             = 200;
    public static final int STATUS_CREATED        = 201;
    public static final int STATUS_NO_CONTENT     = 204;
    public static final int STATUS_BAD_REQUEST    = 400;
    public static final int STATUS_UNAUTHORIZED   = 401;
    public static final int STATUS_FORBIDDEN      = 403;
    public static final int STATUS_NOT_FOUND      = 404;
    public static final int STATUS_SERVER_ERROR   = 500;
}
