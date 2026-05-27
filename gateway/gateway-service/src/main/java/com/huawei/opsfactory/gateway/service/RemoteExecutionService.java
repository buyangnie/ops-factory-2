/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.config.GatewayProperties;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes remote commands on hosts via SSH with command-prefix resolution, variable substitution, and whitelist
 * validation.
 *
 * @author x00000000
 * @since 2026-05-09
 */
@Service
/**
 * Remote Execution Service.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class RemoteExecutionService {
    private static final Logger log = LoggerFactory.getLogger(RemoteExecutionService.class);

    private final HostService hostService;

    private final CommandWhitelistService commandWhitelistService;

    private final GatewayProperties properties;

    private final ClusterService clusterService;

    private final ClusterTypeService clusterTypeService;

    /**
     * Creates the remote execution service instance.
     *
     * @param hostService service for resolving host credentials
     * @param commandWhitelistService service for validating commands against the whitelist
     * @param properties gateway configuration properties
     * @param clusterService service for resolving cluster data
     * @param clusterTypeService service for resolving cluster type command prefixes
     */
    public RemoteExecutionService(HostService hostService, CommandWhitelistService commandWhitelistService,
        GatewayProperties properties, ClusterService clusterService, ClusterTypeService clusterTypeService) {
        this.hostService = hostService;
        this.commandWhitelistService = commandWhitelistService;
        this.properties = properties;
        this.clusterService = clusterService;
        this.clusterTypeService = clusterTypeService;
    }

    /**
     * Execute a remote command on the specified host via SSH.
     *
     * @param hostId the host ID to connect to
     * @param command the shell command to execute
     * @param timeoutSeconds maximum execution time in seconds
     * @return result map with hostIp, username, hostName, exitCode, output, error, duration
     */
    public Map<String, Object> execute(String hostId, String command, int timeoutSeconds) {
        // Step 1: Get host with decrypted credential
        Map<String, Object> host = resolveHost(hostId);
        if (host == null) {
            return buildResult(new ExecutionContext(hostId, "", "", ""), -1, "", "Host not found: " + hostId, 0L);
        }

        String hostName = (String) host.getOrDefault("name", "");
        String hostname = (String) host.get("ip");
        int port = host.get("port") instanceof Number n ? n.intValue() : 22;
        String username = (String) host.get("username");
        String authType = (String) host.get("authType");
        String credential = (String) host.get("credential");
        ExecutionContext ctx = new ExecutionContext(hostId, hostname, username, hostName);

        // Step 2: Resolve command prefix and environment variables from cluster type
        EnvResolution envResolution = resolveClusterEnv(hostId, host);
        String commandPrefix = envResolution.commandPrefix;
        Map<String, String> envVars = envResolution.envVars;

        // Step 3: Build effective command (substitution, validation, prefix)
        CommandResolution cmdResolution = buildEffectiveCommand(ctx, command, commandPrefix, envVars);
        if (cmdResolution.result != null) {
            return cmdResolution.result;
        }
        String effectiveCommand = cmdResolution.effectiveCommand;

        // Step 4: Execute via SSH
        Session session = null;
        ChannelExec channel = null;
        long startTime = System.currentTimeMillis();

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, hostname, port);

            if ("key".equals(authType)) {
                jsch.addIdentity("remote-exec", credential.getBytes(StandardCharsets.UTF_8), null, null);
            } else {
                session.setPassword(credential);
            }

            // WARNING: Strict host key checking is disabled for remote execution in
            // development/testing environments. For production, configure a known_hosts file.
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("bash -l -c " + singleQuote(effectiveCommand));

            try (InputStream in = channel.getInputStream(); InputStream err = channel.getExtInputStream()) {

                ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
                ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();

                channel.connect();

                readStreamsUntilDone(channel, in, err, outputBuffer, errorBuffer, timeoutSeconds, hostId);

                int exitCode = channel.getExitStatus();
                long duration = System.currentTimeMillis() - startTime;
                String output = outputBuffer.toString(StandardCharsets.UTF_8);
                String errorOutput = errorBuffer.toString(StandardCharsets.UTF_8);

                Map<String, Object> result = buildResult(ctx, exitCode, output, errorOutput, duration);
                result.put("command", command);
                result.put("effectiveCommand", effectiveCommand);
                return result;
            }
        } catch (JSchException | IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SSH execution failed for host {}: {}", hostId, e.getMessage());
            return buildResult(ctx, -1, "", "SSH execution failed: " + e.getMessage(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            log.warn("SSH execution interrupted for host {}", hostId);
            return buildResult(ctx, -1, "", "SSH execution interrupted", duration);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * Resolves host information by ID. Returns the host map, or null if the host cannot be found.
     */
    private Map<String, Object> resolveHost(String hostId) {
        try {
            return hostService.getHostWithCredential(hostId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolves command prefix and environment variables from the cluster type associated with the host's cluster.
     */
    private EnvResolution resolveClusterEnv(String hostId, Map<String, Object> host) {
        String commandPrefix = "";
        Map<String, String> envVars = new LinkedHashMap<>();
        Object clusterIdObj = host.get("clusterId");
        if (clusterIdObj == null) {
            return new EnvResolution(commandPrefix, envVars);
        }
        try {
            Map<String, Object> cluster = clusterService.getCluster(clusterIdObj.toString());
            String typeName = cluster != null ? (String) cluster.get("type") : null;
            if (typeName == null) {
                return new EnvResolution(commandPrefix, envVars);
            }
            List<Map<String, Object>> allTypes = clusterTypeService.listClusterTypes();
            for (Map<String, Object> ct : allTypes) {
                if (typeName.equals(ct.get("name"))) {
                    Object prefix = ct.get("commandPrefix");
                    if (prefix != null && !prefix.toString().isBlank()) {
                        commandPrefix = prefix.toString().trim();
                    }
                    Object vars = ct.get("envVariables");
                    if (vars instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                String k = m.get("key") != null ? m.get("key").toString() : null;
                                String v = m.get("value") != null ? m.get("value").toString() : "";
                                if (k != null && !k.isEmpty()) {
                                    envVars.put(k, v);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("Could not resolve cluster type for host {}: {}", hostId, e.getMessage());
        }
        return new EnvResolution(commandPrefix, envVars);
    }

    /**
     * Builds the effective command by applying variable substitution, whitelist validation, and command prefix.
     * Returns a CommandResolution where result is non-null if validation failed (caller should return immediately),
     * or effectiveCommand is set when the command is ready to execute.
     */
    private CommandResolution buildEffectiveCommand(ExecutionContext ctx, String command, String commandPrefix,
        Map<String, String> envVars) {
        // Replace ${VAR} and $VAR placeholders (sorted longest key first to avoid partial matches)
        String effectiveCommand = command;
        List<String> sortedKeys = new ArrayList<>(envVars.keySet());
        sortedKeys.sort((a, b) -> b.length() - a.length());
        for (String key : sortedKeys) {
            String value = envVars.get(key);
            effectiveCommand = effectiveCommand.replace("${" + key + "}", value);
            effectiveCommand =
                effectiveCommand.replaceAll("\\$" + java.util.regex.Pattern.quote(key) + "(?![A-Za-z0-9_])",
                    java.util.regex.Matcher.quoteReplacement(value));
        }

        // Validate resolved command against whitelist
        List<String> rejected = commandWhitelistService.validateCommand(effectiveCommand);
        if (!rejected.isEmpty()) {
            Map<String,
                Object> result = buildResult(ctx, -1, "",
                    "Command rejected: the following commands are not in the whitelist: " + String.join(", ", rejected),
                    0L);
            result.put("rejectedCommands", rejected);
            return new CommandResolution(null, result);
        }

        // Apply command prefix
        // Wrap in bash -c so the prefix applies to the entire command chain (&&, ||, ;)
        if (!commandPrefix.isEmpty()) {
            effectiveCommand = commandPrefix + " bash -c " + singleQuote(effectiveCommand);
        }

        return new CommandResolution(effectiveCommand, null);
    }

    /**
     * Reads stdout and stderr streams from an SSH channel until the channel closes or the timeout expires.
     */
    private void readStreamsUntilDone(ChannelExec channel, InputStream in, InputStream err,
        ByteArrayOutputStream outputBuffer, ByteArrayOutputStream errorBuffer, int timeoutSeconds, String hostId)
        throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;
        byte[] buf = new byte[4096];

        while (true) {
            if (channel.isClosed()) {
                drainStream(in, outputBuffer, buf);
                drainStream(err, errorBuffer, buf);
                break;
            }

            drainStream(in, outputBuffer, buf);
            drainStream(err, errorBuffer, buf);

            if (System.currentTimeMillis() > deadline) {
                log.warn("Command execution timed out after {} seconds for host {}", timeoutSeconds, hostId);
                channel.disconnect();
                break;
            }

            Thread.sleep(50);
        }
    }

    /**
     * Drains all available bytes from an InputStream into a ByteArrayOutputStream.
     */
    private void drainStream(InputStream stream, ByteArrayOutputStream buffer, byte[] buf) throws IOException {
        while (stream.available() > 0) {
            int len = stream.read(buf);
            if (len > 0) {
                buffer.write(buf, 0, len);
            }
        }
    }

    /**
     * Builds a standard result map for remote execution responses.
     */
    private Map<String, Object> buildResult(ExecutionContext ctx, int exitCode, String output, String error,
        long duration) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hostId", ctx.hostId);
        result.put("hostIp", ctx.hostname);
        result.put("username", ctx.username);
        result.put("hostName", ctx.hostName);
        result.put("exitCode", exitCode);
        result.put("output", output);
        result.put("error", error);
        result.put("duration", duration);
        return result;
    }

    /**
     * Holds resolved command prefix and environment variables from cluster type lookup.
     */
    private static class EnvResolution {
        final String commandPrefix;

        final Map<String, String> envVars;

        EnvResolution(String commandPrefix, Map<String, String> envVars) {
            this.commandPrefix = commandPrefix;
            this.envVars = envVars;
        }
    }

    /**
     * Holds the result of effective-command building. Exactly one of effectiveCommand or result is non-null:
     * effectiveCommand is set on success; result is set when validation fails (caller should return immediately).
     *
     * @author x00000000
     * @since 2026-05-27
     */
    private static class CommandResolution {
        final String effectiveCommand;

        final Map<String, Object> result;

        CommandResolution(String effectiveCommand, Map<String, Object> result) {
            this.effectiveCommand = effectiveCommand;
            this.result = result;
        }
    }

    /**
     * Encapsulates host identity fields shared across remote execution result building.
     *
     * @author x00000000
     * @since 2026-05-27
     */
    private static final class ExecutionContext {
        final String hostId;

        final String hostname;

        final String username;

        final String hostName;

        ExecutionContext(String hostId, String hostname, String username, String hostName) {
            this.hostId = hostId;
            this.hostname = hostname;
            this.username = username;
            this.hostName = hostName;
        }
    }

    /**
     * Wrap a string in single quotes, escaping any embedded single quotes
     * using the standard POSIX technique: replace ' with '\''.
     */
    private String singleQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
