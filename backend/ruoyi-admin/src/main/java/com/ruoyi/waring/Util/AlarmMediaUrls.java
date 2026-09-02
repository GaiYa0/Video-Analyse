package com.ruoyi.waring.Util;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.ruoyi.waring.domain.Details;
import com.ruoyi.waring.domain.HWaring;

/**
 * 浏览器侧告警媒体 URL。Nginx 用 {@code /alarm/}、{@code /zlm/} 提供文件；
 * 库里常拼成 127.0.0.1，局域网打不开。不改 zlm_server / sva_server 的 host。
 */
public final class AlarmMediaUrls
{
    private AlarmMediaUrls()
    {
    }

    public static String toBrowserUrl(String mediaUrl)
    {
        if (mediaUrl == null)
        {
            return "";
        }
        String trimmed = mediaUrl.trim();
        if (trimmed.isEmpty())
        {
            return "";
        }

        String publicPath = extractPublicMediaPath(trimmed);
        if (publicPath == null)
        {
            return trimmed;
        }
        if (isHttpUrl(trimmed) && !isLoopbackHost(trimmed))
        {
            return trimmed;
        }
        return publicPath;
    }

    public static void rewrite(HWaring waring)
    {
        if (waring == null)
        {
            return;
        }
        waring.setPicture_absolute_url(toBrowserUrl(waring.getPicture_absolute_url()));
        waring.setVideo_absolute_url(toBrowserUrl(waring.getVideo_absolute_url()));
        if ((waring.getVideo_absolute_url() == null || waring.getVideo_absolute_url().trim().isEmpty())
            && waring.getVideo_url() != null && !waring.getVideo_url().trim().isEmpty())
        {
            waring.setVideo_absolute_url(toBrowserUrl(waring.getVideo_url()));
        }
    }

    public static List<HWaring> rewrite(List<HWaring> list)
    {
        if (list == null)
        {
            return list;
        }
        for (HWaring item : list)
        {
            rewrite(item);
        }
        return list;
    }

    public static void rewrite(Details details)
    {
        if (details == null)
        {
            return;
        }
        details.setPicture_absolute_url(toBrowserUrl(details.getPicture_absolute_url()));
        details.setVideo_absolute_url(toBrowserUrl(details.getVideo_absolute_url()));
    }

    public static void rewritePictureMaps(List<Map<String, Object>> rows)
    {
        if (rows == null)
        {
            return;
        }
        for (Map<String, Object> row : rows)
        {
            if (row == null)
            {
                continue;
            }
            Object url = row.get("picture_absolute_url");
            if (url != null)
            {
                row.put("picture_absolute_url", toBrowserUrl(String.valueOf(url)));
            }
        }
    }

    private static String extractPublicMediaPath(String value)
    {
        String path = value;
        if (isHttpUrl(value))
        {
            try
            {
                URI uri = URI.create(value);
                path = uri.getPath();
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
        if (path == null || path.trim().isEmpty())
        {
            return null;
        }
        String normalized = path.trim();
        if (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("alarm/") || normalized.startsWith("zlm/"))
        {
            return "/" + normalized;
        }
        return null;
    }

    private static boolean isHttpUrl(String value)
    {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static boolean isLoopbackHost(String value)
    {
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null)
            {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return "127.0.0.1".equals(normalized)
                || "localhost".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized);
        }
        catch (Exception ignored)
        {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.contains("127.0.0.1") || lower.contains("localhost");
        }
    }
}
