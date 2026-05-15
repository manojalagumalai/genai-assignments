package com.api.framework.config;

import lombok.Data;
import java.util.Map;

/**
 * POJO mapped from YAML config files using SnakeYAML.
 * Each field corresponds to a key in dev.yml / qa.yml / staging.yml
 */
@Data
public class EnvironmentConfig {

    private String env;
    private String baseUri;
    private String basePath;
    private int port;

    private Auth auth;
    private Timeouts timeouts;
    private Map<String, String> headers;
    private Database database;

    @Data
    public static class Auth {
        private String type;
        private String token;
    }

    @Data
    public static class Timeouts {
        private int connectionTimeout;
        private int readTimeout;
        private int responseTimeThreshold;
    }

    @Data
    public static class Database {
        private String host;
        private int port;
        private String name;
    }
}
