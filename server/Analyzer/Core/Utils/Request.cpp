#include "Request.h"
#include <curl/curl.h>
#include <openssl/sha.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/time.h>
#include <netdb.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <sstream>
#include <chrono>
#include <mutex>
#include <random>
#include <unordered_map>
#include <vector>
#include <algorithm>
#include "Log.h"
#include "Base64.h"

namespace SVAAnalyzer
{
    namespace
    {
        struct ParsedWsUrl
        {
            std::string scheme;
            std::string host;
            std::string port;
            std::string path;
        };

        struct WsConnection
        {
            int fd = -1;
            int64_t lastSendMs = 0;
        };

        std::mutex gWsConnectionMutex;
        std::unordered_map<std::string, WsConnection> gWsConnections;
        const char *kWebSocketGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        constexpr int64_t kWsPingIntervalMs = 3000;
        constexpr int64_t kWsStaleReconnectMs = 4500;

        bool startsWith(const std::string &value, const char *prefix)
        {
            return value.rfind(prefix, 0) == 0;
        }

        int64_t nowMs()
        {
            return std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::steady_clock::now().time_since_epoch())
                .count();
        }

        bool parseWsUrl(const std::string &url, ParsedWsUrl &parsed)
        {
            parsed = ParsedWsUrl();
            if (startsWith(url, "ws://"))
            {
                parsed.scheme = "ws";
            }
            else if (startsWith(url, "wss://"))
            {
                parsed.scheme = "wss";
                return false;
            }
            else
            {
                return false;
            }

            size_t hostBegin = url.find("://") + 3;
            size_t pathBegin = url.find('/', hostBegin);
            std::string authority = pathBegin == std::string::npos ? url.substr(hostBegin) : url.substr(hostBegin, pathBegin - hostBegin);
            parsed.path = pathBegin == std::string::npos ? "/" : url.substr(pathBegin);
            if (authority.empty())
            {
                return false;
            }

            size_t colonPos = authority.rfind(':');
            if (colonPos != std::string::npos && authority.find(']') == std::string::npos)
            {
                parsed.host = authority.substr(0, colonPos);
                parsed.port = authority.substr(colonPos + 1);
            }
            else
            {
                parsed.host = authority;
                parsed.port = "80";
            }
            return !parsed.host.empty() && !parsed.port.empty();
        }

        std::string toLowerCopy(const std::string &value)
        {
            std::string result = value;
            std::transform(result.begin(), result.end(), result.begin(), [](unsigned char c) {
                return static_cast<char>(std::tolower(c));
            });
            return result;
        }

        std::string trimCopy(const std::string &value)
        {
            size_t begin = 0;
            size_t end = value.size();
            while (begin < end && std::isspace(static_cast<unsigned char>(value[begin])))
            {
                ++begin;
            }
            while (end > begin && std::isspace(static_cast<unsigned char>(value[end - 1])))
            {
                --end;
            }
            return value.substr(begin, end - begin);
        }

        void closeWsConnection(WsConnection &connection)
        {
            if (connection.fd >= 0)
            {
                close(connection.fd);
                connection.fd = -1;
            }
            connection.lastSendMs = 0;
        }

        bool sendAll(int fd, const void *buffer, size_t size)
        {
            const char *cursor = static_cast<const char *>(buffer);
            size_t remaining = size;
            while (remaining > 0)
            {
                int flags = 0;
#ifdef MSG_NOSIGNAL
                flags |= MSG_NOSIGNAL;
#endif
                ssize_t sent = send(fd, cursor, remaining, flags);
                if (sent <= 0)
                {
                    return false;
                }
                cursor += sent;
                remaining -= static_cast<size_t>(sent);
            }
            return true;
        }

        bool readHttpUpgradeResponse(int fd, std::string &response)
        {
            response.clear();
            char buffer[1024];
            while (response.find("\r\n\r\n") == std::string::npos)
            {
                ssize_t received = recv(fd, buffer, sizeof(buffer), 0);
                if (received <= 0)
                {
                    return false;
                }
                response.append(buffer, static_cast<size_t>(received));
                if (response.size() > 8192)
                {
                    return false;
                }
            }
            return true;
        }

        std::string buildExpectedAccept(const std::string &key)
        {
            std::string input = key + kWebSocketGuid;
            unsigned char digest[SHA_DIGEST_LENGTH];
            SHA1(reinterpret_cast<const unsigned char *>(input.data()), input.size(), digest);
            Base64 base64;
            std::string encoded;
            base64.encode(digest, SHA_DIGEST_LENGTH, encoded);
            return encoded;
        }

        bool buildSecWebSocketKey(std::string &key)
        {
            std::random_device rd;
            std::mt19937 generator(rd());
            std::uniform_int_distribution<int> distribution(0, 255);
            unsigned char randomBytes[16];
            for (unsigned char &randomByte : randomBytes)
            {
                randomByte = static_cast<unsigned char>(distribution(generator));
            }
            Base64 base64;
            key.clear();
            base64.encode(randomBytes, 16, key);
            return !key.empty();
        }

        bool verifyUpgradeResponse(const std::string &response, const std::string &expectedAccept)
        {
            size_t firstLineEnd = response.find("\r\n");
            if (firstLineEnd == std::string::npos)
            {
                return false;
            }
            if (response.substr(0, firstLineEnd).find("101") == std::string::npos)
            {
                return false;
            }

            size_t searchFrom = firstLineEnd + 2;
            while (searchFrom < response.size())
            {
                size_t lineEnd = response.find("\r\n", searchFrom);
                if (lineEnd == std::string::npos || lineEnd == searchFrom)
                {
                    break;
                }
                std::string line = response.substr(searchFrom, lineEnd - searchFrom);
                size_t colonPos = line.find(':');
                if (colonPos != std::string::npos)
                {
                    std::string headerName = toLowerCopy(trimCopy(line.substr(0, colonPos)));
                    if (headerName == "sec-websocket-accept")
                    {
                        return trimCopy(line.substr(colonPos + 1)) == expectedAccept;
                    }
                }
                searchFrom = lineEnd + 2;
            }
            return false;
        }

        bool connectWebSocket(const std::string &url, WsConnection &connection)
        {
            ParsedWsUrl parsed;
            if (!parseWsUrl(url, parsed))
            {
                LOGE("unsupported websocket url: %s", url.c_str());
                return false;
            }
            if (parsed.scheme != "ws")
            {
                LOGE("wss is not supported in current analyzer build: %s", url.c_str());
                return false;
            }

            struct addrinfo hints;
            std::memset(&hints, 0, sizeof(hints));
            hints.ai_family = AF_UNSPEC;
            hints.ai_socktype = SOCK_STREAM;

            struct addrinfo *result = nullptr;
            int gaiCode = getaddrinfo(parsed.host.c_str(), parsed.port.c_str(), &hints, &result);
            if (gaiCode != 0)
            {
                LOGE("websocket getaddrinfo failed: url=%s, error=%s", url.c_str(), gai_strerror(gaiCode));
                return false;
            }

            int fd = -1;
            for (struct addrinfo *rp = result; rp != nullptr; rp = rp->ai_next)
            {
                fd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
                if (fd < 0)
                {
                    continue;
                }

                struct timeval timeout;
                timeout.tv_sec = 5;
                timeout.tv_usec = 0;
                setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
                setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

                if (connect(fd, rp->ai_addr, rp->ai_addrlen) == 0)
                {
                    break;
                }

                close(fd);
                fd = -1;
            }
            freeaddrinfo(result);

            if (fd < 0)
            {
                LOGE("websocket connect failed: url=%s, errno=%d", url.c_str(), errno);
                return false;
            }

            std::string secKey;
            if (!buildSecWebSocketKey(secKey))
            {
                close(fd);
                return false;
            }

            std::ostringstream request;
            request << "GET " << parsed.path << " HTTP/1.1\r\n"
                    << "Host: " << parsed.host << ':' << parsed.port << "\r\n"
                    << "Upgrade: websocket\r\n"
                    << "Connection: Upgrade\r\n"
                    << "Sec-WebSocket-Key: " << secKey << "\r\n"
                    << "Sec-WebSocket-Version: 13\r\n"
                    << "User-Agent: Analyzer\r\n\r\n";

            const std::string requestText = request.str();
            if (!sendAll(fd, requestText.data(), requestText.size()))
            {
                LOGE("websocket handshake send failed: url=%s", url.c_str());
                close(fd);
                return false;
            }

            std::string response;
            if (!readHttpUpgradeResponse(fd, response))
            {
                LOGE("websocket handshake receive failed: url=%s", url.c_str());
                close(fd);
                return false;
            }

            if (!verifyUpgradeResponse(response, buildExpectedAccept(secKey)))
            {
                LOGE("websocket handshake verify failed: url=%s, response=%s", url.c_str(), response.c_str());
                close(fd);
                return false;
            }

            connection.fd = fd;
            connection.lastSendMs = nowMs();
            return true;
        }

        bool sendMaskedFrame(int fd, unsigned char opcode, const std::string &payload)
        {
            std::vector<unsigned char> frame;
            frame.reserve(payload.size() + 16);
            frame.push_back(static_cast<unsigned char>(0x80 | (opcode & 0x0F)));

            const uint64_t payloadSize = static_cast<uint64_t>(payload.size());
            if (payloadSize <= 125)
            {
                frame.push_back(static_cast<unsigned char>(0x80 | payloadSize));
            }
            else if (payloadSize <= 0xFFFF)
            {
                frame.push_back(0x80 | 126);
                frame.push_back(static_cast<unsigned char>((payloadSize >> 8) & 0xFF));
                frame.push_back(static_cast<unsigned char>(payloadSize & 0xFF));
            }
            else
            {
                frame.push_back(0x80 | 127);
                for (int shift = 56; shift >= 0; shift -= 8)
                {
                    frame.push_back(static_cast<unsigned char>((payloadSize >> shift) & 0xFF));
                }
            }

            std::random_device rd;
            unsigned char mask[4];
            for (unsigned char &maskByte : mask)
            {
                maskByte = static_cast<unsigned char>(rd());
                frame.push_back(maskByte);
            }
            for (size_t i = 0; i < payload.size(); ++i)
            {
                frame.push_back(static_cast<unsigned char>(payload[i]) ^ mask[i % 4]);
            }
            return sendAll(fd, frame.data(), frame.size());
        }

        bool ensureWsConnectionReady(const std::string &url, WsConnection &connection)
        {
            if (connection.fd < 0)
            {
                return connectWebSocket(url, connection);
            }

            const int64_t elapsedMs = nowMs() - connection.lastSendMs;
            if (elapsedMs < kWsPingIntervalMs)
            {
                return true;
            }
            if (elapsedMs >= kWsStaleReconnectMs)
            {
                closeWsConnection(connection);
                return connectWebSocket(url, connection);
            }
            if (sendMaskedFrame(connection.fd, 0x9, ""))
            {
                connection.lastSendMs = nowMs();
                return true;
            }
            closeWsConnection(connection);
            return connectWebSocket(url, connection);
        }

        bool sendMaskedTextFrame(int fd, const std::string &payload)
        {
            return sendMaskedFrame(fd, 0x1, payload);
        }
    }

    inline size_t onWrite(void *buffer, size_t size, size_t nmemb, void *stream)
    {

        std::string *str = dynamic_cast<std::string *>((std::string *)stream);
        if (NULL == str || NULL == buffer)
        {
            return -1;
        }

        char *pData = (char *)buffer;
        str->append(pData, size * nmemb);
        return nmemb;
    }
    /*
    inline size_t onWrite(void* ptr, size_t size, size_t nmEmb, void* stream) {
        //    std::cout << "----->reply" << std::endl;
        std::string* str = (std::string*)stream;
        //    std::cout << *str << std::endl;
        (*str).append((char*)ptr, size * nmEmb);

        return size * nmEmb;
    }
    */
    Request::Request()
    {
    }

    Request::~Request()
    {
    }

    bool Request::get(const char *url, std::string &response)
    {

        CURL *curl = curl_easy_init();
        bool result;

        if (curl)
        {
            curl_easy_setopt(curl, CURLOPT_URL, url);
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, false);
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, false);

            curl_easy_setopt(curl, CURLOPT_VERBOSE, 0); // 0 or 1 当等于1时，会显示详细的调试信息,
            curl_easy_setopt(curl, CURLOPT_READFUNCTION, NULL);
            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, onWrite);
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&response);
            curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);

            curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 10);
            curl_easy_setopt(curl, CURLOPT_TIMEOUT, 10);

            CURLcode code = curl_easy_perform(curl);

            if (code != CURLE_OK)
            {
                LOGE("curl_easy_strerror: %s", curl_easy_strerror(code));
                result = false;
            }
            else
            {
                result = true;
            }
        }
        else
        {
            LOGE("curl_easy_init error");
            result = false;
        }
        curl_easy_cleanup(curl);

        return result;
    }
    bool Request::post(const char *url, const char *data, std::string &response)
    {
        curl_global_init(CURL_GLOBAL_WIN32);

        CURL *curl = curl_easy_init();
        bool result;

        if (curl)
        {
            struct curl_slist *headers = nullptr;
            headers = curl_slist_append(headers, "User-Agent: Analyzer;");
            headers = curl_slist_append(headers, "Content-Type:application/json;");
            headers = curl_slist_append(headers,
                                        "Expect:"); // libcurl请求慢解决方法 https://blog.csdn.net/feng964497595/article/details/86316861
            curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

            // 不接收响应头数据0代表不接收 1代表接收
            curl_easy_setopt(curl, CURLOPT_HEADER, 0);

            curl_easy_setopt(curl, CURLOPT_URL, url);
            curl_easy_setopt(curl, CURLOPT_POST, 1);          // post type
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, data); // post params

            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, false); // if want to use https
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, false); // set peer and host verify false

            curl_easy_setopt(curl, CURLOPT_VERBOSE, 0); // 值为1时，会显示详细的调试信息
            curl_easy_setopt(curl, CURLOPT_READFUNCTION, NULL);
            curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, onWrite);
            curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&response);
            curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);
            // curl_easy_setopt(curl, CURLOPT_HEADER, false);// 是否显示响应头信息
            curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 30);
            curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30);

            CURLcode code = curl_easy_perform(curl);

            if (code != CURLE_OK)
            {
                LOGE("curl_easy_strerror: url=%s, %s", url, curl_easy_strerror(code));
                result = false;
            }
            else
            {
                result = true;
            }
            curl_slist_free_all(headers); // 清理headers,防止内存泄漏
        }
        else
        {
            LOGE("curl_easy_init error: url=%s", url);
            result = false;
        }
        curl_easy_cleanup(curl);
        curl_global_cleanup();
        return result;
    }

    bool Request::sendText(const char *url, const char *data, std::string &response)
    {
        response.clear();
        if (url == nullptr || data == nullptr)
        {
            return false;
        }

        const std::string urlText(url);
        std::lock_guard<std::mutex> lock(gWsConnectionMutex);
        WsConnection &connection = gWsConnections[urlText];
        if (!ensureWsConnectionReady(urlText, connection))
        {
            gWsConnections.erase(urlText);
            return false;
        }

        const std::string payload(data);
        if (!sendMaskedTextFrame(connection.fd, payload))
        {
            LOGE("websocket send failed, reconnecting once: url=%s", url);
            closeWsConnection(connection);
            if (!connectWebSocket(urlText, connection) || !sendMaskedTextFrame(connection.fd, payload))
            {
                closeWsConnection(connection);
                gWsConnections.erase(urlText);
                return false;
            }
        }
        connection.lastSendMs = nowMs();
        response = "WS_SENT";
        return true;
    }
}