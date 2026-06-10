package com.carl.codegen.core.saver;

import cn.hutool.core.util.StrUtil;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器
 */
public class MultiFileCodeSaver extends CodeSaverTemplate<MultiFileResult> {
    @Override
    protected CodeGenTypeEnum getType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileResult result, String parentDir) {
        // 保存HTML文件
        saveFile(parentDir, "index.html", result.getHtmlCode());
        // 保存CSS文件
        saveFile(parentDir, "style.css", result.getCssCode());
        // 保存JS文件
        saveFile(parentDir, "script.js", result.getJsCode());
    }

    @Override
    protected void verifyInput(MultiFileResult result) {
        super.verifyInput(result);
        // 至少有 HTML 文件
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空");
        }
    }
}
