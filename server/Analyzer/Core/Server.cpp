#include "Server.h"

#ifdef WIN32
#pragma comment(lib, "ws2_32.lib")
#include <WinSock2.h>
#include <WS2tcpip.h>
#endif

#include <event2/event.h>
#include <event2/http.h>
#include <event2/buffer.h>
#include <event2/http_struct.h>
#include <json/json.h>
#include <json/value.h>
#include <thread>
#include "Control.h"
#include "Config.h"
#include "Scheduler.h"
#include "Utils/Log.h"
#include "Utils/Common.h"

using namespace SVAAnalyzer;

#define RECV_BUF_MAX_SIZE 1024 * 8

namespace
{
    bool tryParseJsonTextNode(const std::string &text, Json::Value &out)
    {
        if (text.empty())
        {
            return false;
        }
        Json::CharReaderBuilder builder;
        const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
        JSONCPP_STRING errs;
        return reader->parse(text.data(), text.data() + text.size(), &out, &errs) && errs.empty();
    }

    Json::Value resolveMaybeJsonNode(const Json::Value &value)
    {
        if (value.isString())
        {
            Json::Value parsed;
            if (tryParseJsonTextNode(value.asString(), parsed))
            {
                return parsed;
            }
        }
        return value;
    }
}

Server::Server()
{
#ifdef WIN32
    WSADATA wdSockMsg;
    int s = WSAStartup(MAKEWORD(2, 2), &wdSockMsg);

    if (0 != s)
    {
        switch (s)
        {
        case WSASYSNOTREADY:
            printf("重启电脑，或者检查网络库");
            break;
        case WSAVERNOTSUPPORTED:
            printf("请更新网络库");
            break;
        case WSAEINPROGRESS:
            printf("请重新启动");
            break;
        case WSAEPROCLIM:
            printf("请关闭不必要的软件，以确保有足够的网络资源");
            break;
        }
    }

    if (2 != HIBYTE(wdSockMsg.wVersion) || 2 != LOBYTE(wdSockMsg.wVersion))
    {
        LOGE("网络库版本错误");
        return;
    }
#endif
}
Server::~Server()
{
    LOGE("");
#ifdef WIN32
    WSACleanup();
#endif
}

void Server::start(void *arg)
{
    Scheduler *scheduler = (Scheduler *)arg;
    scheduler->setState(true);

    std::thread([](Scheduler *scheduler)
                {
                    LOGI("启动分析器服务：http://0.0.0.0:%d", scheduler->getConfig()->analyzerPort);

                    event_config *evt_config = event_config_new();
                    struct event_base *base = event_base_new_with_config(evt_config);
                    struct evhttp *http = evhttp_new(base);
                    evhttp_set_default_content_type(http, "text/html; charset=utf-8");

                    evhttp_set_timeout(http, 0);
                    // 设置路由
                    evhttp_set_cb(http, "/", api_index, nullptr);
                    evhttp_set_cb(http, "/api/health", api_health, scheduler);
                    evhttp_set_cb(http, "/api/controls", api_controls, scheduler);
                    evhttp_set_cb(http, "/api/control", api_control, scheduler);
                    evhttp_set_cb(http, "/api/control/add", api_control_add, scheduler);
                    evhttp_set_cb(http, "/api/control/cancel", api_control_cancel, scheduler);
                    evhttp_set_cb(http, "/api/alarm/bind-media", api_alarm_bind_media, scheduler);

                    evhttp_bind_socket(http, "0.0.0.0",
                                       scheduler->getConfig()->analyzerPort);
                    event_base_dispatch(base);

                    event_base_free(base);
                    evhttp_free(http);
                    event_config_free(evt_config);

                    scheduler->setState(false); },
                scheduler)
        .detach();
}

void api_index(struct evhttp_request *req, void *arg)
{

    Json::Value result_urls;
    result_urls["/api"] = "this api version 1.0";
    result_urls["/api/health"] = "check health";
    result_urls["/api/controls"] = "get all control being analyzed";
    result_urls["/api/control"] = "get control being analyzed";
    result_urls["/api/control/add"] = "add control";
    result_urls["/api/control/cancel"] = "cancel control";
    result_urls["/api/alarm/bind-media"] = "bind backend alarm metadata to generated media";
    result_urls["/api/largeModelCalcu"] = "largeModelCalcu";

    Json::Value result;
    result["urls"] = result_urls;

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}
void api_health(struct evhttp_request *req, void *arg)
{
    Scheduler *scheduler = (Scheduler *)arg;
    int result_code = 0;
    std::string result_msg = "error";

    // 健康检测
    result_code = 1000;
    result_msg = "current service health";

    Json::Value result;
    result["msg"] = result_msg;
    result["code"] = result_code;

    if (scheduler)
    {
        int queueSize = 0;
        int64_t enqueued = 0;
        int64_t dropped = 0;
        int64_t sent = 0;
        int64_t postFailed = 0;
        int64_t queuePeak = 0;
        int64_t activeEvents = 0;
        int64_t pendingEvents = 0;
        int64_t pendingPeak = 0;
        int64_t pendingEvicted = 0;
        int64_t lifecycleStarted = 0;
        int64_t lifecycleEnded = 0;
        int64_t blockedByRule = 0;
        int64_t blockedByMinHits = 0;
        int64_t blockedByRestartCooldown = 0;
        int64_t debounced = 0;
        int64_t retried = 0;
        int64_t eventSent = 0;
        int64_t eventPostFailed = 0;
        int64_t eventRetried = 0;
        int64_t postSkipped = 0;
        int64_t postCooldownActive = 0;
        int64_t postCircuitStreams = 0;
        int64_t postConsecutiveFailedTotal = 0;
        int64_t postConsecutiveFailedMax = 0;
        int64_t postCooldownMaxRemainMs = 0;
         scheduler->getDetectFrameStats(queueSize, enqueued, dropped, sent, postFailed, queuePeak, activeEvents, pendingEvents, pendingPeak,
             pendingEvicted, lifecycleStarted, lifecycleEnded, blockedByRule, blockedByMinHits, blockedByRestartCooldown, debounced, retried,
               eventSent, eventPostFailed, eventRetried, postSkipped, postCooldownActive,
               postCircuitStreams, postConsecutiveFailedTotal, postConsecutiveFailedMax, postCooldownMaxRemainMs);

        Json::Value metrics;
        metrics["detectFrameQueueSize"] = queueSize;
        metrics["detectFrameEnqueued"] = static_cast<Json::Int64>(enqueued);
        metrics["detectFrameDropped"] = static_cast<Json::Int64>(dropped);
        metrics["detectFrameSent"] = static_cast<Json::Int64>(sent);
        metrics["detectFramePostFailed"] = static_cast<Json::Int64>(postFailed);
        metrics["detectFrameQueuePeak"] = static_cast<Json::Int64>(queuePeak);
        metrics["detectFrameDebounced"] = static_cast<Json::Int64>(debounced);
        metrics["detectFrameRetried"] = static_cast<Json::Int64>(retried);
        metrics["detectEventSent"] = static_cast<Json::Int64>(eventSent);
        metrics["detectEventPostFailed"] = static_cast<Json::Int64>(eventPostFailed);
        metrics["detectEventRetried"] = static_cast<Json::Int64>(eventRetried);
        metrics["detectPostSkipped"] = static_cast<Json::Int64>(postSkipped);
        metrics["detectPostCooldownActive"] = static_cast<Json::Int64>(postCooldownActive);
        metrics["detectPostCircuitStreams"] = static_cast<Json::Int64>(postCircuitStreams);
        metrics["detectPostConsecutiveFailedTotal"] = static_cast<Json::Int64>(postConsecutiveFailedTotal);
        metrics["detectPostConsecutiveFailedMax"] = static_cast<Json::Int64>(postConsecutiveFailedMax);
        metrics["detectPostCooldownMaxRemainMs"] = static_cast<Json::Int64>(postCooldownMaxRemainMs);
        metrics["detectLifecycleActive"] = static_cast<Json::Int64>(activeEvents);
        metrics["detectLifecyclePending"] = static_cast<Json::Int64>(pendingEvents);
        metrics["detectLifecyclePendingPeak"] = static_cast<Json::Int64>(pendingPeak);
        metrics["detectLifecyclePendingEvicted"] = static_cast<Json::Int64>(pendingEvicted);
        metrics["detectLifecycleStarted"] = static_cast<Json::Int64>(lifecycleStarted);
        metrics["detectLifecycleEnded"] = static_cast<Json::Int64>(lifecycleEnded);
        metrics["detectLifecycleBlockedByRule"] = static_cast<Json::Int64>(blockedByRule);
        metrics["detectLifecycleBlockedByMinHits"] = static_cast<Json::Int64>(blockedByMinHits);
        metrics["detectLifecycleBlockedByRestartCooldown"] = static_cast<Json::Int64>(blockedByRestartCooldown);
        result["metrics"] = metrics;
    }

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}
void api_controls(struct evhttp_request *req, void *arg)
{

    Scheduler *scheduler = (Scheduler *)arg;
    char buf[RECV_BUF_MAX_SIZE];
    parse_post(req, buf);

    Json::CharReaderBuilder builder;
    const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    Json::Value root;
    JSONCPP_STRING errs;

    Json::Value result_data;
    Json::Value result_data_item;
    int result_code = 0;
    std::string result_msg = "error";
    Json::Value result;

    if (reader->parse(buf, buf + std::strlen(buf), &root, &errs) && errs.empty())
    {

        std::vector<Control *> controls;
        int len = scheduler->apiControls(controls);

        if (len > 0)
        {
            int64_t curTimestamp = getCurTimestamp();
            int64_t startTimestamp = 0;
            for (size_t i = 0; i < controls.size(); ++i)
            {
                startTimestamp = controls[i]->startTimestamp;

                result_data_item["code"] = controls[i]->code.data();
                result_data_item["streamUrl"] = controls[i]->streamUrl.data();

                result_data_item["pushStream"] = controls[i]->pushStream;
                result_data_item["pushStreamUrl"] = controls[i]->pushStreamUrl.data();
                result_data_item["renderMode"] = controls[i]->renderMode.data();
                result_data_item["serverOverlayEnabled"] = controls[i]->serverOverlayEnabled;
                result_data_item["wsOverlayEnabled"] = controls[i]->wsOverlayEnabled;
                result_data_item["wsEventFps"] = controls[i]->wsEventFps;
                result_data_item["wsEventKeyMode"] = controls[i]->wsEventKeyMode;
                result_data_item["wsEventUpdateIntervalMs"] = controls[i]->wsEventUpdateIntervalMs;
                result_data_item["wsEventEndTimeoutMs"] = controls[i]->wsEventEndTimeoutMs;
                result_data_item["wsEventRuleMode"] = controls[i]->wsEventRuleMode;
                result_data_item["wsEventRequiredAlgorithms"] = controls[i]->wsEventRequiredAlgorithmsStr;
                result_data_item["wsEventMinHits"] = controls[i]->wsEventMinHits;
                result_data_item["wsEventHitWindowMs"] = controls[i]->wsEventHitWindowMs;
                result_data_item["wsEventPendingTimeoutMs"] = controls[i]->wsEventPendingTimeoutMs;
                result_data_item["wsEventRestartCooldownMs"] = controls[i]->wsEventRestartCooldownMs;
                result_data_item["wsFrameDebounceMs"] = controls[i]->wsFrameDebounceMs;
                result_data_item["wsPostRetryMax"] = controls[i]->wsPostRetryMax;
                result_data_item["wsPostFailOpenThreshold"] = controls[i]->wsPostFailOpenThreshold;
                result_data_item["wsPostCooldownMs"] = controls[i]->wsPostCooldownMs;
                result_data_item["algorithmCode"] = controls[i]->algorithmCode.data();
                result_data_item["objectCode"] = controls[i]->objectCode.data();
                result_data_item["recognitionRegion"] = controls[i]->recognitionRegion.data();

                result_data_item["checkFps"] = controls[i]->checkFps;
                result_data_item["detectFps"] = controls[i]->detectFps;
                result_data_item["startTimestamp"] = startTimestamp;
                result_data_item["liveMilliseconds"] = curTimestamp - startTimestamp;

                result_data.append(result_data_item);
            }
            result["data"] = result_data;
            result_code = 1000;
            result_msg = "success";
        }
        else
        {
            result_msg = "the number of control exector is empty";
        }
    }
    else
    {
        result_msg = "invalid request parameter";
    }
    result["msg"] = result_msg;
    result["code"] = result_code;

    // LOGI("\n \t request:%s \n \t response:%s", root.toStyledString().data(), result.toStyledString().data());

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}
void api_control(struct evhttp_request *req, void *arg)
{

    Scheduler *scheduler = (Scheduler *)arg;
    char buf[RECV_BUF_MAX_SIZE];
    parse_post(req, buf);

    Json::CharReaderBuilder builder;
    const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    Json::Value root;
    JSONCPP_STRING errs;

    Json::Value result_control;
    int result_code = 0;
    std::string result_msg = "error";

    if (reader->parse(buf, buf + std::strlen(buf), &root, &errs) && errs.empty())
    {

        Control *control = NULL;
        if (root["code"].isString())
        {
            std::string code = root["code"].asCString();
            control = scheduler->apiControl(code);
        }

        if (control)
        {
            result_control["code"] = control->code;
            result_control["checkFps"] = control->checkFps;
            result_control["detectFps"] = control->detectFps;

            result_code = 1000;
            result_msg = "success";
        }
        else
        {
            result_msg = "the control does not exist";
        }
    }
    else
    {
        result_msg = "invalid request parameter";
    }

    Json::Value result;
    result["control"] = result_control;
    result["msg"] = result_msg;
    result["code"] = result_code;

    LOGI("\n \t request:%s \n \t response:%s", root.toStyledString().data(), result.toStyledString().data());

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}
void api_control_add(struct evhttp_request *req, void *arg)
{

    Scheduler *scheduler = (Scheduler *)arg;
    char buf[RECV_BUF_MAX_SIZE];
    parse_post(req, buf);

    Json::CharReaderBuilder builder;
    const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    Json::Value root;
    JSONCPP_STRING errs;

    int result_code = 0;
    std::string result_msg = "error";

    if (reader->parse(buf, buf + std::strlen(buf), &root, &errs) && errs.empty())
    {
        auto tryParseFloat = [](const std::string &text, float &out) -> bool {
            if (text.empty())
            {
                return false;
            }
            try
            {
                out = std::stof(text);
                return true;
            }
            catch (...)
            {
                return false;
            }
        };
        auto tryParseInt = [](const std::string &text, int &out) -> bool {
            if (text.empty())
            {
                return false;
            }
            try
            {
                out = std::stoi(text);
                return true;
            }
            catch (...)
            {
                return false;
            }
        };
        auto parseStringList = [](const Json::Value &value) -> std::vector<std::string> {
            std::vector<std::string> values;
            Json::Value resolved = resolveMaybeJsonNode(value);
            if (resolved.isArray())
            {
                for (Json::ArrayIndex i = 0; i < resolved.size(); ++i)
                {
                    if (resolved[i].isString())
                    {
                        values.push_back(resolved[i].asString());
                    }
                    else if (resolved[i].isNumeric())
                    {
                        values.push_back(resolved[i].asString());
                    }
                }
            }
            else if (resolved.isString())
            {
                values = split(resolved.asString(), ",");
            }
            return Control::normalizeObjectClassValues(values);
        };

        Control control;

        control.code = root["code"].asCString();

        control.streamCode = root["streamCode"].asString();
        control.streamApp = root["streamApp"].asString();
        control.streamName = root["streamName"].asString();
        control.streamUrl = root["streamUrl"].asString();
        control.pushStream = root["pushStream"].asBool();
        control.pushStreamUrl = root["pushStreamUrl"].asString();
        if (root["renderMode"].isString())
        {
            control.renderMode = root["renderMode"].asString();
        }
        if (root["serverOverlayEnabled"].isBool())
        {
            control.serverOverlayEnabled = root["serverOverlayEnabled"].asBool();
        }
        if (root["wsOverlayEnabled"].isBool())
        {
            control.wsOverlayEnabled = root["wsOverlayEnabled"].asBool();
        }
        if (root["saveImageEnabled"].isBool())
        {
            control.saveImageEnabled = root["saveImageEnabled"].asBool();
        }
        if (root["saveVideoEnabled"].isBool())
        {
            control.saveVideoEnabled = root["saveVideoEnabled"].asBool();
        }
        if (root["wsEventFps"].isNumeric())
        {
            control.wsEventFps = root["wsEventFps"].asFloat();
        }
        else if (root["wsEventFps"].isString() && !root["wsEventFps"].asString().empty())
        {
            float value = 0.0f;
            if (tryParseFloat(root["wsEventFps"].asString(), value))
            {
                control.wsEventFps = value;
            }
        }

        if (control.wsEventFps < 0.0f)
        {
            control.wsEventFps = 0.0f;
        }
        if (control.wsEventFps > 30.0f)
        {
            control.wsEventFps = 30.0f;
        }

        if (root["wsEventKeyMode"].isString())
        {
            control.wsEventKeyMode = root["wsEventKeyMode"].asString();
        }
        if (control.wsEventKeyMode != "control" && control.wsEventKeyMode != "class" && control.wsEventKeyMode != "class_algorithm")
        {
            control.wsEventKeyMode = "control";
        }

        if (root["wsEventUpdateIntervalMs"].isInt())
        {
            control.wsEventUpdateIntervalMs = root["wsEventUpdateIntervalMs"].asInt();
        }
        else if (root["wsEventUpdateIntervalMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventUpdateIntervalMs"].asString(), value))
            {
                control.wsEventUpdateIntervalMs = value;
            }
        }
        if (root["wsEventEndTimeoutMs"].isInt())
        {
            control.wsEventEndTimeoutMs = root["wsEventEndTimeoutMs"].asInt();
        }
        else if (root["wsEventEndTimeoutMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventEndTimeoutMs"].asString(), value))
            {
                control.wsEventEndTimeoutMs = value;
            }
        }
        if (root["wsEventRuleMode"].isString())
        {
            control.wsEventRuleMode = root["wsEventRuleMode"].asString();
        }
        if (root["wsEventRequiredAlgorithms"].isString())
        {
            control.wsEventRequiredAlgorithmsStr = root["wsEventRequiredAlgorithms"].asString();
        }
        else if (root["wsEventRequiredAlgorithms"].isArray())
        {
            std::string joined;
            for (Json::ArrayIndex i = 0; i < root["wsEventRequiredAlgorithms"].size(); ++i)
            {
                const Json::Value &item = root["wsEventRequiredAlgorithms"][i];
                if (!item.isString())
                {
                    continue;
                }
                const std::string s = item.asString();
                if (s.empty())
                {
                    continue;
                }
                if (!joined.empty())
                {
                    joined += ",";
                }
                joined += s;
            }
            control.wsEventRequiredAlgorithmsStr = joined;
        }
        if (root["wsEventMinHits"].isInt())
        {
            control.wsEventMinHits = root["wsEventMinHits"].asInt();
        }
        else if (root["wsEventMinHits"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventMinHits"].asString(), value))
            {
                control.wsEventMinHits = value;
            }
        }
        if (root["wsEventHitWindowMs"].isInt())
        {
            control.wsEventHitWindowMs = root["wsEventHitWindowMs"].asInt();
        }
        else if (root["wsEventHitWindowMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventHitWindowMs"].asString(), value))
            {
                control.wsEventHitWindowMs = value;
            }
        }
        if (root["wsEventPendingTimeoutMs"].isInt())
        {
            control.wsEventPendingTimeoutMs = root["wsEventPendingTimeoutMs"].asInt();
        }
        else if (root["wsEventPendingTimeoutMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventPendingTimeoutMs"].asString(), value))
            {
                control.wsEventPendingTimeoutMs = value;
            }
        }
        if (root["wsEventRestartCooldownMs"].isInt())
        {
            control.wsEventRestartCooldownMs = root["wsEventRestartCooldownMs"].asInt();
        }
        else if (root["wsEventRestartCooldownMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsEventRestartCooldownMs"].asString(), value))
            {
                control.wsEventRestartCooldownMs = value;
            }
        }
        if (root["wsFrameDebounceMs"].isInt())
        {
            control.wsFrameDebounceMs = root["wsFrameDebounceMs"].asInt();
        }
        else if (root["wsFrameDebounceMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsFrameDebounceMs"].asString(), value))
            {
                control.wsFrameDebounceMs = value;
            }
        }
        if (root["wsPostRetryMax"].isInt())
        {
            control.wsPostRetryMax = root["wsPostRetryMax"].asInt();
        }
        else if (root["wsPostRetryMax"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsPostRetryMax"].asString(), value))
            {
                control.wsPostRetryMax = value;
            }
        }
        if (root["wsPostFailOpenThreshold"].isInt())
        {
            control.wsPostFailOpenThreshold = root["wsPostFailOpenThreshold"].asInt();
        }
        else if (root["wsPostFailOpenThreshold"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsPostFailOpenThreshold"].asString(), value))
            {
                control.wsPostFailOpenThreshold = value;
            }
        }
        if (root["wsPostCooldownMs"].isInt())
        {
            control.wsPostCooldownMs = root["wsPostCooldownMs"].asInt();
        }
        else if (root["wsPostCooldownMs"].isString())
        {
            int value = 0;
            if (tryParseInt(root["wsPostCooldownMs"].asString(), value))
            {
                control.wsPostCooldownMs = value;
            }
        }
        control.wsEventUpdateIntervalMs = std::max(200, control.wsEventUpdateIntervalMs);
        control.wsEventEndTimeoutMs = std::max(500, control.wsEventEndTimeoutMs);
        control.wsFrameDebounceMs = std::max(0, std::min(2000, control.wsFrameDebounceMs));
        control.wsPostRetryMax = std::max(0, std::min(5, control.wsPostRetryMax));
        control.wsPostFailOpenThreshold = std::max(0, std::min(100, control.wsPostFailOpenThreshold));
        control.wsPostCooldownMs = std::max(0, std::min(30000, control.wsPostCooldownMs));
        control.wsEventMinHits = std::max(1, std::min(20, control.wsEventMinHits));
        control.wsEventHitWindowMs = std::max(200, std::min(60000, control.wsEventHitWindowMs));
        control.wsEventPendingTimeoutMs = std::max(500, std::min(120000, control.wsEventPendingTimeoutMs));
        control.wsEventRestartCooldownMs = std::max(0, std::min(120000, control.wsEventRestartCooldownMs));
        if (control.wsEventRuleMode != "any" && control.wsEventRuleMode != "all_algorithms_per_class" && control.wsEventRuleMode != "all_algorithms_any_class")
        {
            control.wsEventRuleMode = "any";
        }

        if (control.renderMode == "ws_overlay")
        {
            control.wsOverlayEnabled = true;
            if (!root["serverOverlayEnabled"].isBool())
            {
                control.serverOverlayEnabled = false;
            }
        }

        control.algorithmCode = root["algorithmCode"].asString();
        control.api_url = root["api_url"].asString();
        control.object_str = root["object_str"].asString();
        control.objects_v1 = split(control.object_str, ",");
        control.objects_v1_len = control.objects_v1.size();
        control.objectCode = root["objectCode"].asString();
        control.objectCodes = parseStringList(root["objectCodes"]);
        if (control.objectCodes.empty() && root["objectCode"].isString())
        {
            control.objectCodes.push_back(Control::normalizeObjectClassValue(root["objectCode"].asString()));
        }
        control.objectCodes = Control::normalizeObjectClassValues(control.objectCodes);
        control.objectCode = control.getPrimaryObjectCode();
        control.recognitionRegion = root["recognitionRegion"].asString();

        if (root["algorithmTasks"].isArray())
        {
            for (Json::ArrayIndex i = 0; i < root["algorithmTasks"].size(); ++i)
            {
                Json::Value item = root["algorithmTasks"][i];
                AlgorithmTask task;

                if (item["algorithmCode"].isString())
                {
                    task.algorithmCode = item["algorithmCode"].asString();
                }
                if (item["object_str"].isString())
                {
                    task.object_str = item["object_str"].asString();
                }
                if (item["objectCode"].isString())
                {
                    task.objectCode = item["objectCode"].asString();
                }
                task.objectCodes = parseStringList(item["objectCodes"]);
                if (task.objectCodes.empty() && item["objectCode"].isString())
                {
                    task.objectCodes.push_back(Control::normalizeObjectClassValue(item["objectCode"].asString()));
                }
                task.objectCodes = Control::normalizeObjectClassValues(task.objectCodes);
                task.objectCode = task.getPrimaryObjectCode();

                task.objects_v1 = split(task.object_str, ",");
                task.objects_v1_len = static_cast<int>(task.objects_v1.size());

                if (item["detectFps"].isNumeric())
                {
                    task.detectFps = item["detectFps"].asFloat();
                }
                else if (item["detectFps"].isString() && !item["detectFps"].asString().empty())
                {
                    float value = 0.0f;
                    if (tryParseFloat(item["detectFps"].asString(), value))
                    {
                        task.detectFps = value;
                    }
                }

                double scoreThresholdValue = 0.0;
                if (Control::tryParseJsonNumber(item["scoreThreshold"], scoreThresholdValue))
                {
                    task.scoreThreshold = static_cast<float>(scoreThresholdValue);
                    task.scoreThresholdSet = true;
                }

                double nmsThresholdValue = 0.0;
                if (Control::tryParseJsonNumber(item["nmsThreshold"], nmsThresholdValue))
                {
                    task.nmsThreshold = static_cast<float>(nmsThresholdValue);
                    task.nmsThresholdSet = true;
                }

                if (item["batch_enabled"].isBool())
                {
                    task.batch_enabled = item["batch_enabled"].asBool();
                }
                if (item["max_batch"].isInt())
                {
                    task.max_batch = std::max(1, item["max_batch"].asInt());
                }
                if (item["max_wait_ms"].isInt())
                {
                    task.max_wait_ms = std::max(0, item["max_wait_ms"].asInt());
                }
                if (item["queue_capacity"].isInt())
                {
                    task.queue_capacity = std::max(1, item["queue_capacity"].asInt());
                }
                if (item["timeout_drop"].isBool())
                {
                    task.timeout_drop = item["timeout_drop"].asBool();
                }

                control.algorithmTasks.push_back(task);
            }
        }

        if (root["detectFps"].isNumeric())
        {
            control.detectFps = root["detectFps"].asFloat();
        }
        else if (root["detectFps"].isString() && !root["detectFps"].asString().empty())
        {
            float value = 0.0f;
            if (tryParseFloat(root["detectFps"].asString(), value))
            {
                control.detectFps = value;
            }
        }

        if (control.detectFps < -2.0f)
        {
            control.detectFps = -2.0f;
        }
        if (control.detectFps > 30.0f)
        {
            control.detectFps = 30.0f;
        }

        if (root["minInterval"].isInt())
        {
            int valueSeconds = root["minInterval"].asInt();
            if (valueSeconds < 0)
            {
                valueSeconds = 0;
            }
            control.minInterval = static_cast<int64_t>(valueSeconds) * 1000; // 客户端传递过的该参数单位是秒
        }
        else if (root["minInterval"].isString())
        {
            int valueSeconds = 0;
            if (tryParseInt(root["minInterval"].asString(), valueSeconds))
            {
                if (valueSeconds < 0)
                {
                    valueSeconds = 0;
                }
                control.minInterval = static_cast<int64_t>(valueSeconds) * 1000; // 客户端传递过的该参数单位是秒
            }
        }
        // 强制设置报警时间最大不能超过3分钟=180000毫秒
        if (control.minInterval > 180000)
        {
            control.minInterval = 180000;
        }

        Json::Value geometryConfigNode;
        if (!root["geometryConfig"].isNull())
        {
            geometryConfigNode = resolveMaybeJsonNode(root["geometryConfig"]);
        }
        Json::Value regionsNode = root["regions"].isNull() ? Json::Value() : resolveMaybeJsonNode(root["regions"]);
        Json::Value linesNode = root["lines"].isNull() ? Json::Value() : resolveMaybeJsonNode(root["lines"]);
        Json::Value behaviorRulesNode = root["behaviorRules"].isNull() ? Json::Value() : resolveMaybeJsonNode(root["behaviorRules"]);
        if (regionsNode.isNull() && geometryConfigNode.isObject())
        {
            regionsNode = resolveMaybeJsonNode(geometryConfigNode["regions"]);
        }
        if (linesNode.isNull() && geometryConfigNode.isObject())
        {
            linesNode = resolveMaybeJsonNode(geometryConfigNode["lines"]);
        }
        if (behaviorRulesNode.isNull() && geometryConfigNode.isObject())
        {
            behaviorRulesNode = resolveMaybeJsonNode(geometryConfigNode["behaviorRules"]);
        }
        control.loadGeometryConfig(regionsNode, linesNode);
        control.loadBehaviorRulesConfig(behaviorRulesNode);
        
        // dwellEnabled flag (legacy)
        if (root["dwellEnabled"].isBool())
        {
            control.dwellEnabled = root["dwellEnabled"].asBool();
        }
        if (root["dwellThresholdMs"].isInt64())
        {
            control.dwellThresholdMs = root["dwellThresholdMs"].asInt64();
        }
        else if (root["dwellThresholdMs"].isInt())
        {
            control.dwellThresholdMs = static_cast<int64_t>(root["dwellThresholdMs"].asInt());
        }
        
        // pushEncoder
        if (root["pushEncoder"].isString())
        {
            control.pushEncoder = root["pushEncoder"].asString();
        }
        
        // saveVideoEnabled already parsed above
        if (root["alarmIntervalMs"].isInt())
        {
            control.alarmIntervalMs = root["alarmIntervalMs"].asInt();
        }

        if (control.validateAdd(result_msg))
        {
            scheduler->apiControlAdd(&control, result_code, result_msg);
        }
    }
    else
    {
        result_msg = "invalid request parameter";
    }

    Json::Value result;
    result["msg"] = result_msg;
    result["code"] = result_code;

    LOGI("\n \t request:%s \n \t response:%s", root.toStyledString().data(), result.toStyledString().data());

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}

void api_control_cancel(struct evhttp_request *req, void *arg)
{

    Scheduler *scheduler = (Scheduler *)arg;
    char buf[RECV_BUF_MAX_SIZE];
    parse_post(req, buf);

    Json::CharReaderBuilder builder;
    const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());

    Json::Value root;
    JSONCPP_STRING errs;

    int result_code = 0;
    std::string result_msg = "error";

    if (reader->parse(buf, buf + std::strlen(buf), &root, &errs) && errs.empty())
    {

        Control control;

        if (root["code"].isString())
        {
            control.code = root["code"].asCString();
        }
        if (control.validateCancel(result_msg))
        {
            scheduler->apiControlCancel(&control, result_code, result_msg);
        }
    }
    else
    {
        result_msg = "invalid request parameter";
    }

    Json::Value result;
    result["msg"] = result_msg;
    result["code"] = result_code;

    LOGI("\n \t request:%s \n \t response:%s", root.toStyledString().data(), result.toStyledString().data());

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}

void api_alarm_bind_media(struct evhttp_request *req, void *arg)
{
    Scheduler *scheduler = (Scheduler *)arg;
    char buf[RECV_BUF_MAX_SIZE];
    parse_post(req, buf);

    Json::CharReaderBuilder builder;
    const std::unique_ptr<Json::CharReader> reader(builder.newCharReader());

    Json::Value root;
    JSONCPP_STRING errs;

    int result_code = 0;
    std::string result_msg = "error";

    if (reader->parse(buf, buf + std::strlen(buf), &root, &errs) && errs.empty())
    {
        const std::string controlCode = root.isMember("control_code") ? root["control_code"].asString() : "";
        const std::string alarmId = root.isMember("alarm_id") ? root["alarm_id"].asString() : "";
        const std::string eventId = root.isMember("event_id") ? root["event_id"].asString() : "";
        const std::string behaviorType = root.isMember("behavior_type") ? root["behavior_type"].asString() : "";
        const std::string ruleId = root.isMember("rule_id") ? root["rule_id"].asString() : "";
        const std::string videoPath = root.isMember("video_path") ? root["video_path"].asString() : "";
        const std::string imagePath = root.isMember("image_path") ? root["image_path"].asString() : "";
        if (controlCode.empty() || alarmId.empty())
        {
            result_msg = "missing control_code or alarm_id";
        }
        else if (!scheduler)
        {
            result_msg = "scheduler unavailable";
        }
        else
        {
            scheduler->bindAlarmMedia(controlCode, alarmId, eventId, behaviorType, ruleId, videoPath, imagePath);
            LOGI("alarm bind-media accepted: controlCode=%s alarmId=%s eventId=%s behaviorType=%s ruleId=%s",
                 controlCode.data(), alarmId.data(), eventId.data(), behaviorType.data(), ruleId.data());
            result_code = 1000;
            result_msg = "success";
        }
    }
    else
    {
        result_msg = "invalid request parameter";
    }

    Json::Value result;
    result["msg"] = result_msg;
    result["code"] = result_code;

    struct evbuffer *buff = evbuffer_new();
    evbuffer_add_printf(buff, "%s", result.toStyledString().c_str());
    evhttp_send_reply(req, HTTP_OK, nullptr, buff);
    evbuffer_free(buff);
}

void parse_get(struct evhttp_request *req, struct evkeyvalq *params)
{
    if (req == nullptr || params == nullptr)
    {
        return;
    }
    const char *url = evhttp_request_get_uri(req);
    if (url == nullptr)
    {
        return;
    }
    struct evhttp_uri *decoded = evhttp_uri_parse(url);
    if (!decoded)
    {
        return;
    }
    const char *path = evhttp_uri_get_path(decoded);
    if (path == nullptr)
    {
        path = "/";
    }
    char *query = (char *)evhttp_uri_get_query(decoded);
    if (query == nullptr)
    {
        evhttp_uri_free(decoded);
        return;
    }
    evhttp_parse_query_str(query, params);
    evhttp_uri_free(decoded);
}
void parse_post(struct evhttp_request *req, char *buf)
{
    if (req == nullptr || buf == nullptr)
    {
        return;
    }

    if (buf)
    {
        buf[0] = '\0';
    }

    size_t post_size = 0;

    post_size = evbuffer_get_length(req->input_buffer);
    if (post_size <= 0)
    {
        //        printf("====line:%d,post msg is empty!\n",__LINE__);
        return;
    }
    else
    {
        size_t copy_len = post_size >= RECV_BUF_MAX_SIZE ? (RECV_BUF_MAX_SIZE - 1) : post_size;
        unsigned char *pulled = evbuffer_pullup(req->input_buffer, -1);
        if (pulled == nullptr)
        {
            return;
        }
        //        printf("====line:%d,post len:%d, copy_len:%d\n",__LINE__,post_size,copy_len);
        if (copy_len > 0)
        {
            memcpy(buf, pulled, copy_len);
        }
        buf[copy_len] = '\0';
        //        printf("====line:%d,post msg:%s\n",__LINE__,buf);
    }
}