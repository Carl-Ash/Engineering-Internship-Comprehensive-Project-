package com.carl.codegen.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器模板类
 */
public abstract class CodeSaverTemplate<T> {

    // 默认输出目录
    private static final String OUTPUT_ROOT = System.getProperty("user.dir") + "/tmp/code-output";

    /**
     * 保存代码文件的模板方法
     *
     * @param result 代码生成结果
     * @param appId 应用 id
     * @return 保存的文件目录
     */
    @SuppressWarnings("unchecked")
    public final File save(T result, Long appId) {
        // 验证输入
        verifyInput(result);
        // 构建唯一目录
        String parentDir = createOutputDir(appId);
        // 保存文件，子类实现
        saveFiles(result, parentDir);
        // 返回文件目录
        return new File(parentDir);
    }

    /**
     * 验证输入参数，可由子类重写
     *
     * @param result 代码生成结果
     */
    @SuppressWarnings("unchecked")
    protected void verifyInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成结果不能为空");
        }
    }

    /**
     * 创建唯一输出目录：OUTPUT_ROOT/type_雪花ID
     *
     * @param appId 应用 id
     * @return 唯一目录路径
     */
    protected String createOutputDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用 id 不能为空");
        }
        String type = getType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", type, appId);
        String dirPath = OUTPUT_ROOT + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个代码文件
     *
     * @param parentDir 父目录
     * @param fileName 文件名
     * @param content 文件内容
     */
    public final void saveFile(String parentDir, String fileName, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = parentDir + File.separator + fileName;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取代码生成文件类型
     *
     * @return 代码生成文件类型
     */
    protected abstract CodeGenTypeEnum getType();

    /**
     * 保存代码文件
     *
     * @param result 代码生成结果
     * @param parentDir 父目录
     */
    protected abstract void saveFiles(T result, String parentDir);
}
