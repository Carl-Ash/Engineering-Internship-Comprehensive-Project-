package com.carl.codegen.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.carl.codegen.exception.BusinessException;
import com.carl.codegen.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 网页截图工具
 * <p>
 * 基于 Selenium + 无头 Edge 实现服务端网页截图，
 * Selenium 4.x 内置 Selenium Manager，自动发现并管理 Edge 驱动。
 */
@Slf4j
public class ScreenShotUtils {

    private static volatile WebDriver driver;

    /**
     * 获取浏览器驱动实例（懒加载，双重检查）
     */
    private static WebDriver getDriver() {
        if (driver == null) {
            synchronized (ScreenShotUtils.class) {
                if (driver == null) {
                    driver = initEdgeDriver(1600, 900);
                }
            }
        }
        return driver;
    }

    /**
     * 释放浏览器驱动资源
     */
    @PreDestroy
    public void destroy() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    /**
     * 初始化无头 Edge 浏览器驱动
     * <p>
     * 适用于服务端截图场景，默认开启以下配置：
     * <ul>
     *   <li>无头模式 — 无 GUI 运行，适配服务器环境</li>
     *   <li>禁用 GPU / 沙盒 / 共享内存 — 保证 Docker 容器兼容性</li>
     *   <li>固定视口 1600×900 — 保证截图尺寸一致</li>
     *   <li>页面加载超时 30s，隐式等待 10s — 避免渲染卡死</li>
     * </ul>
     *
     * @param width  浏览器视口宽度
     * @param height 浏览器视口高度
     * @return 初始化完成的 Edge WebDriver 实例
     */
    /**
     * 驱动文件常见搜索路径
     */
    private static final List<String> KNOWN_DRIVER_PATHS = List.of(
            System.getProperty("user.dir") + "/driver/msedgedriver.exe",
            System.getProperty("user.dir") + "/msedgedriver.exe",
            "C:/Program Files (x86)/Microsoft/Edge/Application/msedgedriver.exe",
            "C:/Program Files (x86)/Microsoft/Edge/Application/149.0.4022.98/msedgedriver.exe",
            "C:/Program Files/Microsoft/Edge/Application/msedgedriver.exe"
    );

    /**
     * 查找本地 msedgedriver 可执行文件
     */
    private static String findLocalDriver() {
        // 优先使用系统属性或环境变量显式指定的路径
        String explicitPath = System.getProperty("webdriver.edge.driver");
        if (StrUtil.isNotBlank(explicitPath) && Files.exists(Path.of(explicitPath))) {
            log.info("使用指定驱动路径：{}", explicitPath);
            return explicitPath;
        }
        // 搜索已知常见路径
        for (String path : KNOWN_DRIVER_PATHS) {
            if (Files.exists(Path.of(path))) {
                log.info("自动发现本地驱动：{}", path);
                return path;
            }
        }
        return null;
    }

    private static WebDriver initEdgeDriver(int width, int height) {
        try {
            String localDriver = findLocalDriver();
            if (localDriver != null) {
                System.setProperty("webdriver.edge.driver", localDriver);
            }

            EdgeOptions options = new EdgeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            WebDriver driver = new EdgeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            log.info("Edge 浏览器驱动初始化成功");
            return driver;
        } catch (Exception e) {
            log.error("初始化 Edge 浏览器驱动失败", e);
            log.error("请手动下载 msedgedriver.exe 并放置到以下任一位置：{}", KNOWN_DRIVER_PATHS);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Edge 浏览器驱动失败，请检查日志获取解决方式");
        }
    }

    /**
     * 保存截图到指定路径
     *
     * @param screenshotBytes 截图字节数组
     * @param filePath        保存路径
     */
    private static void saveScreenshot(byte[] screenshotBytes, String filePath) {
        try {
            FileUtil.writeBytes(screenshotBytes, filePath);
        } catch (Exception e) {
            log.error("保存截图失败，路径：{}", filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存截图失败");
        }
    }

    /**
     * 压缩截图
     *
     * @param filePath       原始截图路径
     * @param compressedPath 压缩后截图路径
     */
    private static void compressScreenshot(String filePath, String compressedPath) {
        final float compressionRatio = 0.1f;
        try {
            ImgUtil.compress(
                    FileUtil.file(filePath),
                    FileUtil.file(compressedPath),
                    compressionRatio
            );
        } catch (Exception e) {
            log.error("压缩截图失败，路径：{}", filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩截图失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     * @param driver 浏览器驱动实例
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(d -> ((JavascriptExecutor) d)
                    .executeScript("return document.readyState")
                    .equals("complete"));
            Thread.sleep(1000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载完成异常：{}，继续执行截图操作", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "等待页面加载完成异常");
        }
    }

    /**
     * 保存当前页面截图到指定路径
     *
     * @param url 目标 URL 地址
     * @return 保存的截图路径
     */
    public static String saveWebPageScreenshot(String url) {
        if (StrUtil.isBlank(url)) {
            log.error("URL 不能为空");
            return null;
        }
        try {
            String rootPath = System.getProperty("user.dir") + "/tmp/screenshots/" + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);

            final String IMAGE_SUFFIX = ".png";
            String imagePath = rootPath + File.separator + RandomUtil.randomNumbers(8) + IMAGE_SUFFIX;

            WebDriver driver = getDriver();
            driver.get(url);
            waitForPageLoad(driver);

            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveScreenshot(screenshotBytes, imagePath);
            log.info("截图保存成功，路径：{}", imagePath);

            final String COMPRESSED_IMAGE_SUFFIX = "_compressed.png";
            String compressedPath = rootPath + File.separator + RandomUtil.randomNumbers(8) + COMPRESSED_IMAGE_SUFFIX;
            compressScreenshot(imagePath, compressedPath);
            log.info("截图压缩成功，路径：{}", compressedPath);

            FileUtil.del(imagePath);
            return compressedPath;
        } catch (Exception e) {
            log.error("保存当前页面截图失败", e);
            return null;
        }
    }
}
