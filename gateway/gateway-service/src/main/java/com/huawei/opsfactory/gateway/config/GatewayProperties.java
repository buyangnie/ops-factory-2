/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Central configuration properties bound to the {@code gateway} prefix in application config.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private static final Logger log = LoggerFactory.getLogger(GatewayProperties.class);

    private static final String CONFIG_PATH_KEY = "GATEWAY_CONFIG_PATH";

    private String secretKey = "test";

    private String corsOrigin = "http://127.0.0.1:5173";

    private String goosedBin = "goosed";

    private boolean gooseTls = true;

    private Paths paths = new Paths();

    private Idle idle = new Idle();

    private Upload upload = new Upload();

    private Limits limits = new Limits();

    private Prewarm prewarm = new Prewarm();

    private Sse sse = new Sse();

    private Langfuse langfuse = new Langfuse();

    private OfficePreview officePreview = new OfficePreview();

    private Logging logging = new Logging();

    private String credentialEncryptionKey = "changeit-changeit-changeit-32";

    private RemoteExecution remoteExecution = new RemoteExecution();

    private FileCapsules fileCapsules = new FileCapsules();

    private FileBrowser files = new FileBrowser();

    private SkillMarket skillMarket = new SkillMarket();

    private Knowledge knowledge = new Knowledge();

    private List<String> adminUsers = List.of("admin");

    // ---- Getters / Setters ----

    /**
     * Returns the secret key used for gateway authentication.
     *
     * @return the result
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Sets the secret key used for gateway authentication.
     *
     * @param secretKey the secretKey parameter
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Returns the allowed CORS origin pattern.
     *
     * @return the result
     */
    public String getCorsOrigin() {
        return corsOrigin;
    }

    /**
     * Sets the allowed CORS origin pattern.
     *
     * @param corsOrigin the corsOrigin parameter
     */
    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = corsOrigin;
    }

    /**
     * Returns the path to the goosed binary.
     *
     * @return the result
     */
    public String getGoosedBin() {
        return goosedBin;
    }

    /**
     * Sets the path to the goosed binary.
     *
     * @param goosedBin the goosedBin parameter
     */
    public void setGoosedBin(String goosedBin) {
        this.goosedBin = goosedBin;
    }

    /**
     * Returns whether TLS is enabled for goosed communication.
     *
     * @return the result
     */
    public boolean isGooseTls() {
        return gooseTls;
    }

    /**
     * Sets whether TLS is enabled for goosed communication.
     *
     * @param gooseTls the gooseTls parameter
     */
    public void setGooseTls(boolean gooseTls) {
        this.gooseTls = gooseTls;
    }

    /**
     * Returns the URL scheme (http or https) based on the TLS setting.
     *
     * @return the result
     */
    public String gooseScheme() {
        return gooseTls ? "https" : "http";
    }

    /**
     * Returns the path configuration.
     *
     * @return the result
     */
    public Paths getPaths() {
        return paths;
    }

    /**
     * Sets the path configuration.
     *
     * @param paths the paths parameter
     */
    public void setPaths(Paths paths) {
        this.paths = paths;
    }

    /**
     * Returns the idle timeout configuration.
     *
     * @return the result
     */
    public Idle getIdle() {
        return idle;
    }

    /**
     * Sets the idle timeout configuration.
     *
     * @param idle the idle parameter
     */
    public void setIdle(Idle idle) {
        this.idle = idle;
    }

    /**
     * Returns the upload size limit configuration.
     *
     * @return the result
     */
    public Upload getUpload() {
        return upload;
    }

    /**
     * Sets the upload size limit configuration.
     *
     * @param upload the upload parameter
     */
    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    /**
     * Returns the Langfuse observability configuration.
     *
     * @return the result
     */
    public Langfuse getLangfuse() {
        return langfuse;
    }

    /**
     * Sets the Langfuse observability configuration.
     *
     * @param langfuse the langfuse parameter
     */
    public void setLangfuse(Langfuse langfuse) {
        this.langfuse = langfuse;
    }

    /**
     * Returns the instance limit configuration.
     *
     * @return the result
     */
    public Limits getLimits() {
        return limits;
    }

    /**
     * Sets the instance limit configuration.
     *
     * @param limits the limits parameter
     */
    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    /**
     * Returns the prewarm configuration.
     *
     * @return the result
     */
    public Prewarm getPrewarm() {
        return prewarm;
    }

    /**
     * Sets the prewarm configuration.
     *
     * @param prewarm the prewarm parameter
     */
    public void setPrewarm(Prewarm prewarm) {
        this.prewarm = prewarm;
    }

    /**
     * Returns the SSE timeout configuration.
     *
     * @return the result
     */
    public Sse getSse() {
        return sse;
    }

    /**
     * Sets the SSE timeout configuration.
     *
     * @param sse the sse parameter
     */
    public void setSse(Sse sse) {
        this.sse = sse;
    }

    /**
     * Returns the Office preview configuration.
     *
     * @return the result
     */
    public OfficePreview getOfficePreview() {
        return officePreview;
    }

    /**
     * Sets the Office preview configuration.
     *
     * @param officePreview the officePreview parameter
     */
    public void setOfficePreview(OfficePreview officePreview) {
        this.officePreview = officePreview;
    }

    /**
     * Returns the logging configuration.
     *
     * @return the result
     */
    public Logging getLogging() {
        return logging;
    }

    /**
     * Sets the logging configuration.
     *
     * @param logging the logging parameter
     */
    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    /**
     * Returns the credential encryption key.
     *
     * @return the result
     */
    public String getCredentialEncryptionKey() {
        return credentialEncryptionKey;
    }

    /**
     * Sets the credential encryption key.
     *
     * @param credentialEncryptionKey the credentialEncryptionKey parameter
     */
    public void setCredentialEncryptionKey(String credentialEncryptionKey) {
        this.credentialEncryptionKey = credentialEncryptionKey;
    }

    /**
     * Returns the remote execution configuration.
     *
     * @return the result
     */
    public RemoteExecution getRemoteExecution() {
        return remoteExecution;
    }

    /**
     * Sets the remote execution configuration.
     *
     * @param remoteExecution the remoteExecution parameter
     */
    public void setRemoteExecution(RemoteExecution remoteExecution) {
        this.remoteExecution = remoteExecution;
    }

    /**
     * Returns the file capsules configuration.
     *
     * @return the result
     */
    public FileCapsules getFileCapsules() {
        return fileCapsules;
    }

    /**
     * Sets the file capsules configuration.
     *
     * @param fileCapsules the fileCapsules parameter
     */
    public void setFileCapsules(FileCapsules fileCapsules) {
        this.fileCapsules = fileCapsules;
    }

    /**
     * Returns the file browser configuration.
     *
     * @return the result
     */
    public FileBrowser getFiles() {
        return files;
    }

    /**
     * Sets the file browser configuration.
     *
     * @param files the files parameter
     */
    public void setFiles(FileBrowser files) {
        this.files = files;
    }

    /**
     * Returns the skill market configuration.
     *
     * @return the result
     */
    public SkillMarket getSkillMarket() {
        return skillMarket;
    }

    /**
     * Sets the skill market configuration.
     *
     * @param skillMarket the skillMarket parameter
     */
    public void setSkillMarket(SkillMarket skillMarket) {
        this.skillMarket = skillMarket;
    }

    /**
     * Returns the knowledge feature configuration.
     *
     * @return the result
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge feature configuration.
     *
     * @param knowledge the knowledge parameter
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Returns the list of admin user IDs.
     *
     * @return the result
     */
    public List<String> getAdminUsers() {
        return adminUsers;
    }

    /**
     * Sets the list of admin user IDs.
     *
     * @param adminUsers the adminUsers parameter
     */
    public void setAdminUsers(List<String> adminUsers) {
        this.adminUsers = adminUsers;
    }

    /**
     * Resolves the absolute path to the gateway configuration file.
     *
     * @return the result
     */
    public Path getConfigPath() {
        String configuredPath = configuredConfigPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("config.yaml").toAbsolutePath().normalize();
        }

        Path configPath = Path.of(configuredPath);
        if (configPath.isAbsolute()) {
            return configPath.normalize();
        }
        return Path.of("").toAbsolutePath().resolve(configPath).normalize();
    }

    /**
     * Returns the directory containing the gateway configuration file.
     *
     * @return the result
     */
    public Path getConfigDirectory() {
        Path configPath = getConfigPath();
        Path parent = configPath.getParent();
        if (parent != null) {
            return parent;
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /**
     * Resolves the absolute path to the project root directory.
     *
     * @return the result
     */
    public Path getProjectRootPath() {
        Path configuredRoot = Path.of(paths.getProjectRoot());
        if (configuredRoot.isAbsolute()) {
            return configuredRoot.normalize();
        }
        if (configuredConfigPath() != null) {
            return getConfigDirectory().resolve(configuredRoot).normalize();
        }
        return configuredRoot.toAbsolutePath().normalize();
    }

    /**
     * Resolves the absolute path to the gateway root directory.
     *
     * @return the result
     */
    public Path getGatewayRootPath() {
        if (configuredConfigPath() == null) {
            return getProjectRootPath().resolve("gateway").normalize();
        }
        Path configPath = getConfigPath();
        Path configDir = getConfigDirectory();
        if ("config.yaml".equals(configPath.getFileName() != null ? configPath.getFileName().toString() : "")) {
            return configDir;
        }
        return getProjectRootPath().resolve("gateway").normalize();
    }

    private String configuredConfigPath() {
        String configuredPath = System.getProperty(CONFIG_PATH_KEY);
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv(CONFIG_PATH_KEY);
        }
        return (configuredPath == null || configuredPath.isBlank()) ? null : configuredPath;
    }

    // ---- Nested config classes ----

    public static class Paths {
        private String projectRoot = "..";

        private String agentsDir = "agents";

        private String usersDir = "users";

        /**
         * Gets the project root.
         *
         * @return the result
         */
        public String getProjectRoot() {
            return projectRoot;
        }

        /**
         * Sets the project root.
         *
         * @param projectRoot the projectRoot parameter
         */
        public void setProjectRoot(String projectRoot) {
            this.projectRoot = projectRoot;
        }

        /**
         * Gets the agents dir.
         *
         * @return the result
         */
        public String getAgentsDir() {
            return agentsDir;
        }

        /**
         * Sets the agents dir.
         *
         * @param agentsDir the agentsDir parameter
         */
        public void setAgentsDir(String agentsDir) {
            this.agentsDir = agentsDir;
        }

        /**
         * Gets the users dir.
         *
         * @return the result
         */
        public String getUsersDir() {
            return usersDir;
        }

        /**
         * Sets the users dir.
         *
         * @param usersDir the usersDir parameter
         */
        public void setUsersDir(String usersDir) {
            this.usersDir = usersDir;
        }
    }

    public static class Idle {
        private int timeoutMinutes = 15;

        private long checkIntervalMs = 60000L;

        private int maxRestartAttempts = 3;

        private long restartBaseDelayMs = 5000L;

        /**
         * Gets the timeout minutes.
         *
         * @return the result
         */
        public int getTimeoutMinutes() {
            return timeoutMinutes;
        }

        /**
         * Sets the timeout minutes.
         *
         * @param timeoutMinutes the timeoutMinutes parameter
         */
        public void setTimeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
        }

        /**
         * Gets the check interval ms.
         *
         * @return the result
         */
        public long getCheckIntervalMs() {
            return checkIntervalMs;
        }

        /**
         * Sets the check interval ms.
         *
         * @param checkIntervalMs the checkIntervalMs parameter
         */
        public void setCheckIntervalMs(long checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
        }

        /**
         * Gets the max restart attempts.
         *
         * @return the result
         */
        public int getMaxRestartAttempts() {
            return maxRestartAttempts;
        }

        /**
         * Sets the max restart attempts.
         *
         * @param maxRestartAttempts the maxRestartAttempts parameter
         */
        public void setMaxRestartAttempts(int maxRestartAttempts) {
            this.maxRestartAttempts = maxRestartAttempts;
        }

        /**
         * Gets the restart base delay ms.
         *
         * @return the result
         */
        public long getRestartBaseDelayMs() {
            return restartBaseDelayMs;
        }

        /**
         * Sets the restart base delay ms.
         *
         * @param restartBaseDelayMs the restartBaseDelayMs parameter
         */
        public void setRestartBaseDelayMs(long restartBaseDelayMs) {
            this.restartBaseDelayMs = restartBaseDelayMs;
        }
    }

    public static class Upload {
        private int maxFileSizeMb = 50;

        private int maxImageSizeMb = 20;

        /**
         * Gets the max file size mb.
         *
         * @return the result
         */
        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        /**
         * Sets the max file size mb.
         *
         * @param maxFileSizeMb the maxFileSizeMb parameter
         */
        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        /**
         * Gets the max image size mb.
         *
         * @return the result
         */
        public int getMaxImageSizeMb() {
            return maxImageSizeMb;
        }

        /**
         * Sets the max image size mb.
         *
         * @param maxImageSizeMb the maxImageSizeMb parameter
         */
        public void setMaxImageSizeMb(int maxImageSizeMb) {
            this.maxImageSizeMb = maxImageSizeMb;
        }
    }

    public static class Langfuse {
        private String host = "";

        private String publicKey = "";

        private String secretKey = "";

        /**
         * Gets the host.
         *
         * @return the result
         */
        public String getHost() {
            return host;
        }

        /**
         * Sets the host.
         *
         * @param host the host parameter
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * Gets the public key.
         *
         * @return the result
         */
        public String getPublicKey() {
            return publicKey;
        }

        /**
         * Sets the public key.
         *
         * @param publicKey the publicKey parameter
         */
        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        /**
         * Gets the secret key.
         *
         * @return the result
         */
        public String getSecretKey() {
            return secretKey;
        }

        /**
         * Sets the secret key.
         *
         * @param secretKey the secretKey parameter
         */
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Limits {
        private int maxInstancesPerUser = 5;

        private int maxInstancesGlobal = 50;

        /**
         * Gets the max instances per user.
         *
         * @return the result
         */
        public int getMaxInstancesPerUser() {
            return maxInstancesPerUser;
        }

        /**
         * Sets the max instances per user.
         *
         * @param maxInstancesPerUser the maxInstancesPerUser parameter
         */
        public void setMaxInstancesPerUser(int maxInstancesPerUser) {
            this.maxInstancesPerUser = maxInstancesPerUser;
        }

        /**
         * Gets the max instances global.
         *
         * @return the result
         */
        public int getMaxInstancesGlobal() {
            return maxInstancesGlobal;
        }

        /**
         * Sets the max instances global.
         *
         * @param maxInstancesGlobal the maxInstancesGlobal parameter
         */
        public void setMaxInstancesGlobal(int maxInstancesGlobal) {
            this.maxInstancesGlobal = maxInstancesGlobal;
        }
    }

    public static class Sse {
        private int firstByteTimeoutSec = 120;

        private int idleTimeoutSec = 600;

        private int maxDurationSec = 1200;

        /**
         * Gets the first byte timeout sec.
         *
         * @return the result
         */
        public int getFirstByteTimeoutSec() {
            return firstByteTimeoutSec;
        }

        /**
         * Sets the first byte timeout sec.
         *
         * @param firstByteTimeoutSec the firstByteTimeoutSec parameter
         */
        public void setFirstByteTimeoutSec(int firstByteTimeoutSec) {
            this.firstByteTimeoutSec = firstByteTimeoutSec;
        }

        /**
         * Gets the idle timeout sec.
         *
         * @return the result
         */
        public int getIdleTimeoutSec() {
            return idleTimeoutSec;
        }

        /**
         * Sets the idle timeout sec.
         *
         * @param idleTimeoutSec the idleTimeoutSec parameter
         */
        public void setIdleTimeoutSec(int idleTimeoutSec) {
            this.idleTimeoutSec = idleTimeoutSec;
        }

        /**
         * Gets the max duration sec.
         *
         * @return the result
         */
        public int getMaxDurationSec() {
            return maxDurationSec;
        }

        /**
         * Sets the max duration sec.
         *
         * @param maxDurationSec the maxDurationSec parameter
         */
        public void setMaxDurationSec(int maxDurationSec) {
            this.maxDurationSec = maxDurationSec;
        }
    }

    public static class Prewarm {
        private boolean enabled = true;

        private String defaultAgentId = "universal-agent";

        /**
         * Returns the enabled flag.
         *
         * @return the result
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Updates the enabled flag.
         *
         * @param enabled the enabled parameter
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the default agent id.
         *
         * @return the result
         */
        public String getDefaultAgentId() {
            return defaultAgentId;
        }

        /**
         * Sets the default agent id.
         *
         * @param defaultAgentId the defaultAgentId parameter
         */
        public void setDefaultAgentId(String defaultAgentId) {
            this.defaultAgentId = defaultAgentId;
        }
    }

    public static class OfficePreview {
        private boolean enabled = false;

        private String onlyofficeUrl = "";

        private String fileBaseUrl = "";

        /**
         * Returns the enabled flag.
         *
         * @return the result
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Updates the enabled flag.
         *
         * @param enabled the enabled parameter
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the onlyoffice url.
         *
         * @return the result
         */
        public String getOnlyofficeUrl() {
            return onlyofficeUrl;
        }

        /**
         * Sets the onlyoffice url.
         *
         * @param onlyofficeUrl the onlyofficeUrl parameter
         */
        public void setOnlyofficeUrl(String onlyofficeUrl) {
            this.onlyofficeUrl = onlyofficeUrl;
        }

        /**
         * Gets the file base url.
         *
         * @return the result
         */
        public String getFileBaseUrl() {
            return fileBaseUrl;
        }

        /**
         * Sets the file base url.
         *
         * @param fileBaseUrl the fileBaseUrl parameter
         */
        public void setFileBaseUrl(String fileBaseUrl) {
            this.fileBaseUrl = fileBaseUrl;
        }
    }

    public static class Logging {
        private boolean accessLogEnabled = true;

        private boolean includeUpstreamErrorBody = false;

        private boolean includeSseChunkPreview = false;

        private int sseChunkPreviewMaxChars = 160;

        /**
         * Returns the access log enabled flag.
         *
         * @return the result
         */
        public boolean isAccessLogEnabled() {
            return accessLogEnabled;
        }

        /**
         * Updates the access log enabled flag.
         *
         * @param accessLogEnabled the accessLogEnabled parameter
         */
        public void setAccessLogEnabled(boolean accessLogEnabled) {
            this.accessLogEnabled = accessLogEnabled;
        }

        /**
         * Returns the include upstream error body flag.
         *
         * @return the result
         */
        public boolean isIncludeUpstreamErrorBody() {
            return includeUpstreamErrorBody;
        }

        /**
         * Updates the include upstream error body flag.
         *
         * @param includeUpstreamErrorBody the includeUpstreamErrorBody parameter
         */
        public void setIncludeUpstreamErrorBody(boolean includeUpstreamErrorBody) {
            this.includeUpstreamErrorBody = includeUpstreamErrorBody;
        }

        /**
         * Returns the include sse chunk preview flag.
         *
         * @return the result
         */
        public boolean isIncludeSseChunkPreview() {
            return includeSseChunkPreview;
        }

        /**
         * Updates the include sse chunk preview flag.
         *
         * @param includeSseChunkPreview the includeSseChunkPreview parameter
         */
        public void setIncludeSseChunkPreview(boolean includeSseChunkPreview) {
            this.includeSseChunkPreview = includeSseChunkPreview;
        }

        /**
         * Gets the sse chunk preview max chars.
         *
         * @return the result
         */
        public int getSseChunkPreviewMaxChars() {
            return sseChunkPreviewMaxChars;
        }

        /**
         * Sets the sse chunk preview max chars.
         *
         * @param sseChunkPreviewMaxChars the sseChunkPreviewMaxChars parameter
         */
        public void setSseChunkPreviewMaxChars(int sseChunkPreviewMaxChars) {
            this.sseChunkPreviewMaxChars = sseChunkPreviewMaxChars;
        }
    }

    public static class RemoteExecution {
        private int defaultTimeout = 30;

        private int maxTimeout = 120;

        /**
         * Gets the default timeout.
         *
         * @return the result
         */
        public int getDefaultTimeout() {
            return defaultTimeout;
        }

        /**
         * Sets the default timeout.
         *
         * @param defaultTimeout the defaultTimeout parameter
         */
        public void setDefaultTimeout(int defaultTimeout) {
            this.defaultTimeout = defaultTimeout;
        }

        /**
         * Gets the max timeout.
         *
         * @return the result
         */
        public int getMaxTimeout() {
            return maxTimeout;
        }

        /**
         * Sets the max timeout.
         *
         * @param maxTimeout the maxTimeout parameter
         */
        public void setMaxTimeout(int maxTimeout) {
            this.maxTimeout = maxTimeout;
        }
    }

    public static class FileCapsules {
        private List<String> allowedExtensions = List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "csv",
            "txt", "json", "md", "markdown", "html", "htm");

        /**
         * Gets the allowed extensions.
         *
         * @return the result
         */
        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        /**
         * Sets the allowed extensions.
         *
         * @param allowedExtensions the allowedExtensions parameter
         */
        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class FileBrowser {
        private List<FileScanRoot> scanRoots = List.of(new FileScanRoot("workingDir", "${userAgentDir}", false),
            new FileScanRoot("output", "${userAgentDir}/output", false));

        /**
         * Gets the scan roots.
         *
         * @return the result
         */
        public List<FileScanRoot> getScanRoots() {
            return scanRoots;
        }

        /**
         * Sets the scan roots.
         *
         * @param scanRoots the scanRoots parameter
         */
        public void setScanRoots(List<FileScanRoot> scanRoots) {
            this.scanRoots = scanRoots;
        }
    }

    public static class FileScanRoot {
        private String id = "";

        private String path = "";

        private boolean recursive = false;

        private List<String> excludeDirs = List.of();

        private int maxDepth = 6;

        private int maxFiles = 1000;

        private long scanTimeoutMs = 2000;

        public FileScanRoot() {
        }

        public FileScanRoot(String id, String path, boolean recursive) {
            this.id = id;
            this.path = path;
            this.recursive = recursive;
        }

        /**
         * Gets the id.
         *
         * @return the result
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the id parameter
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Gets the path.
         *
         * @return the result
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the path.
         *
         * @param path the path parameter
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns the recursive flag.
         *
         * @return the result
         */
        public boolean isRecursive() {
            return recursive;
        }

        /**
         * Updates the recursive flag.
         *
         * @param recursive the recursive parameter
         */
        public void setRecursive(boolean recursive) {
            this.recursive = recursive;
        }

        /**
         * Gets the exclude dirs.
         *
         * @return the result
         */
        public List<String> getExcludeDirs() {
            return excludeDirs;
        }

        /**
         * Sets the exclude dirs.
         *
         * @param excludeDirs the excludeDirs parameter
         */
        public void setExcludeDirs(List<String> excludeDirs) {
            this.excludeDirs = excludeDirs;
        }

        /**
         * Gets the max depth.
         *
         * @return the result
         */
        public int getMaxDepth() {
            return maxDepth;
        }

        /**
         * Sets the max depth.
         *
         * @param maxDepth the maxDepth parameter
         */
        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        /**
         * Gets the max files.
         *
         * @return the result
         */
        public int getMaxFiles() {
            return maxFiles;
        }

        /**
         * Sets the max files.
         *
         * @param maxFiles the maxFiles parameter
         */
        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        /**
         * Gets the scan timeout ms.
         *
         * @return the result
         */
        public long getScanTimeoutMs() {
            return scanTimeoutMs;
        }

        /**
         * Sets the scan timeout ms.
         *
         * @param scanTimeoutMs the scanTimeoutMs parameter
         */
        public void setScanTimeoutMs(long scanTimeoutMs) {
            this.scanTimeoutMs = scanTimeoutMs;
        }
    }

    public static class SkillMarket {
        private String baseUrl = "http://127.0.0.1:8095";

        private int requestTimeoutMs = 10000;

        private int maxPackageSizeMb = 200;

        /**
         * Gets the base url.
         *
         * @return the result
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the base url.
         *
         * @param baseUrl the baseUrl parameter
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Gets the request timeout ms.
         *
         * @return the result
         */
        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        /**
         * Sets the request timeout ms.
         *
         * @param requestTimeoutMs the requestTimeoutMs parameter
         */
        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        /**
         * Gets the max package size mb.
         *
         * @return the result
         */
        public int getMaxPackageSizeMb() {
            return maxPackageSizeMb;
        }

        /**
         * Sets the max package size mb.
         *
         * @param maxPackageSizeMb the maxPackageSizeMb parameter
         */
        public void setMaxPackageSizeMb(int maxPackageSizeMb) {
            this.maxPackageSizeMb = maxPackageSizeMb;
        }
    }

    public static class Knowledge {
        private String artifactsRoot = "../knowledge-service/data/artifacts";

        /**
         * Gets the artifacts root.
         *
         * @return the result
         */
        public String getArtifactsRoot() {
            return artifactsRoot;
        }

        /**
         * Sets the artifacts root.
         *
         * @param artifactsRoot the artifactsRoot parameter
         */
        public void setArtifactsRoot(String artifactsRoot) {
            this.artifactsRoot = artifactsRoot;
        }
    }

    @Override
    public String toString() {
        return "GatewayProperties{" + "secretKey='***'" + ", corsOrigin='" + corsOrigin + '\'' + ", gooseTls="
            + gooseTls + ", gooseScheme='" + gooseScheme() + '\'' + ", goosedBin='" + goosedBin + '\'' + '}';
    }

    private void normalizeGoosedBin() {
        if (goosedBin == null || goosedBin.isBlank()) {
            return;
        }
        Path rawPath = Path.of(goosedBin);
        if (rawPath.isAbsolute()) {
            return;
        }
        Path candidate = getGatewayRootPath().resolve(goosedBin).normalize();
        if (Files.exists(candidate)) {
            goosedBin = candidate.toString();
        }
    }
}
