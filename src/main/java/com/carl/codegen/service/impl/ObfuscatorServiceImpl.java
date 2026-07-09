package com.carl.codegen.service.impl;

import cn.hutool.core.util.StrUtil;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import com.carl.codegen.model.vo.ObfuscateVO;
import com.carl.codegen.service.ObfuscatorService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ObfuscatorServiceImpl implements ObfuscatorService {

    @Value("${obfuscator.python-path:python}")
    private String pythonPath;

    @Value("${obfuscator.node-path:node}")
    private String nodePath;

    @Value("${obfuscator.base-dir:obfuscator}")
    private String baseDir;

    @Value("${obfuscator.timeout-seconds:30}")
    private int timeoutSeconds;

    private final Map<String, ScriptConfig> scriptRegistry = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        String base = baseDir;

        // Python schemes
        scriptRegistry.put("python:easy",
                new ScriptConfig(base + "/standalone.py", ".py", ScriptType.STDOUT));
        scriptRegistry.put("python:diff",
                new ScriptConfig(base + "/python/py_obf_diff.py", ".py", ScriptType.FILE_IO));
        scriptRegistry.put("python:baseline",
                new ScriptConfig(base + "/python/baseline_wrapper.py", ".py", ScriptType.FILE_IO));

        // C schemes
        scriptRegistry.put("c:easy",
                new ScriptConfig(base + "/c/c_obf_easy.py", ".c", ScriptType.FILE_POS));
        scriptRegistry.put("c:diff",
                new ScriptConfig(base + "/c/c_obf_diff.py", ".c", ScriptType.FILE_POS));

        // JavaScript schemes (via Node.js)
        scriptRegistry.put("javascript:easy",
                new ScriptConfig(base + "/js/js_obfuscator.js", ".js", ScriptType.FILE_IO));
        scriptRegistry.put("javascript:diff",
                new ScriptConfig(base + "/js/js_obfuscator.js", ".js", ScriptType.FILE_IO));

        log.info("Obfuscator script registry initialized with {} entries", scriptRegistry.size());
    }

    @Override
    public Map<String, Map<String, String>> getSupportedSchemes() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptConfig> entry : scriptRegistry.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String language = parts[0];
            String scheme = parts[1];
            result.computeIfAbsent(language, k -> new LinkedHashMap<>())
                    .put(scheme, getSchemeLabel(language, scheme));
        }
        return result;
    }

    private String getSchemeLabel(String language, String scheme) {
        if ("diff".equals(scheme)) return "强混淆";
        if ("baseline".equals(scheme)) return "基础混淆";
        if ("easy".equals(scheme)) return "基础混淆";
        return scheme;
    }

    @Override
    public ObfuscateVO obfuscate(String sourceCode, String language, String scheme) {
        if (StrUtil.isBlank(sourceCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "源代码不能为空");
        }
        if (sourceCode.length() > 100_000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "源代码过长，限制100000字符");
        }

        String key = (language != null ? language.toLowerCase() : "python") + ":"
                + (scheme != null ? scheme.toLowerCase() : "easy");
        ScriptConfig config = scriptRegistry.get(key);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的混淆方案: " + language + ":" + scheme);
        }

        // 解析脚本路径
        Path scriptPath = resolveScriptPath(config.scriptPath);

        File tempInput = null;
        File tempOutput = null;
        try {
            // 写入临时输入文件
            tempInput = File.createTempFile("obf_input_", config.fileExt);
            Files.write(tempInput.toPath(), sourceCode.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            tempInput.deleteOnExit();

            String runtime = getRuntimeForScript(config.scriptPath);
            String command;
            if (config.scriptType == ScriptType.STDOUT) {
                // standalone.py: python script.py <input> → stdout
                command = String.format("%s \"%s\" \"%s\"",
                        runtime, scriptPath.toAbsolutePath(),
                        tempInput.getAbsolutePath());
            } else if (config.scriptType == ScriptType.FILE_IO) {
                // py_obf_diff.py / baseline_wrapper / js_obfuscator.js: <runtime> script -i <input> -o <output>
                tempOutput = File.createTempFile("obf_output_", config.fileExt);
                tempOutput.deleteOnExit();
                command = String.format("%s \"%s\" -i \"%s\" -o \"%s\"",
                        runtime, scriptPath.toAbsolutePath(),
                        tempInput.getAbsolutePath(), tempOutput.getAbsolutePath());
            } else {
                // c_obf_*.py: python script.py <input> -o <output>
                tempOutput = File.createTempFile("obf_output_", config.fileExt);
                tempOutput.deleteOnExit();
                command = String.format("%s \"%s\" \"%s\" -o \"%s\"",
                        runtime, scriptPath.toAbsolutePath(),
                        tempInput.getAbsolutePath(), tempOutput.getAbsolutePath());
            }

            log.info("执行混淆命令: {}", command);
            String output = executeCommand(command);

            String obfuscatedCode;
            if (config.scriptType == ScriptType.STDOUT) {
                obfuscatedCode = output;
            } else {
                obfuscatedCode = Files.readString(tempOutput.toPath(), StandardCharsets.UTF_8);
            }

            if (StrUtil.isBlank(obfuscatedCode)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "混淆结果为空");
            }

            return new ObfuscateVO(obfuscatedCode);

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("混淆IO异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "混淆服务异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "混淆被中断");
        } finally {
            if (tempInput != null && tempInput.exists()) {
                tempInput.delete();
            }
            if (tempOutput != null && tempOutput.exists()) {
                tempOutput.delete();
            }
        }
    }

    private String getRuntimeForScript(String scriptPath) {
        if (scriptPath.endsWith(".js")) {
            return nodePath;
        }
        return pythonPath;
    }

    private Path resolveScriptPath(String scriptPath) {
        Path path = Path.of(scriptPath);
        if (Files.exists(path)) {
            return path;
        }
        String userDir = System.getProperty("user.dir");
        path = Path.of(userDir, scriptPath);
        if (Files.exists(path)) {
            return path;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "混淆脚本未找到: " + scriptPath);
    }

    private String executeCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) output.append("\n");
                output.append(line);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "混淆超时，请尝试更短的代码");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.error("混淆脚本执行失败, exitCode={}", exitCode);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "混淆失败: " + output.toString());
        }
        return output.toString();
    }

    // --- inner types ---

    private enum ScriptType {
        STDOUT,    // 输出到stdout
        FILE_IO,   // -i <input> -o <output>
        FILE_POS   // <input> -o <output>
    }

    private static class ScriptConfig {
        final String scriptPath;
        final String fileExt;
        final ScriptType scriptType;

        ScriptConfig(String scriptPath, String fileExt, ScriptType scriptType) {
            this.scriptPath = scriptPath;
            this.fileExt = fileExt;
            this.scriptType = scriptType;
        }
    }
}
