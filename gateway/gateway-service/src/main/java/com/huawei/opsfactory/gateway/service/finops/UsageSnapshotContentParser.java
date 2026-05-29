/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.service.finops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses goosed message content into normalized FinOps message fields.
 *
 * @since 2026-05-28
 */
final class UsageSnapshotContentParser {

    private static final int MESSAGE_TEXT_LIMIT = 12_000;
    private static final int MESSAGE_PREVIEW_LIMIT = 280;
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("\\btool(?:Request|Response|_use|_result)\\s+([A-Za-z0-9_.:-]+)");

    private final ObjectMapper objectMapper;

    UsageSnapshotContentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String extractFirstText(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            return findText(root);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    MessageContentSummary summarizeContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return new MessageContentSummary(0, "", "", false, false, false, null, false);
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            StringBuilder text = new StringBuilder();
            ContentSignals signals = new ContentSignals();
            collectContent(root, text, signals);
            String normalized = normalizeWhitespace(text.isEmpty() ? contentJson : text.toString());
            detectTextualToolSignals(normalized, signals);
            int length = normalized.length();
            boolean truncated = length > MESSAGE_TEXT_LIMIT;
            String contentText = truncated ? normalized.substring(0, MESSAGE_TEXT_LIMIT) : normalized;
            return new MessageContentSummary(
                length,
                truncate(normalized, MESSAGE_PREVIEW_LIMIT),
                contentText,
                truncated,
                signals.toolRequest,
                signals.toolResponse,
                signals.toolName,
                signals.error
            );
        } catch (JsonProcessingException ex) {
            String normalized = normalizeWhitespace(contentJson);
            int length = normalized.length();
            boolean truncated = length > MESSAGE_TEXT_LIMIT;
            boolean toolRequest = containsToolRequest(normalized);
            boolean toolResponse = containsToolResponse(normalized);
            return new MessageContentSummary(
                length,
                truncate(normalized, MESSAGE_PREVIEW_LIMIT),
                truncated ? normalized.substring(0, MESSAGE_TEXT_LIMIT) : normalized,
                truncated,
                toolRequest,
                toolResponse,
                extractToolName(normalized),
                normalized.toLowerCase(Locale.ROOT).contains("error")
            );
        }
    }

    boolean containsToolResponse(String value) {
        String text = value == null ? "" : value;
        return text.contains("toolResponse")
            || text.contains("tool_response")
            || text.contains("tool_result");
    }

    MessageMetadata parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new MessageMetadata(true, true);
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            return new MessageMetadata(
                booleanValue(root.get("userVisible"), true),
                booleanValue(root.get("agentVisible"), true)
            );
        } catch (JsonProcessingException ex) {
            return new MessageMetadata(true, true);
        }
    }

    private void collectContent(JsonNode node, StringBuilder text, ContentSignals signals) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String type = textValue(node.get("type"));
            if ("text".equals(type) || "thinking".equals(type)) {
                appendText(text, textValue(node.get("text")));
                appendText(text, textValue(node.get("thinking")));
                return;
            }
            if (node.has("toolRequest") || "tool_use".equals(type)) {
                signals.toolRequest = true;
                JsonNode tool = node.has("toolRequest") ? node.get("toolRequest") : node;
                signals.toolName = firstNonBlank(Arrays.asList(signals.toolName, textValue(tool.get("name")), textValue(tool.get("toolName"))));
                appendText(text, summarizeJson(tool));
            }
            if (node.has("toolResponse") || "tool_result".equals(type)) {
                signals.toolResponse = true;
                JsonNode tool = node.has("toolResponse") ? node.get("toolResponse") : node;
                signals.toolName = firstNonBlank(Arrays.asList(signals.toolName, textValue(tool.get("name")), textValue(tool.get("toolName"))));
                appendText(text, summarizeJson(tool));
            }
            if (node.has("error") || "error".equalsIgnoreCase(textValue(node.get("status")))) {
                signals.error = true;
            }
            for (JsonNode child : node) {
                collectContent(child, text, signals);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectContent(child, text, signals);
            }
            return;
        }
        if (node.isTextual()) {
            appendText(text, node.asText());
        }
    }

    private void detectTextualToolSignals(String value, ContentSignals signals) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (containsToolRequest(value)) {
            signals.toolRequest = true;
        }
        if (containsToolResponse(value)) {
            signals.toolResponse = true;
        }
        signals.toolName = firstNonBlank(Arrays.asList(signals.toolName, extractToolName(value)));
        if (value.toLowerCase(Locale.ROOT).contains("error")) {
            signals.error = true;
        }
    }

    private boolean containsToolRequest(String value) {
        String text = value == null ? "" : value;
        return text.contains("toolRequest")
            || text.contains("tool_request")
            || text.contains("tool_use");
    }

    private String extractToolName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = TOOL_NAME_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String findText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            JsonNode text = node.get("text");
            if (type != null && "text".equals(type.asText()) && text != null && text.isTextual()) {
                return text.asText();
            }
            for (JsonNode child : node) {
                String result = findText(child);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String result = findText(child);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }
        return null;
    }

    private boolean booleanValue(JsonNode node, boolean fallback) {
        return node == null || node.isNull() ? fallback : node.asBoolean(fallback);
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private void appendText(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(value);
    }

    private String summarizeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String text = findText(node);
        if (text != null && !text.isBlank()) {
            return text;
        }
        return node.toString();
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("[\\t\\r ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String firstNonBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    record MessageContentSummary(
        int contentLength,
        String preview,
        String text,
        boolean truncated,
        boolean toolRequest,
        boolean toolResponse,
        String toolName,
        boolean error
    ) {
    }

    record MessageMetadata(boolean userVisible, boolean agentVisible) {
    }

    private static final class ContentSignals {
        private boolean toolRequest;
        private boolean toolResponse;
        private String toolName;
        private boolean error;
    }
}
