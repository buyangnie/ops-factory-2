/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.operationintelligence.service;

import com.huawei.opsfactory.operationintelligence.qos.model.CallFlow;
import com.huawei.opsfactory.operationintelligence.qos.model.FlowNode;
import com.huawei.opsfactory.operationintelligence.qos.model.IpStat;
import com.huawei.opsfactory.operationintelligence.qos.model.TraceLogRecord;
import com.huawei.opsfactory.operationintelligence.qos.parser.TraceLogParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Call Chain Statistics Calculator.
 * Calculates statistics for call flows and nodes.
 *
 * @author x00000000
 * @since 2026-05-14
 */
@Component
/**
 * Call Chain Statistics.
 *
 * @author x00000000
 * @since 2026-05-27
 */
public class CallChainStatistics {

    private static final Logger log = LoggerFactory.getLogger(CallChainStatistics.class);

    private final TraceLogParser parser;

    /**
     * Call Chain Statistics.
     *
     * @param parser the trace log parser
     */
    public CallChainStatistics(TraceLogParser parser) {
        this.parser = parser;
    }

    /**
     * Calculate statistics for a call flow.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    public void calculateStatistics(CallFlow flow, Map<String, List<TraceLogRecord>> byTraceId) {

        // Calculate success statistics
        calculateSuccessStatistics(flow, byTraceId);

        // Calculate flow-level cost statistics
        calculateFlowCostStatistics(flow, byTraceId);

        // Get all unique seqNo positions
        Set<String> seqNos =
            byTraceId.values().stream().flatMap(List::stream).map(TraceLogRecord::getSeqNo).collect(Collectors.toSet());

        List<FlowNode> nodes = new ArrayList<>();

        // Build node for each seqNo position
        for (String seqNo : seqNos) {
            List<TraceLogRecord> positionLogs = byTraceId.values()
                .stream()
                .flatMap(List::stream)
                .filter(log -> seqNo.equals(log.getSeqNo()))
                .collect(Collectors.toList());

            FlowNode node = buildNode(seqNo, positionLogs);
            nodes.add(node);
        }

        // Sort nodes by seqNo
        nodes.sort(Comparator.comparing(FlowNode::getSeqNo, this::compareSeqNo));

        flow.setNodes(nodes);
    }

    /**
     * Calculate success statistics for a flow.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    private void calculateSuccessStatistics(CallFlow flow, Map<String, List<TraceLogRecord>> byTraceId) {
        // Count successful traceIds (all logs in the trace are successful)
        long totalSuccess =
            byTraceId.values().stream().filter(traceLogs -> traceLogs.stream().allMatch(parser::isSuccess)).count();

        flow.setSuccessCount(totalSuccess);

        if (flow.getCallCount() > 0) {
            flow.setSuccessPercent(totalSuccess * 100.0 / flow.getCallCount());
        }
    }

    /**
     * Calculate flow-level cost statistics.
     *
     * @param flow the call flow
     * @param byTraceId trace logs grouped by trace ID
     */
    private void calculateFlowCostStatistics(CallFlow flow, Map<String, List<TraceLogRecord>> byTraceId) {
        LongSummaryStatistics costStats = new LongSummaryStatistics();

        for (List<TraceLogRecord> traceLogs : byTraceId.values()) {
            // Sum all costs in this trace
            long traceTotalCost =
                traceLogs.stream().filter(log -> log.getCost() != null).mapToLong(TraceLogRecord::getCost).sum();

            if (traceTotalCost > 0) {
                costStats.accept(traceTotalCost);
            }
        }

        if (costStats.getCount() > 0) {
            flow.setAvgCost((long) costStats.getAverage());
            flow.setMinCost(costStats.getMin());
            flow.setMaxCost(costStats.getMax());
        }
    }

    /**
     * Build a flow node from logs at a specific seqNo position.
     *
     * @param seqNo the seqNo
     * @param logs the trace logs at this position
     * @return the flow node
     */
    private FlowNode buildNode(String seqNo, List<TraceLogRecord> logs) {
        FlowNode node = new FlowNode();
        node.setSeqNo(seqNo);

        // Set node fields from first log
        TraceLogRecord sample = logs.get(0);
        setNodeFieldsFromSample(node, sample);

        // Calculate IP statistics
        node.setIpList(calculateIpStatistics(logs));

        // Extract cluster type id string (comma-separated)
        node.setClusterTypeId(extractClusterTypes(logs));

        // Extract cluster id string (comma-separated)
        node.setClusterId(extractClusterIds(logs));

        // Calculate node-level cost statistics
        calculateNodeCostStatistics(node, logs);

        return node;
    }

    /**
     * Set node fields from sample trace log record.
     *
     * @param node the flow node
     * @param sample the sample record
     */
    private void setNodeFieldsFromSample(FlowNode node, TraceLogRecord sample) {
        if (sample.getUrl() != null) {
            node.setUrl(sample.getUrl());
        } else if (sample.getServiceName() != null) {
            node.setServiceName(sample.getServiceName());
            node.setOperationName(sample.getOperationName());
        } else if (sample.getTopic() != null) {
            node.setTopic(sample.getTopic());
            node.setEventName(sample.getEventName());
        }

        // BPM-specific fields
        if (sample.getBusiCode() != null) {
            node.setBusiCode(sample.getBusiCode());
        }
        if (sample.getProcessName() != null) {
            node.setProcessName(sample.getProcessName());
        }
        if (sample.getElementName() != null) {
            node.setElementName(sample.getElementName());
        }
        if (sample.getElementType() != null) {
            node.setElementType(sample.getElementType());
        }
    }

    /**
     * Calculate IP statistics for logs at a seqNo position.
     *
     * @param logs the trace logs
     * @return list of IP statistics
     */
    private List<IpStat> calculateIpStatistics(List<TraceLogRecord> logs) {
        Map<String, IpStatAccumulator> byIp = new LinkedHashMap<>();

        for (TraceLogRecord log : logs) {
            String ip = log.getIp();
            if (ip == null || ip.isEmpty()) {
                continue;
            }

            IpStatAccumulator acc = byIp.computeIfAbsent(ip, k -> new IpStatAccumulator(ip));
            acc.totalCalls++;

            if (parser.isSuccess(log)) {
                acc.successCalls++;
            }

            if (log.getCost() != null) {
                acc.totalCost += log.getCost();
                acc.costCount++;
                if (log.getCost() < acc.minCost) {
                    acc.minCost = log.getCost();
                }
                if (log.getCost() > acc.maxCost) {
                    acc.maxCost = log.getCost();
                }
            }
        }

        return byIp.values().stream().map(IpStatAccumulator::toIpStat).collect(Collectors.toList());
    }

    /**
     * Extract unique cluster type values from logs.
     *
     * @param logs the trace logs
     * @return first cluster type value
     */
    private String extractClusterTypes(List<TraceLogRecord> logs) {
        return logs.stream()
            .map(TraceLogRecord::getCluster)
            .filter(cluster -> cluster != null && !cluster.isEmpty())
            .findFirst()
            .orElse(null);
    }

    /**
     * Extract unique cluster id values from logs.
     *
     * @param logs the trace logs
     * @return first cluster id value
     */
    private String extractClusterIds(List<TraceLogRecord> logs) {
        return logs.stream()
            .map(TraceLogRecord::getClusterId)
            .filter(clusterId -> clusterId != null && !clusterId.isEmpty())
            .findFirst()
            .orElse(null);
    }

    /**
     * Calculate node-level cost statistics.
     *
     * @param node the flow node
     * @param logs the trace logs
     */
    private void calculateNodeCostStatistics(FlowNode node, List<TraceLogRecord> logs) {
        LongSummaryStatistics costStats =
            logs.stream().filter(log -> log.getCost() != null).mapToLong(TraceLogRecord::getCost).summaryStatistics();

        if (costStats.getCount() > 0) {
            node.setAvgCost((long) costStats.getAverage());
            node.setMinCost(costStats.getMin());
            node.setMaxCost(costStats.getMax());
        }
    }

    /**
     * Compare seqNo values.
     *
     * @param s1 first seqNo
     * @param s2 second seqNo
     * @return comparison result
     */
    private int compareSeqNo(String s1, String s2) {
        if (s1 == null)
            s1 = "0";
        if (s2 == null)
            s2 = "0";

        String[] parts1 = s1.split("\\.");
        String[] parts2 = s2.split("\\.");

        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int v1 = i < parts1.length ? parseSeqNoPart(parts1[i]) : 0;
            int v2 = i < parts2.length ? parseSeqNoPart(parts2[i]) : 0;
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }

    /**
     * Parse a single seqNo part to integer.
     *
     * @param part the seqNo part
     * @return the integer value
     */
    private int parseSeqNoPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Accumulator for IP statistics calculation.
     */
    private static class IpStatAccumulator {
        private final String ip;

        private int totalCalls = 0;

        private int successCalls = 0;

        private long totalCost = 0;

        private int costCount = 0;

        private long minCost = Long.MAX_VALUE;

        private long maxCost = Long.MIN_VALUE;

        IpStatAccumulator(String ip) {
            this.ip = ip;
        }

        IpStat toIpStat() {
            IpStat stat = new IpStat();
            stat.setIp(ip);
            stat.setCallCount((long) totalCalls);
            stat.setSuccessCount((long) successCalls);

            if (totalCalls > 0) {
                stat.setSuccessPercent(successCalls * 100.0 / totalCalls);
            }

            if (costCount > 0) {
                stat.setAvgCost(totalCost / costCount);
                stat.setMinCost(minCost);
                stat.setMaxCost(maxCost);
            }

            return stat;
        }
    }

    /**
     * Merge flows by service name (service mode).
     * Merges flows that have the same node sequence after service-level node merging.
     *
     * @param flows the list of flows
     * @return merged list of flows
     */
    public List<CallFlow> mergeFlowsByService(List<CallFlow> flows) {
        // Step 1: Merge nodes within each flow by serviceName
        List<CallFlow> mergedNodeFlows = flows.stream()
            .map(this::mergeNodesByServiceName)
            .collect(Collectors.toList());

        // Step 2: Group flows by node sequence signature
        Map<String, List<CallFlow>> byNodeSequence = new LinkedHashMap<>();
        for (CallFlow flow : mergedNodeFlows) {
            String sequenceSig = generateNodeSequenceSignature(flow.getNodes());
            byNodeSequence.computeIfAbsent(sequenceSig, k -> new ArrayList<>()).add(flow);
        }

        // Step 3: Merge flows with same node sequence
        List<CallFlow> result = new ArrayList<>();
        for (Map.Entry<String, List<CallFlow>> entry : byNodeSequence.entrySet()) {
            List<CallFlow> flowList = entry.getValue();
            if (flowList.size() == 1) {
                result.add(flowList.get(0));
            } else {
                result.add(mergeFlows(flowList));
            }
        }

        return result;
    }

    /**
     * Merge nodes within a flow by service name.
     *
     * @param flow the call flow
     * @return merged flow
     */
    private CallFlow mergeNodesByServiceName(CallFlow flow) {
        Map<String, List<FlowNode>> byServiceName = new LinkedHashMap<>();

        for (FlowNode node : flow.getNodes()) {
            String serviceName = node.getServiceName() != null ? node.getServiceName() :
                              node.getUrl() != null ? "URL:" + node.getUrl() :
                              "UNKNOWN";
            byServiceName.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(node);
        }

        List<FlowNode> mergedNodes = new ArrayList<>();
        for (Map.Entry<String, List<FlowNode>> entry : byServiceName.entrySet()) {
            List<FlowNode> serviceNodes = entry.getValue();
            FlowNode merged = mergeServiceNodes(serviceNodes);
            mergedNodes.add(merged);
        }

        CallFlow mergedFlow = new CallFlow();
        mergedFlow.setFlowId(flow.getFlowId());
        mergedFlow.setCallCount(flow.getCallCount());
        mergedFlow.setCallRatio(flow.getCallRatio());
        mergedFlow.setSuccessCount(flow.getSuccessCount());
        mergedFlow.setSuccessPercent(flow.getSuccessPercent());
        mergedFlow.setNodes(mergedNodes);

        // Merge flow-level cost statistics
        if (flow.getAvgCost() != null) {
            mergedFlow.setAvgCost(flow.getAvgCost());
        }
        if (flow.getMinCost() != null) {
            mergedFlow.setMinCost(flow.getMinCost());
        }
        if (flow.getMaxCost() != null) {
            mergedFlow.setMaxCost(flow.getMaxCost());
        }

        return mergedFlow;
    }

    /**
     * Merge multiple service nodes into one.
     *
     * @param serviceNodes list of nodes with the same service
     * @return merged node
     */
    private FlowNode mergeServiceNodes(List<FlowNode> serviceNodes) {
        FlowNode merged = new FlowNode();

        // Use first node as template for basic fields
        FlowNode first = serviceNodes.get(0);
        merged.setServiceName(first.getServiceName());
        merged.setOperationName(first.getOperationName());
        merged.setUrl(first.getUrl());
        merged.setTopic(first.getTopic());
        merged.setEventName(first.getEventName());
        // BPM-specific fields
        if (first.getBusiCode() != null) {
            merged.setBusiCode(first.getBusiCode());
        }
        if (first.getProcessName() != null) {
            merged.setProcessName(first.getProcessName());
        }
        if (first.getElementName() != null) {
            merged.setElementName(first.getElementName());
        }
        if (first.getElementType() != null) {
            merged.setElementType(first.getElementType());
        }

        // Merge operationNames (deduplicate and join with comma)
        Set<String> opNames = serviceNodes.stream()
            .map(FlowNode::getOperationName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (!opNames.isEmpty()) {
            merged.setOperationName(String.join(",", opNames));
        }

        // Collect all unique seqNos from merged nodes and select minimum
        Set<String> seqNos = serviceNodes.stream()
            .map(FlowNode::getSeqNo)
            .collect(Collectors.toSet());
        if (!seqNos.isEmpty()) {
            String minSeqNo = seqNos.stream()
                .min(this::compareSeqNo)
                .orElse(seqNos.iterator().next());
            merged.setSeqNo(minSeqNo);
        }

        // Merge IP statistics
        merged.setIpList(mergeIpStatisticsFromNodes(serviceNodes));

        // Merge cost statistics
        merged.setAvgCost(mergeCostStatsFromNodes(serviceNodes, "avg"));
        merged.setMinCost(mergeCostStatsFromNodes(serviceNodes, "min"));
        merged.setMaxCost(mergeCostStatsFromNodes(serviceNodes, "max"));

        // Merge clusterId and clusterTypeId (comma-separated strings)
        merged.setClusterId(mergeClusterIdsFromNodes(serviceNodes));
        merged.setClusterTypeId(mergeClusterTypeIdsFromNodes(serviceNodes));

        return merged;
    }

    /**
     * Merge multiple flows into one.
     *
     * @param flows list of flows to merge
     * @return merged flow
     */
    private CallFlow mergeFlows(List<CallFlow> flows) {
        CallFlow merged = new CallFlow();
        merged.setFlowId("flow_" + UUID.randomUUID().toString().substring(0, 8));

        // Sum call counts
        long totalCallCount = flows.stream().mapToLong(CallFlow::getCallCount).sum();
        merged.setCallCount(totalCallCount);

        // Calculate call ratio
        long totalCount = flows.stream().mapToLong(CallFlow::getCallCount).sum();
        if (totalCount > 0) {
            merged.setCallRatio(totalCallCount * 100.0 / totalCount);
        }

        // Sum success counts
        long totalSuccessCount = flows.stream().mapToLong(CallFlow::getSuccessCount).sum();
        merged.setSuccessCount(totalSuccessCount);

        // Calculate success percent
        if (totalCallCount > 0) {
            merged.setSuccessPercent(totalSuccessCount * 100.0 / totalCallCount);
        }

        // Merge cost statistics
        List<Long> avgCosts = flows.stream()
            .map(CallFlow::getAvgCost)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!avgCosts.isEmpty()) {
            merged.setAvgCost(avgCosts.stream().mapToLong(Long::longValue).sum() / avgCosts.size());
        }

        List<Long> minCosts = flows.stream()
            .map(CallFlow::getMinCost)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!minCosts.isEmpty()) {
            merged.setMinCost(minCosts.stream().min(Long::compare).orElse(null));
        }

        List<Long> maxCosts = flows.stream()
            .map(CallFlow::getMaxCost)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!maxCosts.isEmpty()) {
            merged.setMaxCost(maxCosts.stream().max(Long::compare).orElse(null));
        }

        // Merge nodes by service name again (across flows)
        List<FlowNode> allNodes = flows.stream()
            .flatMap(flow -> flow.getNodes().stream())
            .collect(Collectors.toList());

        Map<String, List<FlowNode>> byServiceName = new LinkedHashMap<>();
        for (FlowNode node : allNodes) {
            String serviceName = node.getServiceName() != null ? node.getServiceName() :
                              node.getUrl() != null ? "URL:" + node.getUrl() :
                              "UNKNOWN";
            byServiceName.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(node);
        }

        List<FlowNode> finalNodes = new ArrayList<>();
        for (Map.Entry<String, List<FlowNode>> entry : byServiceName.entrySet()) {
            finalNodes.add(mergeServiceNodes(entry.getValue()));
        }

        merged.setNodes(finalNodes);
        return merged;
    }

    /**
     * Generate node sequence signature for flow merging.
     *
     * @param nodes the flow nodes
     * @return the sequence signature
     */
    private String generateNodeSequenceSignature(List<FlowNode> nodes) {
        String sequence = nodes.stream()
            .map(node -> node.getServiceName() != null ? node.getServiceName() :
                          node.getUrl() != null ? node.getUrl() : "UNKNOWN")
            .collect(Collectors.joining("->"));

        // Use SHA-256 hash for fixed-length signature
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sequence.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return sequence;
        }
    }

    /**
     * Merge IP statistics from multiple nodes.
     *
     * @param nodes list of flow nodes
     * @return merged IP statistics list
     */
    private List<IpStat> mergeIpStatisticsFromNodes(List<FlowNode> nodes) {
        Map<String, IpMergeAccumulator> byIp = new LinkedHashMap<>();

        for (FlowNode node : nodes) {
            if (node.getIpList() == null) {
                continue;
            }
            for (IpStat ipStat : node.getIpList()) {
                String ip = ipStat.getIp();
                IpMergeAccumulator acc = byIp.computeIfAbsent(ip, k -> new IpMergeAccumulator(ip));
                acc.totalCalls += ipStat.getCallCount();
                acc.successCalls += ipStat.getSuccessCount();

                if (ipStat.getAvgCost() != null) {
                    acc.totalCost += ipStat.getAvgCost() * ipStat.getCallCount();
                    acc.costCount += ipStat.getCallCount();
                }

                if (ipStat.getMinCost() != null) {
                    acc.minCost = Math.min(acc.minCost, ipStat.getMinCost());
                }
                if (ipStat.getMaxCost() != null) {
                    acc.maxCost = Math.max(acc.maxCost, ipStat.getMaxCost());
                }
            }
        }

        return byIp.values().stream()
            .map(IpMergeAccumulator::toIpStat)
            .collect(Collectors.toList());
    }

    /**
     * Merge cost statistics from multiple nodes.
     *
     * @param nodes list of flow nodes
     * @param statType the stat type (avg, min, max)
     * @return merged cost value
     */
    private Long mergeCostStatsFromNodes(List<FlowNode> nodes, String statType) {
        if ("avg".equals(statType)) {
            long totalCost = 0;
            long totalCount = 0;
            for (FlowNode node : nodes) {
                if (node.getAvgCost() != null && node.getIpList() != null) {
                    // Calculate total calls from IP statistics
                    long nodeCalls = node.getIpList().stream()
                        .mapToLong(IpStat::getCallCount)
                        .sum();
                    totalCost += node.getAvgCost() * nodeCalls;
                    totalCount += nodeCalls;
                }
            }
            return totalCount > 0 ? totalCost / totalCount : null;
        } else if ("min".equals(statType)) {
            long min = Long.MAX_VALUE;
            for (FlowNode node : nodes) {
                if (node.getMinCost() != null && node.getMinCost() < min) {
                    min = node.getMinCost();
                }
            }
            return min != Long.MAX_VALUE ? min : null;
        } else if ("max".equals(statType)) {
            long max = Long.MIN_VALUE;
            for (FlowNode node : nodes) {
                if (node.getMaxCost() != null && node.getMaxCost() > max) {
                    max = node.getMaxCost();
                }
            }
            return max != Long.MIN_VALUE ? max : null;
        }
        return null;
    }

    /**
     * Merge cluster IDs from multiple nodes.
     *
     * @param nodes list of flow nodes
     * @return first cluster ID from all nodes
     */
    private String mergeClusterIdsFromNodes(List<FlowNode> nodes) {
        return nodes.stream()
            .filter(node -> node.getClusterId() != null && !node.getClusterId().isEmpty())
            .map(FlowNode::getClusterId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Merge cluster type IDs from multiple nodes.
     *
     * @param nodes list of flow nodes
     * @return first cluster type ID from all nodes
     */
    private String mergeClusterTypeIdsFromNodes(List<FlowNode> nodes) {
        return nodes.stream()
            .filter(node -> node.getClusterTypeId() != null && !node.getClusterTypeId().isEmpty())
            .map(FlowNode::getClusterTypeId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Accumulator for IP merge.
     */
    private static class IpMergeAccumulator {
        private final String ip;
        private long totalCalls = 0;
        private long successCalls = 0;
        private long totalCost = 0;
        private long costCount = 0;
        private long minCost = Long.MAX_VALUE;
        private long maxCost = Long.MIN_VALUE;

        IpMergeAccumulator(String ip) {
            this.ip = ip;
        }

        IpStat toIpStat() {
            IpStat stat = new IpStat();
            stat.setIp(ip);
            stat.setCallCount(totalCalls);
            stat.setSuccessCount(successCalls);

            if (totalCalls > 0) {
                stat.setSuccessPercent(successCalls * 100.0 / totalCalls);
            }

            if (costCount > 0) {
                stat.setAvgCost(totalCost / costCount);
                stat.setMinCost(minCost);
                stat.setMaxCost(maxCost);
            }

            return stat;
        }
    }
}
