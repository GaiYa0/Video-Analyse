#include "Config.h"
#include <fstream>
#include <iostream>
#include <filesystem>
#include <json/json.h>
#include "Utils/Log.h"
#include "Version.h"

namespace SVAAnalyzer
{
    Config::Config(const char *file) : file(file)
    {

        std::ifstream ifs(file, std::ios::binary);

        if (!ifs.is_open())
        {
            LOGE("open %s error", file);
            return;
        }
        else
        {
            Json::CharReaderBuilder builder;
            builder["collectComments"] = true;
            JSONCPP_STRING errs;
            Json::Value root;

            if (parseFromStream(builder, ifs, &root, &errs))
            {
                this->code = root["code"].asString();
                this->host = root["host"].asString();
                this->adminPort = root["adminPort"].asInt();
                this->adminHost = "http://" + this->host + ":" + std::to_string(this->adminPort);
                this->saveAlarmUrl = root.isMember("saveAlarmUrl") ? root["saveAlarmUrl"].asString() : "";
                this->detectEventUrl = root.isMember("detectEventUrl") ? root["detectEventUrl"].asString() : "";
                this->analyzerPort = root["analyzerPort"].asInt();
                this->mediaHttpPort = root["mediaHttpPort"].asInt();
                this->mediaRtspPort = root["mediaRtspPort"].asInt();

                this->uploadDir = root["uploadDir"].asString();
                this->modelDir = root["modelDir"].asString();

                std::filesystem::path path(uploadDir);
                try
                {
                    if (!std::filesystem::exists(path))
                    {
                        std::filesystem::create_directory(path);
                    }

                    mState = true;
                }
                catch (std::filesystem::filesystem_error &e)
                {
                    std::cout << e.what() << std::endl;
                }
            }
            else
            {
                LOGE("parse %s error", file);
            }
            ifs.close();
        }
    }

    Config::~Config()
    {
    }

    void Config::show()
    {

        printf("config.file=%s\n", file);
        printf("config.host=%s\n", host.data());
        printf("config.adminPort=%d\n", adminPort);
        printf("config.analyzerPort=%d\n", analyzerPort);
        printf("config.mediaHttpPort=%d\n", mediaHttpPort);
        printf("config.mediaRtspPort=%d\n", mediaRtspPort);
        printf("config.detectEventUrl=%s\n", detectEventUrl.data());

        printf("config.uploadDir=%s\n", uploadDir.data());
        printf("config.modelDir=%s\n", modelDir.data());
    }
}