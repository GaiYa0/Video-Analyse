#ifndef ANALYZER_BEHAVIOREVALUATOR_H
#define ANALYZER_BEHAVIOREVALUATOR_H

#include <string>

namespace SVAAnalyzer
{
    struct Control;
    struct DetectObject;

    /**
     * @brief 单个行为规则评估的结果
     * 
     * 每个 DetectObject 在每帧被评估时，返回第一个匹配的行为规则结果。
     * 如果没有规则匹配，则 matched = false。
     * 
     * 教学注意：这是一个"首次匹配即返回"的策略 —— 如果同一目标触发多条规则，
     * 只返回第一条。这是权衡性能和准确性的设计选择。
     */
    struct BehaviorDecision
    {
        bool matched = false;
        std::string ruleId;            // 规则ID（如 "behavior_rule_1"）
        std::string customEventName;   // 自定义事件名（业务侧命名）
        std::string behaviorType;      // 行为类型（cross_line, dwell, ...）
        std::string regionId;          // 相关区域ID
        std::string regionName;        // 相关区域名称
        std::string lineId;            // 相关线段ID（仅 cross_line）
        std::string lineName;          // 相关线段名称
        std::string crossingDirection; // 穿越方向（left_to_right/right_to_left）
        double directionAngleDeg = 0.0; // 匹配到的运动方向角度
    };

    /**
     * @brief 检查布控是否启用了行为分析模式
     * 
     * 当为 true 时，Analyzer::applyRegionAndObjectMatch() 会跳过经典的
     * "objectCode 精确匹配 + region IoU" 逻辑，转而只在区域过滤后让所有
     * 检测结果进入行为分析流程。
     * 
     * 行为模式激活条件（任一即触发）：
     * - behaviorRules 中有 enabled 的规则
     * - dwellEnabled = true
     * - lines 中有有效的线段（用于跨线检测）
     */
    bool controlUsesBehaviorOnlyMode(const Control &control);

    /**
     * @brief 核心入口：评估一个 DetectObject 在某个布控下的行为
     * 
     * ## 调用链
     * Worker::handleDecodeVideo() 
     *   → TemporalProcessor::updateStream()   // 先做追踪
     *   → evaluateAtomicBehavior()            // 再做行为判断
     * 
     * ## 支持的行为类型
     * | 类型             | 说明                         | 需要的追踪数据     |
     * |-----------------|------------------------------|-------------------|
     * | cross_line      | 穿越指定线段                  | trail (轨迹历史)   |
     * | enter_region    | 进入指定区域                  | regionStates       |
     * | exit_region     | 离开指定区域                  | regionStates       |
     * | dwell           | 在区域内停留超过阈值           | regionStates.inRegionDurationMs |
     * | low_speed       | 在区域内低速移动               | trail + speed     |
     * | loitering       | 在区域内小范围徘徊             | trail             |
     * | sleep           | 静止 + 宽高比异常（躺卧）      | motionState + box size |
     * | direction_move  | 运动方向匹配指定角度           | trail + speed     |
     * | direction_reverse| 运动方向与指定角度相反        | trail + speed     |
     * 
     * @param control 布控配置（包含 behaviorRules、lines、regions）
     * @param detect  增强后的检测对象（已有时态追踪数据）
     * @return 第一个匹配的规则结果
     */
    BehaviorDecision evaluateAtomicBehavior(const Control &control, const DetectObject &detect);
}

#endif // ANALYZER_BEHAVIOREVALUATOR_H
