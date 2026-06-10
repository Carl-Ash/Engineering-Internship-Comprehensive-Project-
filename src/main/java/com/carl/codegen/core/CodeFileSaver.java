package com.carl.codegen.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.ai.model.HtmlResult;
import com.carl.codegen.ai.model.MultiFileResult;
import com.carl.codegen.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;
@Deprecated
public class CodeFileSaver {

    // 默认输出目录
    private static final String OUTPUT_ROOT = System.getProperty("user.dir") + "/tmp/code-output";

    /**
     * 保存单个代码文件
     *
     * @param parentDir 父目录
     * @param fileName 文件名
     * @param content 文件内容
     */
    private static void saveFile(String parentDir, String fileName, String content) {
        String filePath = parentDir + File.separator + fileName;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }

    /**
     * 创建唯一输出目录：OUTPUT_ROOT/type_雪花ID
     *
     * @param type 代码生成文件类型
     * @return 唯一目录路径
     */
    private static String createOutputDir(String type) {
        String uniqueDirName = StrUtil.format("{}_{}", type, IdUtil.getSnowflakeNextIdStr());
        String dirPath = OUTPUT_ROOT + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存 HTML 代码文件
     *
     * @param htmlResult HTML 代码结果
     * @return 保存的 HTML 文件
     */
    public static File saveHtmlFile(HtmlResult htmlResult) {
        String parentDir = createOutputDir(CodeGenTypeEnum.HTML.getValue());
        saveFile(parentDir, "index.html", htmlResult.getHtmlCode());
        return new File(parentDir);
    }

    /**
     * 保存多文件代码结果
     *
     * @param multiFileResult 多文件代码结果
     * @return 保存的多文件目录
     */
    public static File saveMultiFile(MultiFileResult multiFileResult) {
        String parentDir = createOutputDir(CodeGenTypeEnum.HTML.getValue());
        saveFile(parentDir, "index.html", multiFileResult.getHtmlCode());
        saveFile(parentDir, "style.css", multiFileResult.getCssCode());
        saveFile(parentDir, "script.js", multiFileResult.getJsCode());
        return new File(parentDir);
    }
}
