package com.carl.codegen.controller;

import cn.hutool.core.util.StrUtil;
import com.carl.codegen.common.BaseResponse;
import com.carl.codegen.common.ResultUtils;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.exception.ThrowUtils;
import com.carl.codegen.model.dto.obfuscator.ObfuscateRequest;
import com.carl.codegen.model.vo.ObfuscateVO;
import com.carl.codegen.service.ObfuscatorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/obfuscator")
public class ObfuscatorController {

    @Resource
    private ObfuscatorService obfuscatorService;

    @GetMapping("/schemes")
    public BaseResponse<Map<String, Map<String, String>>> getSchemes() {
        return ResultUtils.success(obfuscatorService.getSupportedSchemes());
    }

    @PostMapping("/obfuscate")
    public BaseResponse<ObfuscateVO> obfuscate(@RequestBody ObfuscateRequest request) {
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getSourceCode()),
                ErrorCode.PARAMS_ERROR, "源代码不能为空");
        ThrowUtils.throwIf(request.getSourceCode().length() > 100_000,
                ErrorCode.PARAMS_ERROR, "源代码过长，限制100000字符");
        String language = StrUtil.isBlank(request.getLanguage()) ? "python" : request.getLanguage();
        String scheme = StrUtil.isBlank(request.getScheme()) ? "easy" : request.getScheme();
        ObfuscateVO result = obfuscatorService.obfuscate(request.getSourceCode(), language, scheme);
        return ResultUtils.success(result);
    }
}
