package com.ruoyi.waring.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.waring.domain.HWaring;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * 告警邮件通知。
 *
 * 与站内 WebSocket 推送互补：人离开页面后仍能收到。按告警等级分级发送，
 * 与睡岗三档分级天然咬合——严重档即时发，确认档不打扰。
 *
 * 收件人按告警所属组织找该组织下「有邮箱且启用」的用户；找不到就只打日志。
 * 任何异常都不影响告警落库。
 */
@Service
public class AlarmEmailNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(AlarmEmailNotifyService.class);

    /** 总开关，取值 true / false。 */
    public static final String CONFIG_KEY_ENABLED = "sva.sleep.email.enabled";
    /** 收件人上限，防止一次给几十个人发信。 */
    public static final String CONFIG_KEY_MAX_RECIPIENTS = "sva.sleep.email.maxRecipients";
    /** 兜底收件人（多个用逗号分隔），组织下没人配邮箱时用。 */
    public static final String CONFIG_KEY_FALLBACK = "sva.sleep.email.fallback";

    private static final int DEFAULT_MAX_RECIPIENTS = 10;
    private static final String LEVEL_SEVERE = "5";
    /** 关键帧三连图在邮件里的 Content-ID，与 HTML 正文的 cid: 引用一致。 */
    private static final String KEYFRAME_CID = "keyframes";
    private static final String KEYFRAME_FILE_NAME = "keyframes.jpg";

    @Resource
    private ISysConfigService sysConfigService;

    @Resource
    private ISysUserService sysUserService;

    @Resource
    private ISysDeptService sysDeptService;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 异步发信。调用方不等待，失败也不回滚落库。
     *
     * @param waring 已落库的告警，需带 alarm_level_name / device_name / org_index
     */
    public void notifyAlarm(HWaring waring)
    {
        if (waring == null || !isEnabled())
        {
            return;
        }
        // 只发严重档，避免确认档把邮箱刷爆。
        if (!LEVEL_SEVERE.equals(StringUtils.trimToEmpty(waring.getAlarm_level())))
        {
            log.debug("告警邮件跳过非严重档: wId={} level={}", waring.getW_id(), waring.getAlarm_level());
            return;
        }
        if (StringUtils.isBlank(mailFrom))
        {
            log.info("告警邮件未配置 SMTP 账号, 仅记录日志: wId={} device={}",
                waring.getW_id(), waring.getDevice_name());
            return;
        }
        List<String> recipients = resolveRecipients(waring);
        if (recipients.isEmpty())
        {
            log.info("告警邮件无可用收件人, 仅记录日志: wId={} orgIndex={}",
                waring.getW_id(), waring.getOrg_index());
            return;
        }
        try
        {
            threadPoolTaskExecutor.execute(() -> send(waring, recipients));
        }
        catch (Exception ex)
        {
            log.warn("告警邮件提交失败: wId={} {}", waring.getW_id(), ex.getMessage());
        }
    }

    private boolean isEnabled()
    {
        return Convert.toBool(sysConfigService.selectConfigByKey(CONFIG_KEY_ENABLED), false);
    }

    /**
     * 收件人：优先告警所属组织下的启用用户，其次兜底配置。
     */
    private List<String> resolveRecipients(HWaring waring)
    {
        List<String> recipients = new ArrayList<>();
        int max = Convert.toInt(sysConfigService.selectConfigByKey(CONFIG_KEY_MAX_RECIPIENTS),
            DEFAULT_MAX_RECIPIENTS);
        String orgIndex = StringUtils.trimToEmpty(waring.getOrg_index());
        if (StringUtils.isNotEmpty(orgIndex))
        {
            SysDept query = new SysDept();
            query.setOrgIndex(orgIndex);
            List<SysDept> depts = sysDeptService.selectDeptList(query);
            for (SysDept dept : depts)
            {
                if (dept == null || dept.getDeptId() == null)
                {
                    continue;
                }
                SysUser userQuery = new SysUser();
                userQuery.setDeptId(dept.getDeptId());
                userQuery.setStatus("0");
                List<SysUser> users = sysUserService.selectUserList(userQuery);
                for (SysUser user : users)
                {
                    String email = user == null ? "" : StringUtils.trimToEmpty(user.getEmail());
                    if (StringUtils.isNotEmpty(email) && !recipients.contains(email))
                    {
                        recipients.add(email);
                        if (recipients.size() >= max)
                        {
                            return recipients;
                        }
                    }
                }
            }
        }
        if (recipients.isEmpty())
        {
            String fallback = StringUtils.trimToEmpty(sysConfigService.selectConfigByKey(CONFIG_KEY_FALLBACK));
            if (StringUtils.isNotEmpty(fallback))
            {
                for (String item : fallback.split(","))
                {
                    String email = item.trim();
                    if (StringUtils.isNotEmpty(email) && !recipients.contains(email))
                    {
                        recipients.add(email);
                    }
                }
            }
        }
        return recipients;
    }

    private void send(HWaring waring, List<String> recipients)
    {
        try
        {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(buildSubject(waring));
            // 正文用 HTML，关键帧三连图作为内联资源（cid 引用），邮件里直接看到对比图。
            helper.setText(buildHtmlBody(waring), true);
            attachKeyframeStrip(helper, waring);
            mailSender.send(message);
            log.info("告警邮件已发送: wId={} to={}", waring.getW_id(), recipients);
        }
        catch (Exception ex)
        {
            log.warn("告警邮件发送失败: wId={} {}", waring.getW_id(), ex.getMessage());
        }
    }

    /**
     * 关键帧三连图内联进邮件。图由 Analyzer 写在封面图同目录的 keyframes.jpg。
     * 找不到就跳过，纯文字邮件照常发。
     */
    private void attachKeyframeStrip(MimeMessageHelper helper, HWaring waring)
    {
        try
        {
            Path strip = resolveKeyframeStrip(waring);
            if (strip == null)
            {
                return;
            }
            helper.addInline(KEYFRAME_CID, new FileSystemResource(strip.toFile()), "image/jpeg");
        }
        catch (Exception ex)
        {
            log.debug("关键帧三连图内联失败, wId={} {}", waring.getW_id(), ex.getMessage());
        }
    }

    /**
     * 从封面图路径推导同目录的 keyframes.jpg，并映射到本地上传目录。
     */
    private Path resolveKeyframeStrip(HWaring waring)
    {
        String imagePath = StringUtils.trimToEmpty(waring.getPicture_url());
        if (StringUtils.isEmpty(imagePath))
        {
            return null;
        }
        String relative = imagePath.replace('\\', '/');
        if (relative.startsWith("http://") || relative.startsWith("https://"))
        {
            try
            {
                relative = URI.create(relative).getPath();
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
        while (relative.startsWith("/"))
        {
            relative = relative.substring(1);
        }
        if (relative.startsWith(Constants.RESOURCE_PREFIX))
        {
            relative = relative.substring(Constants.RESOURCE_PREFIX.length());
        }
        int slash = relative.lastIndexOf('/');
        if (slash <= 0)
        {
            return null;
        }
        String stripRelative = relative.substring(0, slash + 1) + KEYFRAME_FILE_NAME;
        String profile = StringUtils.trimToEmpty(RuoYiConfig.getProfile());
        if (StringUtils.isEmpty(profile))
        {
            return null;
        }
        Path candidate = Paths.get(profile, stripRelative);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private String buildHtmlBody(HWaring waring)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<div style=\"font-family:-apple-system,'Segoe UI',sans-serif;font-size:14px;color:#222;\">");
        builder.append("<h3 style=\"margin:0 0 12px;\">")
            .append(escapeHtml(buildSubject(waring)))
            .append("</h3>");
        builder.append("<table cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
        appendRow(builder, "告警类型", StringUtils.defaultIfBlank(waring.getAlarm_type_name(), "—"));
        appendRow(builder, "告警等级", StringUtils.defaultIfBlank(waring.getAlarm_level_name(), "—"));
        appendRow(builder, "设备", StringUtils.defaultIfBlank(waring.getDevice_name(), waring.getDevice_id()));
        if (StringUtils.isNotBlank(waring.getOrg_name()))
        {
            appendRow(builder, "组织", waring.getOrg_name());
        }
        appendRow(builder, "时间", StringUtils.trimToEmpty(waring.getAlarm_time()));
        if (waring.getDuration_ms() != null && waring.getDuration_ms() > 0)
        {
            appendRow(builder, "持续", String.format("%.1f 秒", waring.getDuration_ms() / 1000.0));
        }
        if (waring.getSva_pitch_degree() != null)
        {
            appendRow(builder, "俯仰角", String.format("%.0f°", waring.getSva_pitch_degree()));
        }
        if (waring.getSva_sleep_score() != null)
        {
            appendRow(builder, "睡岗质量分", String.format("%.0f / 100", waring.getSva_sleep_score()));
        }
        appendRow(builder, "事件号", StringUtils.trimToEmpty(waring.getId()));
        builder.append("</table>");

        if (resolveKeyframeStrip(waring) != null)
        {
            builder.append("<p style=\"margin:16px 0 6px;\">关键帧（起始 / 峰值 / 结束）：</p>");
            builder.append("<img src=\"cid:").append(KEYFRAME_CID)
                .append("\" alt=\"keyframes\" style=\"max-width:100%;border:1px solid #ddd;\"/>");
        }
        else if (StringUtils.isNotBlank(waring.getPicture_absolute_url()))
        {
            // 没有三连图时退回封面截图，注意邮件客户端不认相对路径。
            builder.append("<p style=\"margin:16px 0 6px;\">告警截图：</p>");
            builder.append("<img src=\"").append(escapeHtml(waring.getPicture_absolute_url()))
                .append("\" alt=\"snapshot\" style=\"max-width:100%;border:1px solid #ddd;\"/>");
        }

        builder.append("<p style=\"margin-top:16px;color:#666;\">请登录平台查看视频证据。</p>");
        builder.append("</div>");
        return builder.toString();
    }

    private void appendRow(StringBuilder builder, String label, String value)
    {
        builder.append("<tr><td style=\"color:#666;padding-right:14px;white-space:nowrap;\">")
            .append(escapeHtml(label))
            .append("</td><td style=\"color:#111;font-weight:600;\">")
            .append(escapeHtml(value))
            .append("</td></tr>");
    }

    private String escapeHtml(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String buildSubject(HWaring waring)
    {
        return "【" + StringUtils.defaultIfBlank(waring.getAlarm_level_name(), "告警") + "】"
            + StringUtils.defaultIfBlank(waring.getAlarm_type_name(), "告警")
            + " - " + StringUtils.defaultIfBlank(waring.getDevice_name(), waring.getDevice_id());
    }

    private String buildBody(HWaring waring)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("告警类型：")
            .append(StringUtils.defaultIfBlank(waring.getAlarm_type_name(), "—")).append('\n');
        builder.append("告警等级：")
            .append(StringUtils.defaultIfBlank(waring.getAlarm_level_name(), "—")).append('\n');
        builder.append("设备：")
            .append(StringUtils.defaultIfBlank(waring.getDevice_name(), waring.getDevice_id())).append('\n');
        if (StringUtils.isNotBlank(waring.getOrg_name()))
        {
            builder.append("组织：").append(waring.getOrg_name()).append('\n');
        }
        builder.append("时间：").append(StringUtils.trimToEmpty(waring.getAlarm_time())).append('\n');
        if (waring.getDuration_ms() != null && waring.getDuration_ms() > 0)
        {
            builder.append("持续：").append(String.format("%.1f", waring.getDuration_ms() / 1000.0))
                .append(" 秒\n");
        }
        if (waring.getSva_pitch_degree() != null)
        {
            builder.append("俯仰角：").append(String.format("%.0f", waring.getSva_pitch_degree()))
                .append("°\n");
        }
        if (waring.getSva_sleep_score() != null)
        {
            builder.append("睡岗质量分：").append(String.format("%.0f", waring.getSva_sleep_score()))
                .append(" / 100\n");
        }
        builder.append("事件号：").append(StringUtils.trimToEmpty(waring.getId())).append('\n');
        builder.append("\n请登录平台查看截图与视频证据。");
        return builder.toString();
    }
}
