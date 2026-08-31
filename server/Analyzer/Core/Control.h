#ifndef ANALYZER_CONTROL_H
#define ANALYZER_CONTROL_H

#include <string>
#include <vector>
#include <algorithm>
#include <cctype>
#include <limits>
#include <unordered_map>
#include <sstream>
#include <mutex>
#include <json/json.h>
#include <opencv2/opencv.hpp>
#include "Utils/Common.h"

namespace SVAAnalyzer
{
	class Algorithm;

	struct AlgorithmTask
	{
		std::string algorithmCode;
		std::string object_str;
		std::vector<std::string> objects_v1;
		int objects_v1_len = 0;
		std::string objectCode;
		std::vector<std::string> objectCodes;
		float detectFps = 8.0f;
		float scoreThreshold = 0.0f;
		bool scoreThresholdSet = false;
		float nmsThreshold = 0.0f;
		bool nmsThresholdSet = false;

		bool batch_enabled = false;
		int max_batch = 1;
		int max_wait_ms = 0;
		int queue_capacity = 64;
		bool timeout_drop = true;

		int64_t lastInferTimestampMs = 0;
		Algorithm *resolvedAlgorithm = nullptr;
		std::string resolvedAlgorithmCode;

		std::string getPrimaryObjectCode() const
		{
			if (!objectCodes.empty())
			{
				return objectCodes[0];
			}
			return objectCode;
		}
	};

	struct RegionConfig
	{
		std::string id;
		std::string name;
		std::string type = "polygon";
		bool primary = false;
		std::vector<double> normalizedPoints;
		std::vector<double> polygon_d;
		std::vector<cv::Point> polygon_points;

		bool hasValidNormalizedPolygon() const
		{
			return normalizedPoints.size() >= 8 && normalizedPoints.size() % 2 == 0;
		}
	};

	struct LineConfig
	{
		std::string id;
		std::string name;
		std::string type = "tripwire";
		std::string direction = "both";
		std::vector<double> normalizedPoints;
		std::vector<cv::Point> points;

		bool hasValidNormalizedLine() const
		{
			return normalizedPoints.size() >= 4 && normalizedPoints.size() % 2 == 0;
		}
	};

	struct BehaviorRuleConfig
	{
		std::string id;
		std::string name;
		std::string customEventName;
		std::string behaviorType;
		bool enabled = true;
		std::string geometryType;
		std::string geometryId;
		std::string direction = "both";
		std::string ruleObjectCode;
		std::string subjectObject;
		std::string targetObject;
		std::string relationType;
		int64_t thresholdMs = 0;
		int thresholdCount = 0;
		double distanceThresholdPx = 0.0;
		double maxSpeedPxPerSec = 0.0;
		double maxDisplacementPx = 0.0;
		double directionAngleDeg = 0.0;
		double directionToleranceDeg = 30.0;
		std::string sequenceId;
		int stageIndex = 0;
		int64_t stageTimeoutMs = 0;
		int64_t stageHoldMs = 0;
		std::string logicMode = "all";
	};

	struct Control
	{
		// 布控请求必需参数
	public:
		std::string code;		// 布控编号
		std::string streamCode; // 视频流编号
		std::string streamApp;	// 视频流app
		std::string streamName; // 视频流name
		std::string streamUrl;	// 拉流地址

		bool pushStream = false;   // 是否推流
		std::string pushStreamUrl; // 推流地址
		std::string pushEncoder = "auto"; // auto/libx264/h264_nvenc
		std::string renderMode = "server_overlay"; // server_overlay/ws_overlay/detect_only
		bool serverOverlayEnabled = true;
		bool wsOverlayEnabled = false;
		float wsEventFps = 8.0f; // websocket检测事件输出频率（<=0 表示不输出）
		std::string wsEventKeyMode = "control"; // control/class/class_algorithm
		int wsEventUpdateIntervalMs = 1000;
		int wsEventEndTimeoutMs = 2000;
		std::string wsEventRuleMode = "any"; // any/all_algorithms_per_class/all_algorithms_any_class
		std::string wsEventRequiredAlgorithmsStr;
		std::vector<std::string> wsEventRequiredAlgorithms;
		int wsEventMinHits = 1;
		int wsEventHitWindowMs = 1000;
		int wsEventPendingTimeoutMs = 3000;
		int wsEventRestartCooldownMs = 0;
		int wsFrameDebounceMs = 180;
		int wsPostRetryMax = 2;
		int wsPostFailOpenThreshold = 8;
		int wsPostCooldownMs = 1000;
		bool dwellEnabled = false;
		int64_t dwellThresholdMs = 5000;
		bool saveImageEnabled = true;
		bool saveVideoEnabled = true;
		int alarmIntervalMs = 180000;

		std::string algorithmCode; // 算法编号
		std::string api_url;	   // 算法api接口地址
		std::string object_str;	   // 当前算法支持的所有目标分类

		std::vector<std::string> objects_v1;
		int objects_v1_len;

		std::string objectCode; // 目标监测分类编号
		std::vector<std::string> objectCodes;
		std::vector<AlgorithmTask> algorithmTasks; // 新版多算法任务

		std::string recognitionRegion;					 // 算法识别区域坐标点 x1, y1, x2, y2, x3, y3, x4, y4
		std::vector<double> recognitionRegion_d;		 // 算法识别区域坐标点 x1, y1, x2, y2, x3, y3, x4, y4
		std::vector<cv::Point> recognitionRegion_points; // 算法识别区域
		std::vector<RegionConfig> regions;
		std::vector<LineConfig> lines;
		std::vector<BehaviorRuleConfig> behaviorRules;

		int64_t minInterval = 180000; // 布控最小的报警间隔时间（单位毫秒），3分钟=3*60*1000=180000毫秒

	public:
		// 通过计算获得的参数

		int64_t startTimestamp = 0; // 执行器启动时毫秒级时间戳（13位）
		float checkFps = 0;			// 算法检测的帧率（每秒检测的次数）
		float inferFps = 0;			// 真实发生推理的帧率（每秒推理次数）
		float detectFps = 8.0f;		// 目标检测帧率。语义（按数值范围）：
									// > 0  : 目标 fps（按时间间隔节流）
									// = 0  : 不抽帧，所有解码帧都送推理
									// = -1 : 仅关键帧检测
									// = -2 : 暂停检测
		int videoWidth = 0;			// 布控视频流的像素宽
		int videoHeight = 0;		// 布控视频流的像素高
		int videoChannel = 0;
		int videoIndex = -1;
		int videoFps = 0;
		int64_t lastWsEventTimestampMs = 0;

	public:
		static bool tryParseJsonNumber(const Json::Value &value, double &out)
		{
			if (value.isDouble() || value.isInt() || value.isUInt() || value.isInt64() || value.isUInt64())
			{
				out = value.asDouble();
				return true;
			}
			if (value.isString())
			{
				const std::string text = value.asString();
				if (text.empty())
				{
					return false;
				}
				try
				{
					out = std::stod(text);
					return true;
				}
				catch (...)
				{
					return false;
				}
			}
			return false;
		}

		static bool tryAppendPoint(const Json::Value &value, std::vector<double> &target)
		{
			double x = 0.0;
			double y = 0.0;
			if (value.isObject())
			{
				if (!tryParseJsonNumber(value["x"], x) || !tryParseJsonNumber(value["y"], y))
				{
					return false;
				}
			}
			else if (value.isArray() && value.size() >= 2)
			{
				if (!tryParseJsonNumber(value[0], x) || !tryParseJsonNumber(value[1], y))
				{
					return false;
				}
			}
			else
			{
				return false;
			}
			target.push_back(x);
			target.push_back(y);
			return true;
		}

		static bool parseNormalizedPointsArray(const Json::Value &value, std::vector<double> &target)
		{
			target.clear();
			if (!value.isArray() || value.empty())
			{
				return false;
			}

			const Json::Value &first = value[0];
			if (first.isObject() || first.isArray())
			{
				for (Json::ArrayIndex i = 0; i < value.size(); ++i)
				{
					if (!tryAppendPoint(value[i], target))
					{
						target.clear();
						return false;
					}
				}
				return !target.empty();
			}

			for (Json::ArrayIndex i = 0; i < value.size(); ++i)
			{
				double coordinate = 0.0;
				if (!tryParseJsonNumber(value[i], coordinate))
				{
					target.clear();
					return false;
				}
				target.push_back(coordinate);
			}
			return target.size() >= 4 && target.size() % 2 == 0;
		}

		static std::string joinNormalizedPoints(const std::vector<double> &points)
		{
			std::ostringstream oss;
			oss.precision(6);
			for (size_t i = 0; i < points.size(); ++i)
			{
				if (i > 0)
				{
					oss << ",";
				}
				oss << points[i];
			}
			return oss.str();
		}

		static std::string normalizeLineDirectionValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			if (value == "left_to_right" || value == "right_to_left")
			{
				return value;
			}
			return "both";
		}

		static std::string normalizeBehaviorTypeValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			if (value == "cross_line" || value == "enter_region" || value == "exit_region" || value == "dwell" ||
				value == "low_speed" || value == "loitering" || value == "sleep" || value == "absence" || value == "count_threshold" || value == "occupancy" || value == "region_motion" ||
				value == "direction_move" || value == "direction_reverse" || value == "relation_near" || value == "relation_apart" || value == "relation_not_contains" || value == "fight")
			{
				return value;
			}
			return "";
		}

		static std::string trimValue(std::string value)
		{
			auto begin = std::find_if_not(value.begin(), value.end(), [](unsigned char c) {
				return std::isspace(c) != 0;
			});
			auto end = std::find_if_not(value.rbegin(), value.rend(), [](unsigned char c) {
				return std::isspace(c) != 0;
			}).base();
			if (begin >= end)
			{
				return "";
			}
			return std::string(begin, end);
		}

		static std::string normalizeObjectClassValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			return trimValue(value);
		}

		static std::vector<std::string> normalizeObjectClassValues(const std::vector<std::string> &values)
		{
			std::vector<std::string> normalized;
			std::unordered_map<std::string, bool> seen;
			for (size_t i = 0; i < values.size(); ++i)
			{
				const std::string value = normalizeObjectClassValue(values[i]);
				if (value.empty() || seen.find(value) != seen.end())
				{
					continue;
				}
				seen[value] = true;
				normalized.push_back(value);
			}
			return normalized;
		}

		static std::string joinObjectClassValues(const std::vector<std::string> &values)
		{
			std::ostringstream builder;
			const std::vector<std::string> normalized = normalizeObjectClassValues(values);
			for (size_t i = 0; i < normalized.size(); ++i)
			{
				if (i > 0)
				{
					builder << ',';
				}
				builder << normalized[i];
			}
			return builder.str();
		}

		std::string getPrimaryObjectCode() const
		{
			if (!objectCodes.empty())
			{
				return objectCodes[0];
			}
			return objectCode;
		}

		std::string resolveDefaultRuleObjectCode() const
		{
			if (!objectCodes.empty())
			{
				return objectCodes[0];
			}
			for (size_t i = 0; i < algorithmTasks.size(); ++i)
			{
				const std::string value = algorithmTasks[i].getPrimaryObjectCode();
				if (!value.empty())
				{
					return value;
				}
			}
			return normalizeObjectClassValue(objectCode);
		}

		static std::string normalizeRelationTypeValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			if (value == "near" || value == "apart" || value == "not_contains")
			{
				return value;
			}
			return "";
		}

		static std::string normalizeSequenceIdValue(std::string value)
		{
			return trimValue(value);
		}

		static bool isSequenceCapableBehaviorType(const std::string &behaviorType)
		{
			return behaviorType == "cross_line" ||
				   behaviorType == "enter_region" ||
				   behaviorType == "exit_region" ||
				   behaviorType == "dwell" ||
				   behaviorType == "low_speed" ||
				   behaviorType == "loitering" ||
				   behaviorType == "sleep" ||
				   behaviorType == "direction_move" ||
				   behaviorType == "direction_reverse" ||
				   behaviorType == "relation_near" ||
				   behaviorType == "relation_apart" ||
				   behaviorType == "relation_not_contains" ||
				   behaviorType == "fight";
		}

		static std::string normalizeLogicModeValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			if (value == "any" || value == "all")
			{
				return value;
			}
			return "all";
		}

		static std::string normalizeBehaviorGeometryTypeValue(std::string value)
		{
			std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
				return static_cast<char>(std::tolower(c));
			});
			if (value == "line" || value == "region")
			{
				return value;
			}
			return "";
		}

		bool parseRecognitionRegionLegacy(std::vector<double> &normalizedPoints) const
		{
			normalizedPoints.clear();
			if (recognitionRegion.empty())
			{
				return false;
			}

			std::vector<std::string> xys = split(recognitionRegion, ",");
			if (xys.size() < 8 || xys.size() % 2 != 0)
			{
				return false;
			}

			for (size_t i = 0; i < xys.size(); ++i)
			{
				try
				{
					normalizedPoints.push_back(std::stod(xys[i]));
				}
				catch (...)
				{
					normalizedPoints.clear();
					return false;
				}
			}
			return true;
		}

		bool loadGeometryConfig(const Json::Value &regionsValue, const Json::Value &linesValue)
		{
			regions.clear();
			lines.clear();

			if (regionsValue.isArray())
			{
				for (Json::ArrayIndex i = 0; i < regionsValue.size(); ++i)
				{
					const Json::Value &item = regionsValue[i];
					RegionConfig region;
					if (item.isObject())
					{
						if (item["id"].isString())
						{
							region.id = item["id"].asString();
						}
						if (item["name"].isString())
						{
							region.name = item["name"].asString();
						}
						if (item["type"].isString())
						{
							region.type = item["type"].asString();
						}
						if (item["primary"].isBool())
						{
							region.primary = item["primary"].asBool();
						}
						else if (item["isPrimary"].isBool())
						{
							region.primary = item["isPrimary"].asBool();
						}

						const Json::Value &pointsValue = item["points"].isArray() ? item["points"] : (item["polygon"].isArray() ? item["polygon"] : item["vertices"]);
						parseNormalizedPointsArray(pointsValue, region.normalizedPoints);
					}
					else if (item.isArray())
					{
						parseNormalizedPointsArray(item, region.normalizedPoints);
					}

					if (!region.id.size())
					{
						region.id = "region_" + std::to_string(i);
					}
					if (region.hasValidNormalizedPolygon())
					{
						regions.push_back(region);
					}
				}
			}

			if (linesValue.isArray())
			{
				for (Json::ArrayIndex i = 0; i < linesValue.size(); ++i)
				{
					const Json::Value &item = linesValue[i];
					LineConfig line;
					if (item.isObject())
					{
						if (item["id"].isString())
						{
							line.id = item["id"].asString();
						}
						if (item["name"].isString())
						{
							line.name = item["name"].asString();
						}
						if (item["type"].isString())
						{
							line.type = item["type"].asString();
						}
						if (item["direction"].isString())
						{
							line.direction = item["direction"].asString();
						}
						else if (item["crossingDirection"].isString())
						{
							line.direction = item["crossingDirection"].asString();
						}

						if (item["points"].isArray())
						{
							parseNormalizedPointsArray(item["points"], line.normalizedPoints);
						}
						else if (item["vertices"].isArray())
						{
							parseNormalizedPointsArray(item["vertices"], line.normalizedPoints);
						}
						else if (item["start"].isObject() && item["end"].isObject())
						{
							line.normalizedPoints.clear();
							if (!tryAppendPoint(item["start"], line.normalizedPoints) || !tryAppendPoint(item["end"], line.normalizedPoints))
							{
								line.normalizedPoints.clear();
							}
						}
					}
					else if (item.isArray())
					{
						parseNormalizedPointsArray(item, line.normalizedPoints);
					}

					if (!line.id.size())
					{
						line.id = "line_" + std::to_string(i);
					}
					line.direction = normalizeLineDirectionValue(line.direction);
					if (line.hasValidNormalizedLine())
					{
						lines.push_back(line);
					}
				}
			}

			normalizeGeometryConfig();
			return !regions.empty() || !lines.empty();
		}

		bool loadBehaviorRulesConfig(const Json::Value &rulesValue)
		{
			behaviorRules.clear();
			if (!rulesValue.isArray())
			{
				return false;
			}

			for (Json::ArrayIndex i = 0; i < rulesValue.size(); ++i)
			{
				const Json::Value &item = rulesValue[i];
				if (!item.isObject())
				{
					continue;
				}

				BehaviorRuleConfig rule;
				if (item["id"].isString())
				{
					rule.id = item["id"].asString();
				}
				if (item["name"].isString())
				{
					rule.name = item["name"].asString();
				}
				if (item["customEventName"].isString())
				{
					rule.customEventName = item["customEventName"].asString();
				}
				else if (item["custom_event_name"].isString())
				{
					rule.customEventName = item["custom_event_name"].asString();
				}
				else if (item["alarmTypeName"].isString())
				{
					rule.customEventName = item["alarmTypeName"].asString();
				}
				else if (item["businessEventName"].isString())
				{
					rule.customEventName = item["businessEventName"].asString();
				}
				if (item["behaviorType"].isString())
				{
					rule.behaviorType = item["behaviorType"].asString();
				}
				else if (item["type"].isString())
				{
					rule.behaviorType = item["type"].asString();
				}
				if (item["enabled"].isBool())
				{
					rule.enabled = item["enabled"].asBool();
				}
				if (item["geometryType"].isString())
				{
					rule.geometryType = item["geometryType"].asString();
				}
				if (item["geometryId"].isString())
				{
					rule.geometryId = item["geometryId"].asString();
				}
				else if (item["lineId"].isString())
				{
					rule.geometryId = item["lineId"].asString();
				}
				else if (item["regionId"].isString())
				{
					rule.geometryId = item["regionId"].asString();
				}
				if (item["direction"].isString())
				{
					rule.direction = item["direction"].asString();
				}
				else if (item["crossingDirection"].isString())
				{
					rule.direction = item["crossingDirection"].asString();
				}
				if (item["thresholdMs"].isInt64())
				{
					rule.thresholdMs = item["thresholdMs"].asInt64();
				}
				else if (item["thresholdMs"].isInt())
				{
					rule.thresholdMs = static_cast<int64_t>(item["thresholdMs"].asInt());
				}
				else if (item["thresholdMs"].isString())
				{
					try
					{
						rule.thresholdMs = std::stoll(item["thresholdMs"].asString());
					}
					catch (...)
					{
					}
				}
				if (item["thresholdCount"].isInt())
				{
					rule.thresholdCount = item["thresholdCount"].asInt();
				}
				else if (item["countThreshold"].isInt())
				{
					rule.thresholdCount = item["countThreshold"].asInt();
				}
				else if (item["thresholdCount"].isString())
				{
					try
					{
						rule.thresholdCount = std::stoi(item["thresholdCount"].asString());
					}
					catch (...)
					{
					}
				}
				double speedThreshold = 0.0;
				if (item["ruleObjectCode"].isString())
				{
					rule.ruleObjectCode = item["ruleObjectCode"].asString();
				}
				else if (item["rule_object_code"].isString())
				{
					rule.ruleObjectCode = item["rule_object_code"].asString();
				}
				else if (item["objectCode"].isString())
				{
					rule.ruleObjectCode = item["objectCode"].asString();
				}
				if (item["subjectObject"].isString())
				{
					rule.subjectObject = item["subjectObject"].asString();
				}
				else if (item["subjectClass"].isString())
				{
					rule.subjectObject = item["subjectClass"].asString();
				}
				if (item["targetObject"].isString())
				{
					rule.targetObject = item["targetObject"].asString();
				}
				else if (item["targetClass"].isString())
				{
					rule.targetObject = item["targetClass"].asString();
				}
				if (item["relationType"].isString())
				{
					rule.relationType = item["relationType"].asString();
				}
				double distanceThreshold = 0.0;
				if (tryParseJsonNumber(item["distanceThresholdPx"], distanceThreshold) ||
					tryParseJsonNumber(item["distancePx"], distanceThreshold) ||
					tryParseJsonNumber(item["distanceThreshold"], distanceThreshold))
				{
					rule.distanceThresholdPx = distanceThreshold;
				}
				if (tryParseJsonNumber(item["maxSpeedPxPerSec"], speedThreshold) ||
					tryParseJsonNumber(item["speedThreshold"], speedThreshold) ||
					tryParseJsonNumber(item["maxSpeed"], speedThreshold))
				{
					rule.maxSpeedPxPerSec = speedThreshold;
				}
				double directionAngleDeg = 0.0;
				if (tryParseJsonNumber(item["directionAngleDeg"], directionAngleDeg) ||
					tryParseJsonNumber(item["directionAngle"], directionAngleDeg) ||
					tryParseJsonNumber(item["angleDeg"], directionAngleDeg))
				{
					rule.directionAngleDeg = directionAngleDeg;
				}
				double directionToleranceDeg = 0.0;
				if (tryParseJsonNumber(item["directionToleranceDeg"], directionToleranceDeg) ||
					tryParseJsonNumber(item["directionTolerance"], directionToleranceDeg) ||
					tryParseJsonNumber(item["angleToleranceDeg"], directionToleranceDeg))
				{
					rule.directionToleranceDeg = directionToleranceDeg;
				}
				double displacementThreshold = 0.0;
				if (tryParseJsonNumber(item["maxDisplacementPx"], displacementThreshold) ||
					tryParseJsonNumber(item["loiteringRadiusPx"], displacementThreshold) ||
					tryParseJsonNumber(item["radiusPx"], displacementThreshold))
				{
					rule.maxDisplacementPx = displacementThreshold;
				}
				if (item["sequenceId"].isString())
				{
					rule.sequenceId = item["sequenceId"].asString();
				}
				if (item["stageIndex"].isInt())
				{
					rule.stageIndex = item["stageIndex"].asInt();
				}
				if (item["stageTimeoutMs"].isInt64())
				{
					rule.stageTimeoutMs = item["stageTimeoutMs"].asInt64();
				}
				else if (item["stageTimeoutMs"].isInt())
				{
					rule.stageTimeoutMs = item["stageTimeoutMs"].asInt();
				}
				if (item["stageHoldMs"].isInt64())
				{
					rule.stageHoldMs = item["stageHoldMs"].asInt64();
				}
				else if (item["stageHoldMs"].isInt())
				{
					rule.stageHoldMs = item["stageHoldMs"].asInt();
				}
				if (item["logicMode"].isString())
				{
					rule.logicMode = item["logicMode"].asString();
				}
				behaviorRules.push_back(rule);
			}

			normalizeBehaviorRulesConfig();
			return !behaviorRules.empty();
		}

		void normalizeGeometryConfig()
		{
			if (regions.empty())
			{
				RegionConfig legacyRegion;
				legacyRegion.id = "region_primary";
				legacyRegion.name = "primary";
				legacyRegion.primary = true;
				if (parseRecognitionRegionLegacy(legacyRegion.normalizedPoints))
				{
					regions.push_back(legacyRegion);
				}
			}

			bool hasPrimaryRegion = false;
			for (size_t i = 0; i < regions.size(); ++i)
			{
				if (!regions[i].hasValidNormalizedPolygon())
				{
					continue;
				}
				if (!hasPrimaryRegion && regions[i].primary)
				{
					hasPrimaryRegion = true;
				}
				else if (hasPrimaryRegion)
				{
					regions[i].primary = false;
				}
			}
			if (!hasPrimaryRegion)
			{
				for (size_t i = 0; i < regions.size(); ++i)
				{
					if (regions[i].hasValidNormalizedPolygon())
					{
						regions[i].primary = true;
						break;
					}
				}
			}
			syncLegacyRecognitionRegionFromPrimaryNormalized();
		}

		const RegionConfig *findPrimaryRegion() const
		{
			for (size_t i = 0; i < regions.size(); ++i)
			{
				if (regions[i].primary && regions[i].hasValidNormalizedPolygon())
				{
					return &regions[i];
				}
			}
			for (size_t i = 0; i < regions.size(); ++i)
			{
				if (regions[i].hasValidNormalizedPolygon())
				{
					return &regions[i];
				}
			}
			return nullptr;
		}

		const RegionConfig *findRegionById(const std::string &regionId) const
		{
			if (regionId.empty())
			{
				return nullptr;
			}
			for (size_t i = 0; i < regions.size(); ++i)
			{
				if (regions[i].id == regionId && regions[i].hasValidNormalizedPolygon())
				{
					return &regions[i];
				}
			}
			return nullptr;
		}

		const LineConfig *findLineById(const std::string &lineId) const
		{
			if (lineId.empty())
			{
				return nullptr;
			}
			for (size_t i = 0; i < lines.size(); ++i)
			{
				if (lines[i].id == lineId && lines[i].hasValidNormalizedLine())
				{
					return &lines[i];
				}
			}
			return nullptr;
		}

		void normalizeBehaviorRulesConfig()
		{
			for (size_t i = 0; i < behaviorRules.size(); ++i)
			{
				BehaviorRuleConfig &rule = behaviorRules[i];
				rule.behaviorType = normalizeBehaviorTypeValue(rule.behaviorType);
				if (rule.behaviorType.empty())
				{
					continue;
				}
				rule.geometryType = normalizeBehaviorGeometryTypeValue(rule.geometryType);
				if (rule.behaviorType == "cross_line")
				{
					if (rule.geometryType.empty())
					{
						rule.geometryType = "line";
					}
					rule.direction = normalizeLineDirectionValue(rule.direction);
					rule.thresholdMs = 0;
					rule.thresholdCount = 0;
					rule.maxSpeedPxPerSec = 0.0;
					rule.maxDisplacementPx = 0.0;
				}
				else if (rule.behaviorType == "region_motion")
				{
					rule.geometryType = "region";
					rule.thresholdMs = std::max<int64_t>(200, std::min<int64_t>(3600000,
						rule.thresholdMs > 0 ? rule.thresholdMs : 3000));
					rule.thresholdCount = 0;
					rule.distanceThresholdPx = std::max(1.0, std::min(100.0,
						rule.distanceThresholdPx > 0.0 ? rule.distanceThresholdPx : 12.0));
					rule.maxSpeedPxPerSec = 0.0;
					rule.maxDisplacementPx = 0.0;
					rule.direction = "both";
					rule.directionAngleDeg = 0.0;
					rule.directionToleranceDeg = 30.0;
					rule.ruleObjectCode = "specified_region";
					rule.subjectObject.clear();
					rule.targetObject.clear();
					rule.relationType.clear();
				}
				else if (rule.behaviorType == "direction_move" || rule.behaviorType == "direction_reverse")
				{
					if (rule.geometryType.empty())
					{
						rule.geometryType = "region";
					}
					rule.direction = "both";
					rule.thresholdMs = std::max<int64_t>(0, std::min<int64_t>(3600000, rule.thresholdMs));
					rule.thresholdCount = 0;
					rule.distanceThresholdPx = 0.0;
					rule.maxSpeedPxPerSec = 0.0;
					rule.maxDisplacementPx = 0.0;
					while (rule.directionAngleDeg < 0.0)
					{
						rule.directionAngleDeg += 360.0;
					}
					while (rule.directionAngleDeg >= 360.0)
					{
						rule.directionAngleDeg -= 360.0;
					}
					rule.directionToleranceDeg = std::max(1.0, std::min(180.0, rule.directionToleranceDeg > 0.0 ? rule.directionToleranceDeg : 30.0));
					rule.subjectObject.clear();
					rule.targetObject.clear();
					rule.relationType.clear();
				}
				else if (rule.behaviorType == "relation_near" || rule.behaviorType == "relation_apart" || rule.behaviorType == "relation_not_contains" || rule.behaviorType == "fight")
				{
					if (rule.geometryType.empty())
					{
						rule.geometryType = "region";
					}
					rule.direction = "both";
					rule.thresholdMs = std::max<int64_t>(0, std::min<int64_t>(3600000,
						rule.thresholdMs > 0 ? rule.thresholdMs : (rule.behaviorType == "fight" ? 800 : 0)));
					rule.thresholdCount = 0;
					if (rule.behaviorType == "relation_not_contains")
					{
						rule.distanceThresholdPx = 0.0;
					}
					else
					{
						rule.distanceThresholdPx = std::max(1.0, std::min(10000.0,
							rule.distanceThresholdPx > 0.0 ? rule.distanceThresholdPx : (rule.behaviorType == "fight" ? 120.0 : 80.0)));
					}
					if (rule.behaviorType == "fight")
					{
						rule.maxSpeedPxPerSec = std::max(0.1, std::min(10000.0, rule.maxSpeedPxPerSec > 0.0 ? rule.maxSpeedPxPerSec : 20.0));
					}
					else
					{
						rule.maxSpeedPxPerSec = 0.0;
					}
					rule.maxDisplacementPx = 0.0;
					rule.directionAngleDeg = 0.0;
					rule.directionToleranceDeg = 30.0;
					rule.ruleObjectCode.clear();
					rule.subjectObject = normalizeObjectClassValue(rule.subjectObject.empty() ? resolveDefaultRuleObjectCode() : rule.subjectObject);
					rule.targetObject = normalizeObjectClassValue(rule.targetObject.empty() && rule.behaviorType == "fight" ? rule.subjectObject : rule.targetObject);
					rule.relationType = normalizeRelationTypeValue(rule.relationType.empty()
						? (rule.behaviorType == "relation_apart" ? "apart" : (rule.behaviorType == "relation_not_contains" ? "not_contains" : "near"))
						: rule.relationType);
				}
				else
				{
					if (rule.geometryType.empty())
					{
						rule.geometryType = "region";
					}
					rule.direction = "both";
					if (rule.behaviorType == "dwell" || rule.behaviorType == "absence" || rule.behaviorType == "occupancy")
					{
						rule.thresholdMs = std::max<int64_t>(1, std::min<int64_t>(3600000, rule.thresholdMs > 0 ? rule.thresholdMs : (dwellThresholdMs > 0 ? dwellThresholdMs : 5000)));
						rule.thresholdCount = 0;
						rule.maxSpeedPxPerSec = 0.0;
						rule.maxDisplacementPx = 0.0;
					}
					else if (rule.behaviorType == "low_speed")
					{
						rule.thresholdMs = std::max<int64_t>(200, std::min<int64_t>(3600000, rule.thresholdMs > 0 ? rule.thresholdMs : 5000));
						rule.thresholdCount = 0;
						rule.maxSpeedPxPerSec = std::max(0.1, std::min(10000.0, rule.maxSpeedPxPerSec > 0.0 ? rule.maxSpeedPxPerSec : 12.0));
						rule.maxDisplacementPx = 0.0;
					}
					else if (rule.behaviorType == "loitering")
					{
						rule.thresholdMs = std::max<int64_t>(1000, std::min<int64_t>(3600000, rule.thresholdMs > 0 ? rule.thresholdMs : 10000));
						rule.thresholdCount = 0;
						rule.maxSpeedPxPerSec = 0.0;
						rule.maxDisplacementPx = std::max(1.0, std::min(10000.0, rule.maxDisplacementPx > 0.0 ? rule.maxDisplacementPx : 80.0));
					}
					else if (rule.behaviorType == "sleep")
					{
						rule.thresholdMs = std::max<int64_t>(1000, std::min<int64_t>(3600000,
							rule.thresholdMs > 0 ? rule.thresholdMs : std::max<int64_t>(15000, dwellThresholdMs > 0 ? dwellThresholdMs : 15000)));
						rule.thresholdCount = 0;
						rule.maxSpeedPxPerSec = std::max(0.1, std::min(10000.0, rule.maxSpeedPxPerSec > 0.0 ? rule.maxSpeedPxPerSec : 6.0));
						rule.maxDisplacementPx = std::max(1.0, std::min(10000.0, rule.maxDisplacementPx > 0.0 ? rule.maxDisplacementPx : 48.0));
						rule.distanceThresholdPx = std::max(0.5, std::min(8.0, rule.distanceThresholdPx > 0.0 ? rule.distanceThresholdPx : 1.2));
					}
					else if (rule.behaviorType == "count_threshold")
					{
						rule.thresholdMs = std::max<int64_t>(0, std::min<int64_t>(3600000, rule.thresholdMs));
						rule.thresholdCount = std::max(1, std::min(100000, rule.thresholdCount > 0 ? rule.thresholdCount : 1));
						rule.maxSpeedPxPerSec = 0.0;
						rule.maxDisplacementPx = 0.0;
					}
					else
					{
						rule.thresholdMs = 0;
						rule.thresholdCount = 0;
						rule.maxSpeedPxPerSec = 0.0;
						rule.maxDisplacementPx = 0.0;
					}
				}
				if (rule.behaviorType != "relation_near" && rule.behaviorType != "relation_apart" && rule.behaviorType != "relation_not_contains" && rule.behaviorType != "fight")
				{
					rule.ruleObjectCode = normalizeObjectClassValue(rule.ruleObjectCode.empty() ? resolveDefaultRuleObjectCode() : rule.ruleObjectCode);
					rule.subjectObject.clear();
					rule.targetObject.clear();
					rule.relationType.clear();
				}
				rule.sequenceId = normalizeSequenceIdValue(rule.sequenceId);
				if (rule.sequenceId.empty() || !isSequenceCapableBehaviorType(rule.behaviorType))
				{
					rule.sequenceId.clear();
					rule.logicMode = "all";
					rule.stageIndex = 0;
					rule.stageTimeoutMs = 0;
					rule.stageHoldMs = 0;
				}
				else
				{
					rule.logicMode = normalizeLogicModeValue(rule.logicMode);
					rule.stageIndex = std::max(0, std::min(32, rule.stageIndex));
					rule.stageTimeoutMs = std::max<int64_t>(0, std::min<int64_t>(3600000, rule.stageTimeoutMs));
					rule.stageHoldMs = std::max<int64_t>(0, std::min<int64_t>(3600000, rule.stageHoldMs));
				}
				if (rule.geometryType == "region" && rule.geometryId.empty())
				{
					const RegionConfig *primaryRegion = findPrimaryRegion();
					if (primaryRegion)
					{
						rule.geometryId = primaryRegion->id;
					}
				}
				if (rule.id.empty())
				{
					rule.id = "behavior_rule_" + std::to_string(i + 1);
				}
				if (rule.name.empty())
				{
					rule.name = rule.behaviorType + "_" + std::to_string(i + 1);
				}
			}

			behaviorRules.erase(std::remove_if(behaviorRules.begin(), behaviorRules.end(), [](const BehaviorRuleConfig &rule) {
				return rule.behaviorType.empty();
			}), behaviorRules.end());

			std::unordered_map<std::string, std::vector<BehaviorRuleConfig *>> sequenceRulesById;
			sequenceRulesById.reserve(behaviorRules.size());
			for (size_t i = 0; i < behaviorRules.size(); ++i)
			{
				BehaviorRuleConfig &rule = behaviorRules[i];
				if (!rule.sequenceId.empty())
				{
					sequenceRulesById[rule.sequenceId].push_back(&rule);
				}
			}

			for (auto &entry : sequenceRulesById)
			{
				std::vector<BehaviorRuleConfig *> &rules = entry.second;
				std::sort(rules.begin(), rules.end(), [](const BehaviorRuleConfig *lhs, const BehaviorRuleConfig *rhs) {
					if (lhs->stageIndex != rhs->stageIndex)
					{
						return lhs->stageIndex < rhs->stageIndex;
					}
					return lhs->id < rhs->id;
				});

				int normalizedStageIndex = -1;
				int currentRawStageIndex = std::numeric_limits<int>::min();
				std::string currentLogicMode = "all";
				for (size_t i = 0; i < rules.size(); ++i)
				{
					BehaviorRuleConfig *rule = rules[i];
					if (!rule)
					{
						continue;
					}
					if (rule->stageIndex != currentRawStageIndex)
					{
						currentRawStageIndex = rule->stageIndex;
						currentLogicMode = rule->logicMode.empty() ? "all" : rule->logicMode;
						++normalizedStageIndex;
					}
					rule->stageIndex = normalizedStageIndex;
					rule->logicMode = currentLogicMode;
				}
			}
		}

		const std::vector<double> &getPrimaryRegionPolygonD() const
		{
			const RegionConfig *primaryRegion = findPrimaryRegion();
			if (primaryRegion && primaryRegion->polygon_d.size() >= 8)
			{
				return primaryRegion->polygon_d;
			}
			return recognitionRegion_d;
		}

		void syncLegacyRecognitionRegionFromPrimaryNormalized()
		{
			const RegionConfig *primaryRegion = findPrimaryRegion();
			if (primaryRegion)
			{
				recognitionRegion = joinNormalizedPoints(primaryRegion->normalizedPoints);
			}
		}

		void clearParsedGeometry()
		{
			recognitionRegion_d.clear();
			recognitionRegion_points.clear();
			for (size_t i = 0; i < regions.size(); ++i)
			{
				regions[i].polygon_d.clear();
				regions[i].polygon_points.clear();
			}
			for (size_t i = 0; i < lines.size(); ++i)
			{
				lines[i].points.clear();
			}
		}

		bool parseRecognitionRegion()
		{
			clearParsedGeometry();
			normalizeGeometryConfig();
			if (videoWidth <= 0 || videoHeight <= 0)
			{
				return false;
			}

			for (size_t i = 0; i < regions.size(); ++i)
			{
				RegionConfig &region = regions[i];
				if (!region.hasValidNormalizedPolygon())
				{
					continue;
				}
				for (size_t pointIndex = 0; pointIndex + 1 < region.normalizedPoints.size(); pointIndex += 2)
				{
					const int x = static_cast<int>(region.normalizedPoints[pointIndex] * videoWidth);
					const int y = static_cast<int>(region.normalizedPoints[pointIndex + 1] * videoHeight);
					region.polygon_d.push_back(x);
					region.polygon_d.push_back(y);
					region.polygon_points.push_back(cv::Point(x, y));
				}
			}

			for (size_t i = 0; i < lines.size(); ++i)
			{
				LineConfig &line = lines[i];
				if (!line.hasValidNormalizedLine())
				{
					continue;
				}
				for (size_t pointIndex = 0; pointIndex + 1 < line.normalizedPoints.size(); pointIndex += 2)
				{
					const int x = static_cast<int>(line.normalizedPoints[pointIndex] * videoWidth);
					const int y = static_cast<int>(line.normalizedPoints[pointIndex + 1] * videoHeight);
					line.points.push_back(cv::Point(x, y));
				}
			}

			const RegionConfig *primaryRegion = findPrimaryRegion();
			if (!primaryRegion || primaryRegion->polygon_d.size() < 8 || primaryRegion->polygon_points.size() < 4)
			{
				return false;
			}

			recognitionRegion_d = primaryRegion->polygon_d;
			recognitionRegion_points = primaryRegion->polygon_points;
			recognitionRegion = joinNormalizedPoints(primaryRegion->normalizedPoints);
			return true;
		}
		bool validateAdd(std::string &result_msg)
		{
			normalizeGeometryConfig();
			if (code.empty() || streamUrl.empty() || !findPrimaryRegion())
			{
				result_msg = "validate parameter error";
				return false;
			}

			if (algorithmTasks.empty())
			{
				objectCodes = normalizeObjectClassValues(objectCodes);
				if (objectCodes.empty() && !objectCode.empty())
				{
					objectCodes.push_back(normalizeObjectClassValue(objectCode));
				}
				if (algorithmCode.empty() || objectCodes.empty())
				{
					result_msg = "validate parameter algorithm error";
					return false;
				}

				AlgorithmTask task;
				task.algorithmCode = algorithmCode;
				task.object_str = object_str;
				task.objects_v1 = objects_v1;
				task.objects_v1_len = static_cast<int>(objects_v1.size());
				task.objectCodes = objectCodes;
				task.objectCode = task.getPrimaryObjectCode();
				task.detectFps = detectFps;
				algorithmTasks.push_back(task);
			}
			else
			{
				for (size_t i = 0; i < algorithmTasks.size(); ++i)
				{
					AlgorithmTask &task = algorithmTasks[i];
					task.objectCodes = normalizeObjectClassValues(task.objectCodes);
					if (task.objectCodes.empty() && !task.objectCode.empty())
					{
						task.objectCodes.push_back(normalizeObjectClassValue(task.objectCode));
					}
					task.objectCode = task.getPrimaryObjectCode();
					if (task.algorithmCode.empty() || task.objectCodes.empty())
					{
						result_msg = "validate parameter algorithmTasks error";
						return false;
					}
					if (task.objects_v1_len == 0)
					{
						task.objects_v1_len = static_cast<int>(task.objects_v1.size());
					}
					if (task.detectFps < -2.0f)
					{
						task.detectFps = -2.0f;
					}
					if (task.detectFps > 30.0f)
					{
						task.detectFps = 30.0f;
					}
					if (task.scoreThresholdSet)
					{
						if (task.scoreThreshold < 0.0f)
						{
							task.scoreThreshold = 0.0f;
						}
						if (task.scoreThreshold > 1.0f)
						{
							task.scoreThreshold = 1.0f;
						}
					}
					if (task.nmsThresholdSet)
					{
						if (task.nmsThreshold < 0.0f)
						{
							task.nmsThreshold = 0.0f;
						}
						if (task.nmsThreshold > 1.0f)
						{
							task.nmsThreshold = 1.0f;
						}
					}
				}

				// 旧字段保持可读，取首个任务作为展示与兼容输出
				algorithmCode = algorithmTasks[0].algorithmCode;
				object_str = algorithmTasks[0].object_str;
				objects_v1 = algorithmTasks[0].objects_v1;
				objects_v1_len = algorithmTasks[0].objects_v1_len;
				objectCodes = algorithmTasks[0].objectCodes;
				objectCode = algorithmTasks[0].getPrimaryObjectCode();
				detectFps = algorithmTasks[0].detectFps;
			}
			if (pushStream)
			{
				if (pushStreamUrl.empty())
				{
					result_msg = "validate parameter pushStreamUrl is error: " + pushStreamUrl;
					return false;
				}
			}

			if (!pushEncoder.empty())
			{
				std::transform(pushEncoder.begin(), pushEncoder.end(), pushEncoder.begin(), [](unsigned char c) {
					return static_cast<char>(std::tolower(c));
				});
			}
			if (pushEncoder != "auto" && pushEncoder != "libx264" && pushEncoder != "h264_nvenc")
			{
				pushEncoder = "auto";
			}

			if (wsFrameDebounceMs < 0)
			{
				wsFrameDebounceMs = 0;
			}
			if (wsFrameDebounceMs > 2000)
			{
				wsFrameDebounceMs = 2000;
			}

			if (wsPostRetryMax < 0)
			{
				wsPostRetryMax = 0;
			}
			if (wsPostRetryMax > 5)
			{
				wsPostRetryMax = 5;
			}

			if (wsPostFailOpenThreshold < 0)
			{
				wsPostFailOpenThreshold = 0;
			}
			if (wsPostFailOpenThreshold > 100)
			{
				wsPostFailOpenThreshold = 100;
			}

			if (wsPostCooldownMs < 0)
			{
				wsPostCooldownMs = 0;
			}
			if (wsPostCooldownMs > 30000)
			{
				wsPostCooldownMs = 30000;
			}

			if (dwellThresholdMs < 0)
			{
				dwellThresholdMs = 0;
			}
			if (dwellThresholdMs > 3600000)
			{
				dwellThresholdMs = 3600000;
			}
			if (dwellEnabled && dwellThresholdMs <= 0)
			{
				dwellThresholdMs = 5000;
			}

			normalizeBehaviorRulesConfig();

			if (wsEventRuleMode != "any" && wsEventRuleMode != "all_algorithms_per_class" && wsEventRuleMode != "all_algorithms_any_class")
			{
				wsEventRuleMode = "any";
			}

			if (wsEventMinHits < 1)
			{
				wsEventMinHits = 1;
			}
			if (wsEventMinHits > 20)
			{
				wsEventMinHits = 20;
			}

			if (wsEventHitWindowMs < 200)
			{
				wsEventHitWindowMs = 200;
			}
			if (wsEventHitWindowMs > 60000)
			{
				wsEventHitWindowMs = 60000;
			}

			if (wsEventPendingTimeoutMs < 500)
			{
				wsEventPendingTimeoutMs = 500;
			}
			if (wsEventPendingTimeoutMs > 120000)
			{
				wsEventPendingTimeoutMs = 120000;
			}

			if (wsEventRestartCooldownMs < 0)
			{
				wsEventRestartCooldownMs = 0;
			}
			if (wsEventRestartCooldownMs > 120000)
			{
				wsEventRestartCooldownMs = 120000;
			}

			wsEventRequiredAlgorithms.clear();
			if (!wsEventRequiredAlgorithmsStr.empty())
			{
				std::vector<std::string> parts = split(wsEventRequiredAlgorithmsStr, ",");
				for (size_t i = 0; i < parts.size(); ++i)
				{
					std::string item = parts[i];
					item.erase(std::remove_if(item.begin(), item.end(), [](unsigned char ch) {
						return std::isspace(ch);
					}), item.end());
					if (!item.empty())
					{
						wsEventRequiredAlgorithms.push_back(item);
					}
				}
			}
			result_msg = "validate success";
			return true;
		}
		bool validateCancel(std::string &result_msg)
		{

			if (code.empty())
			{
				result_msg = "validate parameter error";
				return false;
			}
			result_msg = "validate success";
			return true;
		}
	};
}
#endif // ANALYZER_CONTROL_H