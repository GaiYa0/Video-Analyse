#include "Algorithm.h"
#include "Config.h"
#include "Utils/Log.h"
#include "Utils/Common.h"

namespace SVAAnalyzer
{
    Algorithm::Algorithm(Config *config) : mConfig(config)
    {
    }

    Algorithm::~Algorithm()
    {
    }
    bool Algorithm::createState()
    {
        return mCreateState;
    }

}