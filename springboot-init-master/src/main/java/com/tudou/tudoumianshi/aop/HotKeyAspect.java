package com.tudou.tudoumianshi.aop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jd.platform.hotkey.client.callback.JdHotKeyStore;

import com.tudou.tudoumianshi.common.BaseResponse;
import com.tudou.tudoumianshi.common.ErrorCode;
import com.tudou.tudoumianshi.common.ResultUtils;
import com.tudou.tudoumianshi.exception.ThrowUtils;
import com.tudou.tudoumianshi.model.dto.question.QuestionQueryRequest;
import com.tudou.tudoumianshi.model.dto.questionBank.QuestionBankQueryRequest;
import com.tudou.tudoumianshi.model.entity.Question;
import com.tudou.tudoumianshi.model.entity.QuestionBank;
import com.tudou.tudoumianshi.model.entity.User;
import com.tudou.tudoumianshi.model.vo.QuestionBankVO;

import com.tudou.tudoumianshi.service.QuestionBankService;
import com.tudou.tudoumianshi.service.QuestionService;
import com.tudou.tudoumianshi.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class HotKeyAspect {

    @Resource
    private QuestionBankService questionBankService;

    @Pointcut("@annotation(com.tudou.tudoumianshi.annotation.HotKeyValid)")
    public void controllerMethods() {
    }

    @Before("controllerMethods() && args(questionBankQueryRequest, request)")
    public void beforeMethod(QuestionBankQueryRequest questionBankQueryRequest, HttpServletRequest request) throws Throwable {
        if (questionBankQueryRequest != null && questionBankQueryRequest.getId() != null && questionBankQueryRequest.getId() > 0) {
            String key = "bank_detail_" + questionBankQueryRequest.getId();
            if (JdHotKeyStore.isHotKey(key)) {
                QuestionBank questionBank = questionBankService.getById(questionBankQueryRequest.getId());
                QuestionBankVO questionBankVO = new QuestionBankVO();
                BeanUtils.copyProperties(questionBank, questionBankVO);
                JdHotKeyStore.smartSet(key, questionBankVO);
            }
        }
    }
}

