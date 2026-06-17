package com.carl.codegen.core.saver;

import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;

import java.io.File;

public class CodeSaverExe {

    private static final HtmlCodeSaver htmlCodeSaver = new HtmlCodeSaver();

    private static final MultiFileCodeSaver multiFileCodeSaver = new MultiFileCodeSaver();

    /**
     * 执行代码保存
     *
     * @param result 代码生成结果对象
     * @param type   代码生成类型
     * @param appId  应用 id
     * @return 保存后的文件对象
     */
    public static File executeSaver(Object result, CodeGenTypeEnum type, Long appId) {
        return switch (type) {
            case HTML -> htmlCodeSaver.save((HtmlResult) result, appId);
            case MULTI_FILE -> multiFileCodeSaver.save((MultiFileResult) result, appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + type);
        };
    }
}
