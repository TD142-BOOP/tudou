package com.tudou.tudoumianshi.aop;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.manager.CounterManager;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.service.UserService;
import com.tudou.tudoumianshi.utils.IpUtils;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Aspect
@Component
public class LimitAspect {
    @Resource
    private CounterManager counterManager;
    @Resource
    private UserService userService;

    @Pointcut("@annotation(com.tudou.tudoumianshi.annotation.Limit)")
    public void controllerMethods() {
    }

    @Before("controllerMethods() && args(id,request)")
    public void beforeMethod(long id, HttpServletRequest request) throws Throwable {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        String ip = IpUtils.getClientIp(request);
        // 检测爬虫
        if (loginUser != null) {
            crawlerDetect(loginUser.getId(),ip);
        }
    }

//    @NacosValue(value = "${warnCount}", autoRefreshed = true)
//    private Integer warnCount;
//
//    @NacosValue(value = "${banCount}", autoRefreshed = true)
//    private Integer banCount;

    /**
     * 检测爬虫
     * @param loginUserId
     */
    private void crawlerDetect(long loginUserId, String ip) {
//        // 调用多少次时告警
//        final int WARN_COUNT = warnCount;
//        // 超过多少次封号
//        final int BAN_COUNT = banCount;
        // 调用多少次时告警
        final int WARN_COUNT = 10;
        // 超过多少次封号
        final int BAN_COUNT = 20;
        // 拼接访问 key
        String key = String.format("user:access:%s", loginUserId);
        // 一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key);
        // 是否封号
        if (count > BAN_COUNT) {
            // 踢下线
            StpUtil.kickout(loginUserId);
            // 封号
            updateNacosBlackList(ip);
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问太频繁，已被封号");
        }
        // 是否告警
        if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告访问太频繁");
        }
    }
    public String handleBlock(BlockException ex) {
        // 降级处理逻辑
        return "降级处理，请稍后重试。";
    }


    @Value("${nacos.config.data-id}")
    private String dataId;

    @Value("${nacos.config.server-addr}")
    private String serverAddr;

    @Value("${nacos.config.group}")
    private String group;
    private void updateNacosBlackList(String ip){
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);

        try {
            // 创建ConfigService
            ConfigService configService = NacosFactory.createConfigService(properties);

            // 从Nacos获取配置
            String content = configService.getConfig(dataId, group, 5000);
            System.out.println("获取到的YAML配置内容: " + content);

            // 解析YAML
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(content);

            // 获取黑名单IP列表
            List<String> blacklist = (List<String>) yamlMap.get("blackIpList");
            // 添加新的IP到黑名单
            if (!blacklist.contains(ip)) {
                blacklist.add(ip);
                System.out.println("添加新的IP到黑名单: " + ip);
            }

            // 将更新后的YAML内容发布到Nacos
            String updatedContent = yaml.dump(yamlMap);
            boolean isPublishOk = configService.publishConfig(dataId, group, updatedContent);
            if (isPublishOk) {
                System.out.println("配置更新成功");
            } else {
                System.out.println("配置更新失败");
            }
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }
}
