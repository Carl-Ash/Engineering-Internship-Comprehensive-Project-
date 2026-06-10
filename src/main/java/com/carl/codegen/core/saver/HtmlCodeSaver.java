package com.carl.codegen.core.saver;

import cn.hutool.core.util.StrUtil;
import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;

/**
 * HTML 代码保存器
 */
public class HtmlCodeSaver extends CodeSaverTemplate<HtmlResult> {
    @Override
    protected CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HtmlResult result, String parentDir) {
        saveFile(parentDir, "index.html", result.getHtmlCode());
    }

    @Override
    protected void verifyInput(HtmlResult result) {
        super.verifyInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空");
        }
    }
}
