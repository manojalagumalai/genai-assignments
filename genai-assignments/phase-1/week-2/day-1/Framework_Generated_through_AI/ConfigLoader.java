package com.api.framework.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Singleton ConfigLoader — reads the appropriate YAML file based on
 * the 'env' system property set by Maven Surefire / GitHub Actions.
 *
 * Usage:
 *   EnvironmentConfig config = ConfigLoader.getInstance().getConfig();
 *   String baseUri = config.getBaseUri();
 *
 * Environment selection (in order of priority):
 *   1. System property  : -Denv=qa
 *   2. Env variable     : ENV=qa
 *   3. Default fallback : qa
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static ConfigLoader instance;
    private final EnvironmentConfig config;

    // ─── Private Constructor ───────────────────────────────────────────────
    private ConfigLoader() {
        String env = resolveEnvironment();
        log.info("🔧 Loading config for environment: [{}]", env);
        this.config = loadYaml(env);
        log.info("✅ Config loaded → baseUri: {}", config.getBaseUri());
    }

    // ─── Singleton Access ──────────────────────────────────────────────────
    public static synchronized ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    // ─── Public Getter ─────────────────────────────────────────────────────
    public EnvironmentConfig getConfig() {
        return config;
    }

    // ─── Environment Resolution ────────────────────────────────────────────
    /**
     * Resolves the target environment in priority order:
     * 1. JVM system property (-Denv=qa)
     * 2. OS environment variable (ENV=qa)
     * 3. Default: "qa"
     */
    private String resolveEnvironment() {
        String env = System.getProperty("env");
        if (env != null && !env.isBlank()) {
            log.debug("Environment from system property: {}", env);
            return env.trim().toLowerCase();
        }

        env = System.getenv("ENV");
        if (env != null && !env.isBlank()) {
            log.debug("Environment from OS env variable: {}", env);
            return env.trim().toLowerCase();
        }

        log.warn("No 'env' property found. Defaulting to 'qa'");
        return "qa";
    }

    // ─── YAML Loader ──────────────────────────────────────────────────────
    /**
     * Loads the YAML config file from the classpath.
     * File path pattern: config/{env}.yml
     */
    private EnvironmentConfig loadYaml(String env) {
        String filePath = "config/" + env + ".yml";

        try (InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(filePath)) {

            if (inputStream == null) {
                throw new RuntimeException(
                    "❌ Config file not found on classpath: [" + filePath + "]. " +
                    "Ensure the file exists under src/test/resources/config/"
                );
            }

            Yaml yaml = new Yaml(new Constructor(EnvironmentConfig.class,
                    new org.yaml.snakeyaml.LoaderOptions()));
            return yaml.load(inputStream);

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to load config file: " + filePath, e);
        }
    }

    // ─── Convenience Accessors ─────────────────────────────────────────────
    /** Returns the full base URL including path: baseUri + basePath */
    public String getFullBaseUrl() {
        return config.getBaseUri() + config.getBasePath();
    }

    /** Returns the Bearer token from config */
    public String getBearerToken() {
        return config.getAuth().getToken();
    }

    /** Returns the response time threshold (ms) for performance assertions */
    public long getResponseTimeThreshold() {
        return config.getTimeouts().getResponseTimeThreshold();
    }
}
