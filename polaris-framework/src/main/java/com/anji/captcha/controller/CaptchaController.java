package com.anji.captcha.controller;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.utils.ip.IpUtils;
import com.polaris.system.service.ISysConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 覆盖 AJ-Captcha 包中默认的 CaptchaController 以适配 Spring Boot 3 (Jakarta.servlet)
 * 
 * @author polaris
 */
@RestController("ajCaptchaController")
@RequestMapping
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private ISysConfigService configService;

    /**
     * 获取验证码开启状态（适配原有前端逻辑，替换已删除的 CaptchaController）
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCaptchaEnabled() {
        AjaxResult ajax = AjaxResult.success();
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        ajax.put("captchaEnabled", captchaEnabled);
        return ajax;
    }

    @PostMapping("/captcha/get")
    public ResponseModel get(@RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(IpUtils.getIpAddr(request));
        return captchaService.get(data);
    }

    @PostMapping("/captcha/check")
    public ResponseModel check(@RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(IpUtils.getIpAddr(request));
        return captchaService.check(data);
    }
}
