package com.tudou.tudoumianshi.aop;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.exception.BusinessException;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.manager.CounterManager;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.service.UserService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
        // 检测爬虫
        if (loginUser != null) {
            crawlerDetect(loginUser.getId());
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
    private void crawlerDetect(long loginUserId) {
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
            User updateUser = new User();
            updateUser.setId(loginUserId);
            updateUser.setUserRole("ban");
            userService.updateById(updateUser);
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
}
