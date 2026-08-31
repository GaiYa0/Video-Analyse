package com.ruoyi.web.service.deployment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.DeploymentBusinessEventTemplate;
import com.ruoyi.system.domain.DeploymentTaskEvent;
import com.ruoyi.system.service.IDeploymentBusinessEventTemplateService;

@Service
public class DeploymentBusinessEventCompiler
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern WHOLE_PLACEHOLDER_PATTERN = Pattern.compile("^(?:\\$\\{\\s*([^}]+?)\\s*\\}|\\{\\{\\s*([^}]+?)\\s*\\}\\})$");
    private static final Pattern INLINE_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*\\}|\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    @Autowired
    private IDeploymentBusinessEventTemplateService deploymentBusinessEventTemplateService;

    public List<DeploymentTaskEvent> compile(List<DeploymentTaskEvent> sourceEvents)
    {
        List<DeploymentTaskEvent> compiledEvents = new ArrayList<>();
        if (sourceEvents == null || sourceEvents.isEmpty())
        {
            return compiledEvents;
        }

        Map<String, String> ruleOwnership = new LinkedHashMap<>();
        for (int index = 0; index < sourceEvents.size(); ++index)
        {
            DeploymentTaskEvent source = sourceEvents.get(index);
            if (source == null)
            {
                continue;
            }

            DeploymentTaskEvent compiledEvent = copyEvent(source, index);
            ArrayNode compiledRules = compileRulesForEvent(compiledEvent, index);
            if (compiledRules != null)
            {
                compiledEvent.setCompiledRuleSnapshotJson(writeJson(compiledRules));
                compiledEvent.setCompiledRuleIdsJson(writeJson(extractRuleIds(compiledRules, compiledEvent, ruleOwnership)));
            }
            compiledEvents.add(compiledEvent);
        }
        return compiledEvents;
    }

    private DeploymentTaskEvent copyEvent(DeploymentTaskEvent source, int index)
    {
        DeploymentTaskEvent target = new DeploymentTaskEvent();
        target.setId(source.getId());
        target.setDeploymentId(source.getDeploymentId());
        target.setEventKey(normalizeEventKey(source.getEventKey(), index));
        target.setTemplateId(normalizeNullableText(source.getTemplateId()));
        target.setTemplateVersion(source.getTemplateVersion());
        target.setEventName(normalizeNullableText(source.getEventName()));
        target.setEnabled(source.getEnabled() == null ? Boolean.TRUE : source.getEnabled());
        target.setSortOrder(source.getSortOrder() == null ? index : source.getSortOrder());
        target.setParameterValuesJson(source.getParameterValuesJson());
        target.setCompiledRuleIdsJson(source.getCompiledRuleIdsJson());
        target.setCompiledRuleSnapshotJson(source.getCompiledRuleSnapshotJson());
        target.setCreateBy(source.getCreateBy());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateBy(source.getUpdateBy());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private ArrayNode compileRulesForEvent(DeploymentTaskEvent event, int index)
    {
        if (!StringUtils.isEmpty(event.getTemplateId()))
        {
            return compileRulesFromTemplate(event, index);
        }
        return normalizeExistingCompiledRules(event, index);
    }

    private ArrayNode compileRulesFromTemplate(DeploymentTaskEvent event, int index)
    {
        DeploymentBusinessEventTemplate template = deploymentBusinessEventTemplateService.selectByTemplateId(event.getTemplateId());
        if (template == null)
        {
            throw new IllegalArgumentException("业务事件模板不存在: " + event.getTemplateId());
        }
        if (StringUtils.isEmpty(template.getRuleBlueprintJson()))
        {
            throw new IllegalArgumentException("业务事件模板缺少ruleBlueprintJson: " + event.getTemplateId());
        }

        JsonNode blueprintNode = parseJson(template.getRuleBlueprintJson(), "业务事件模板ruleBlueprintJson解析失败: " + event.getTemplateId());
        ArrayNode ruleTemplates = extractRuleTemplates(blueprintNode);
        if (ruleTemplates == null || ruleTemplates.isEmpty())
        {
            throw new IllegalArgumentException("业务事件模板未定义可编译的behaviorRules: " + event.getTemplateId());
        }

        if (StringUtils.isEmpty(event.getEventName()))
        {
            event.setEventName(normalizeNullableText(template.getTemplateName()));
        }
        if (StringUtils.isEmpty(event.getEventName()))
        {
            event.setEventName(event.getEventKey());
        }
        if (event.getTemplateVersion() == null)
        {
            event.setTemplateVersion(template.getVersionNo());
        }

        JsonNode parameterValues = parseJsonOrDefaultObject(event.getParameterValuesJson());
        ObjectNode context = buildCompileContext(event, template, parameterValues, index);
        ArrayNode compiledRules = OBJECT_MAPPER.createArrayNode();
        for (int ruleIndex = 0; ruleIndex < ruleTemplates.size(); ++ruleIndex)
        {
            JsonNode resolvedNode = resolvePlaceholders(ruleTemplates.get(ruleIndex), context);
            if (resolvedNode == null || !resolvedNode.isObject())
            {
                continue;
            }
            ObjectNode normalizedRule = finalizeCompiledRule((ObjectNode) resolvedNode.deepCopy(), event, template, ruleIndex, true);
            if (normalizedRule != null)
            {
                compiledRules.add(normalizedRule);
            }
        }

        if (compiledRules.isEmpty())
        {
            throw new IllegalArgumentException("业务事件模板编译后未生成任何behaviorRules: " + event.getTemplateId());
        }
        validateSequenceRules(compiledRules, event);
        return compiledRules;
    }

    private ArrayNode normalizeExistingCompiledRules(DeploymentTaskEvent event, int index)
    {
        JsonNode snapshotNode = parseJsonQuietly(event.getCompiledRuleSnapshotJson());
        if (snapshotNode == null || !snapshotNode.isArray())
        {
            return null;
        }
        if (StringUtils.isEmpty(event.getEventName()))
        {
            event.setEventName(event.getEventKey());
        }

        ArrayNode normalizedRules = OBJECT_MAPPER.createArrayNode();
        for (int ruleIndex = 0; ruleIndex < snapshotNode.size(); ++ruleIndex)
        {
            JsonNode item = snapshotNode.get(ruleIndex);
            if (item == null || !item.isObject())
            {
                continue;
            }
            ObjectNode normalizedRule = finalizeCompiledRule((ObjectNode) item.deepCopy(), event, null, ruleIndex, false);
            if (normalizedRule != null)
            {
                normalizedRules.add(normalizedRule);
            }
        }
        validateSequenceRules(normalizedRules, event);
        return normalizedRules.isEmpty() ? null : normalizedRules;
    }

    private ArrayNode extractRuleTemplates(JsonNode blueprintNode)
    {
        if (blueprintNode == null || blueprintNode.isNull())
        {
            return null;
        }
        if (blueprintNode.isArray())
        {
            return (ArrayNode) blueprintNode;
        }
        if (!blueprintNode.isObject())
        {
            return null;
        }

        JsonNode behaviorRulesNode = blueprintNode.get("behaviorRules");
        if (behaviorRulesNode != null && behaviorRulesNode.isArray())
        {
            return (ArrayNode) behaviorRulesNode;
        }
        JsonNode rulesNode = blueprintNode.get("rules");
        if (rulesNode != null && rulesNode.isArray())
        {
            return (ArrayNode) rulesNode;
        }
        JsonNode ruleNode = blueprintNode.get("rule");
        if (ruleNode != null && ruleNode.isObject())
        {
            ArrayNode wrapped = OBJECT_MAPPER.createArrayNode();
            wrapped.add(ruleNode);
            return wrapped;
        }
        if (looksLikeBehaviorRule(blueprintNode))
        {
            ArrayNode wrapped = OBJECT_MAPPER.createArrayNode();
            wrapped.add(blueprintNode);
            return wrapped;
        }
        return null;
    }

    private boolean looksLikeBehaviorRule(JsonNode node)
    {
        return node != null
            && node.isObject()
            && (node.has("behaviorType") || node.has("type") || node.has("geometryId") || node.has("regionId") || node.has("lineId"));
    }

    private ObjectNode buildCompileContext(DeploymentTaskEvent event, DeploymentBusinessEventTemplate template,
        JsonNode parameterValues, int index)
    {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();

        ObjectNode eventNode = OBJECT_MAPPER.createObjectNode();
        eventNode.put("eventKey", StringUtils.nvl(event.getEventKey(), ""));
        eventNode.put("eventName", StringUtils.nvl(event.getEventName(), ""));
        eventNode.put("templateId", StringUtils.nvl(event.getTemplateId(), ""));
        if (event.getTemplateVersion() != null)
        {
            eventNode.put("templateVersion", event.getTemplateVersion());
        }
        eventNode.put("enabled", Boolean.TRUE.equals(event.getEnabled()));
        eventNode.put("sortOrder", event.getSortOrder() == null ? index : event.getSortOrder());

        ObjectNode templateNode = OBJECT_MAPPER.createObjectNode();
        if (template != null)
        {
            templateNode.put("templateId", StringUtils.nvl(template.getTemplateId(), ""));
            templateNode.put("templateCode", StringUtils.nvl(template.getTemplateCode(), ""));
            templateNode.put("templateName", StringUtils.nvl(template.getTemplateName(), ""));
            if (template.getVersionNo() != null)
            {
                templateNode.put("versionNo", template.getVersionNo());
            }
        }

        root.set("event", eventNode);
        root.set("template", templateNode);
        root.set("params", parameterValues == null ? OBJECT_MAPPER.createObjectNode() : parameterValues.deepCopy());
        root.set("parameterValues", parameterValues == null ? OBJECT_MAPPER.createObjectNode() : parameterValues.deepCopy());
        root.put("eventKey", StringUtils.nvl(event.getEventKey(), ""));
        root.put("eventName", StringUtils.nvl(event.getEventName(), ""));
        root.put("templateId", StringUtils.nvl(event.getTemplateId(), ""));
        if (event.getTemplateVersion() != null)
        {
            root.put("templateVersion", event.getTemplateVersion());
        }
        root.put("index", index);
        return root;
    }

    private JsonNode resolvePlaceholders(JsonNode node, ObjectNode context)
    {
        if (node == null || node.isNull())
        {
            return NullNode.getInstance();
        }
        if (node.isObject())
        {
            if (node.has("$valueFrom") && node.get("$valueFrom").isTextual())
            {
                JsonNode referenced = getContextValue(context, node.get("$valueFrom").asText());
                return referenced == null ? NullNode.getInstance() : referenced.deepCopy();
            }

            ObjectNode resolved = OBJECT_MAPPER.createObjectNode();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext())
            {
                String fieldName = fieldNames.next();
                resolved.set(fieldName, resolvePlaceholders(node.get(fieldName), context));
            }
            return resolved;
        }
        if (node.isArray())
        {
            ArrayNode resolved = OBJECT_MAPPER.createArrayNode();
            for (int i = 0; i < node.size(); ++i)
            {
                resolved.add(resolvePlaceholders(node.get(i), context));
            }
            return resolved;
        }
        if (!node.isTextual())
        {
            return node.deepCopy();
        }

        String text = node.asText();
        Matcher wholeMatcher = WHOLE_PLACEHOLDER_PATTERN.matcher(text);
        if (wholeMatcher.matches())
        {
            String path = firstNonBlank(wholeMatcher.group(1), wholeMatcher.group(2));
            JsonNode referenced = getContextValue(context, path);
            return referenced == null ? NullNode.getInstance() : referenced.deepCopy();
        }

        Matcher inlineMatcher = INLINE_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        boolean matched = false;
        while (inlineMatcher.find())
        {
            matched = true;
            String path = firstNonBlank(inlineMatcher.group(1), inlineMatcher.group(2));
            JsonNode referenced = getContextValue(context, path);
            inlineMatcher.appendReplacement(buffer, Matcher.quoteReplacement(stringifyInlineValue(referenced)));
        }
        if (!matched)
        {
            return node.deepCopy();
        }
        inlineMatcher.appendTail(buffer);
        return TextNode.valueOf(buffer.toString());
    }

    private JsonNode getContextValue(ObjectNode context, String path)
    {
        if (context == null || StringUtils.isEmpty(path))
        {
            return null;
        }
        JsonNode value = readPath(context, path);
        if (value != null && !value.isMissingNode())
        {
            return value;
        }
        if (path.indexOf('.') < 0)
        {
            value = readPath(context.path("event"), path);
            if (value != null && !value.isMissingNode())
            {
                return value;
            }
            value = readPath(context.path("params"), path);
            if (value != null && !value.isMissingNode())
            {
                return value;
            }
            value = readPath(context.path("template"), path);
            if (value != null && !value.isMissingNode())
            {
                return value;
            }
        }
        return null;
    }

    private JsonNode readPath(JsonNode root, String path)
    {
        if (root == null || StringUtils.isEmpty(path))
        {
            return null;
        }
        JsonNode current = root;
        String[] segments = path.split("\\.");
        for (String rawSegment : segments)
        {
            String segment = rawSegment == null ? "" : rawSegment.trim();
            if (segment.isEmpty() || current == null)
            {
                return null;
            }
            if (current.isArray())
            {
                int index = parseArrayIndex(segment);
                if (index < 0 || index >= current.size())
                {
                    return null;
                }
                current = current.get(index);
            }
            else
            {
                current = current.get(segment);
            }
        }
        return current;
    }

    private int parseArrayIndex(String value)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (Exception ex)
        {
            return -1;
        }
    }

    private String stringifyInlineValue(JsonNode value)
    {
        if (value == null || value.isNull())
        {
            return "";
        }
        if (value.isTextual())
        {
            return value.asText();
        }
        if (value.isNumber() || value.isBoolean())
        {
            return value.asText();
        }
        try
        {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (Exception ex)
        {
            return value.toString();
        }
    }

    private ObjectNode finalizeCompiledRule(ObjectNode rule, DeploymentTaskEvent event,
        DeploymentBusinessEventTemplate template, int ruleIndex, boolean rewriteRuleId)
    {
        if (rule == null)
        {
            return null;
        }

        String behaviorType = normalizeNullableText(readText(rule, "behaviorType", "type"));
        if (StringUtils.isEmpty(behaviorType))
        {
            return null;
        }
        rule.put("behaviorType", behaviorType);

        String originalId = normalizeNullableText(readText(rule, "id"));
        String generatedRuleId = rewriteRuleId || StringUtils.isEmpty(originalId)
            ? buildRuleId(event, originalId, behaviorType, ruleIndex)
            : originalId;
        rule.put("id", generatedRuleId);

        String ruleName = normalizeNullableText(readText(rule, "name"));
        if (StringUtils.isEmpty(ruleName))
        {
            String baseName = StringUtils.isEmpty(event.getEventName())
                ? (template == null ? event.getEventKey() : template.getTemplateName())
                : event.getEventName();
            ruleName = baseName + "_" + (ruleIndex + 1);
        }
        rule.put("name", ruleName);

        if (!rule.has("enabled") || rule.get("enabled").isNull())
        {
            rule.set("enabled", Boolean.TRUE.equals(event.getEnabled())
                ? JsonNodeFactory.instance.booleanNode(true)
                : JsonNodeFactory.instance.booleanNode(false));
        }
        if (!rule.has("geometryType") || rule.get("geometryType").isNull() || StringUtils.isEmpty(rule.path("geometryType").asText()))
        {
            rule.put("geometryType", "cross_line".equals(behaviorType) ? "line" : "region");
        }
        if (template != null)
        {
            rule.put("templateId", StringUtils.nvl(template.getTemplateId(), ""));
            if (event.getTemplateVersion() != null)
            {
                rule.put("templateVersion", event.getTemplateVersion());
            }
        }
        rule.put("businessEventKey", StringUtils.nvl(event.getEventKey(), ""));
        rule.put("businessEventName", StringUtils.nvl(event.getEventName(), ""));
        return rule;
    }

    private void validateSequenceRules(ArrayNode compiledRules, DeploymentTaskEvent event)
    {
        if (compiledRules == null || compiledRules.isEmpty())
        {
            return;
        }

        String eventLabel = StringUtils.isEmpty(event.getEventName()) ? event.getEventKey() : event.getEventName();
        Map<String, List<ObjectNode>> sequenceGroups = new LinkedHashMap<>();
        for (int i = 0; i < compiledRules.size(); ++i)
        {
            JsonNode item = compiledRules.get(i);
            if (item == null || !item.isObject())
            {
                continue;
            }
            ObjectNode rule = (ObjectNode) item;
            String sequenceId = normalizeNullableText(readText(rule, "sequenceId"));
            if (StringUtils.isEmpty(sequenceId))
            {
                continue;
            }
            sequenceGroups.computeIfAbsent(sequenceId, key -> new ArrayList<>()).add(rule);
        }

        for (Map.Entry<String, List<ObjectNode>> entry : sequenceGroups.entrySet())
        {
            String sequenceId = entry.getKey();
            List<ObjectNode> rules = entry.getValue();
            if (rules == null || rules.isEmpty())
            {
                continue;
            }

            String expectedSubjectObject = null;
            Map<Integer, Boolean> stageIndexes = new LinkedHashMap<>();
            int maxStageIndex = -1;
            for (ObjectNode rule : rules)
            {
                String behaviorType = normalizeNullableText(readText(rule, "behaviorType", "type"));
                String ruleId = normalizeNullableText(readText(rule, "id"));
                if (!isSequenceCapableBehaviorType(behaviorType) || isAggregateBehaviorType(behaviorType))
                {
                    throw new IllegalArgumentException("业务事件 " + eventLabel + " 的 sequence " + sequenceId
                        + " 包含不支持的规则类型: " + StringUtils.nvl(behaviorType, StringUtils.nvl(ruleId, "unknown")));
                }

                String subjectObject = extractSequenceSubjectObject(rule, behaviorType);
                if (!StringUtils.isEmpty(subjectObject))
                {
                    if (expectedSubjectObject == null)
                    {
                        expectedSubjectObject = subjectObject;
                    }
                    else if (!expectedSubjectObject.equals(subjectObject))
                    {
                        throw new IllegalArgumentException("业务事件 " + eventLabel + " 的 sequence " + sequenceId
                            + " 存在多个主体目标: " + expectedSubjectObject + " / " + subjectObject);
                    }
                }

                int stageIndex = parseStageIndex(rule.get("stageIndex"));
                stageIndexes.put(stageIndex, Boolean.TRUE);
                maxStageIndex = Math.max(maxStageIndex, stageIndex);
            }

            for (int expectedStageIndex = 0; expectedStageIndex <= maxStageIndex; ++expectedStageIndex)
            {
                if (!stageIndexes.containsKey(expectedStageIndex))
                {
                    throw new IllegalArgumentException("业务事件 " + eventLabel + " 的 sequence " + sequenceId
                        + " 阶段序号必须从 0 开始连续，缺少阶段: " + expectedStageIndex);
                }
            }
        }
    }

    private ArrayNode extractRuleIds(ArrayNode compiledRules, DeploymentTaskEvent event,
        Map<String, String> ruleOwnership)
    {
        ArrayNode ruleIds = OBJECT_MAPPER.createArrayNode();
        if (compiledRules == null)
        {
            return ruleIds;
        }

        String owner = StringUtils.isEmpty(event.getEventName()) ? event.getEventKey() : event.getEventName();
        for (int i = 0; i < compiledRules.size(); ++i)
        {
            JsonNode item = compiledRules.get(i);
            if (item == null || !item.isObject())
            {
                continue;
            }
            String ruleId = normalizeNullableText(readText(item, "id"));
            if (StringUtils.isEmpty(ruleId))
            {
                continue;
            }
            String existingOwner = ruleOwnership.putIfAbsent(ruleId, owner);
            if (existingOwner != null && !existingOwner.equals(owner))
            {
                throw new IllegalArgumentException("业务事件编译后规则ID冲突: " + ruleId + " 已被 " + existingOwner + " 使用");
            }
            ruleIds.add(ruleId);
        }
        return ruleIds;
    }

    private String buildRuleId(DeploymentTaskEvent event, String originalId, String behaviorType, int ruleIndex)
    {
        String eventKey = sanitizeIdentifier(StringUtils.isEmpty(event.getEventKey()) ? "event" : event.getEventKey());
        String suffix = sanitizeIdentifier(StringUtils.isEmpty(originalId) ? behaviorType + "_" + (ruleIndex + 1) : originalId);
        return "be_" + eventKey + "_" + suffix;
    }

    private String sanitizeIdentifier(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return "rule";
        }
        String sanitized = value.trim().replaceAll("[^0-9A-Za-z_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "");
        sanitized = sanitized.replaceAll("_+$", "");
        return sanitized.isEmpty() ? "rule" : sanitized;
    }

    private String normalizeEventKey(String eventKey, int index)
    {
        String normalized = normalizeNullableText(eventKey);
        return normalized == null ? "event_" + (index + 1) : normalized;
    }

    private String normalizeNullableText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isSequenceCapableBehaviorType(String behaviorType)
    {
        return "cross_line".equals(behaviorType)
            || "enter_region".equals(behaviorType)
            || "exit_region".equals(behaviorType)
            || "dwell".equals(behaviorType)
            || "low_speed".equals(behaviorType)
            || "loitering".equals(behaviorType)
            || "direction_move".equals(behaviorType)
            || "direction_reverse".equals(behaviorType)
            || "relation_near".equals(behaviorType)
            || "relation_apart".equals(behaviorType)
            || "relation_not_contains".equals(behaviorType);
    }

    private boolean isRelationalBehaviorType(String behaviorType)
    {
        return "relation_near".equals(behaviorType)
            || "relation_apart".equals(behaviorType)
            || "relation_not_contains".equals(behaviorType);
    }

    private boolean isAggregateBehaviorType(String behaviorType)
    {
        return "count_threshold".equals(behaviorType)
            || "occupancy".equals(behaviorType)
            || "absence".equals(behaviorType);
    }

    private String extractSequenceSubjectObject(JsonNode rule, String behaviorType)
    {
        if (isRelationalBehaviorType(behaviorType))
        {
            return normalizeNullableText(readText(rule, "subjectObject", "subject_object", "subjectCode", "subjectClass"));
        }
        return normalizeNullableText(readText(rule,
            "ruleObjectCode", "rule_object_code", "objectCode", "objectClass"));
    }

    private int parseStageIndex(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return 0;
        }
        if (node.isInt() || node.isLong() || node.isShort())
        {
            return Math.max(0, node.asInt());
        }
        if (node.isTextual())
        {
            try
            {
                return Math.max(0, Integer.parseInt(node.asText().trim()));
            }
            catch (Exception ex)
            {
                return 0;
            }
        }
        return Math.max(0, node.asInt(0));
    }

    private String readText(JsonNode node, String... fieldNames)
    {
        if (node == null || fieldNames == null)
        {
            return null;
        }
        for (String fieldName : fieldNames)
        {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull())
            {
                if (value.isTextual())
                {
                    return value.asText();
                }
                if (value.isNumber() || value.isBoolean())
                {
                    return value.asText();
                }
            }
        }
        return null;
    }

    private JsonNode parseJson(String text, String errorMessage)
    {
        try
        {
            return OBJECT_MAPPER.readTree(text);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException(errorMessage, ex);
        }
    }

    private JsonNode parseJsonQuietly(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        try
        {
            return OBJECT_MAPPER.readTree(text);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private JsonNode parseJsonOrDefaultObject(String text)
    {
        JsonNode value = parseJsonQuietly(text);
        return value == null ? OBJECT_MAPPER.createObjectNode() : value;
    }

    private String writeJson(JsonNode node)
    {
        if (node == null)
        {
            return null;
        }
        try
        {
            return OBJECT_MAPPER.writeValueAsString(node);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("业务事件编译结果序列化失败", ex);
        }
    }

    private String firstNonBlank(String first, String second)
    {
        return StringUtils.isEmpty(first) ? second : first;
    }
}