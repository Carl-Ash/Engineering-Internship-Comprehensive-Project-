package com.carl.codegen.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 key 生成工具类
 *
 */
public class CacheKeyUtils {

    /**
     * 将对象序列化为JSON后取MD5，生成固定长度的缓存键
     *
     * @param sourceObject 请求参数对象
     * @return MD5 哈希后的缓存键
     */
    public static String generateCacheKey(Object sourceObject) {
        if (sourceObject == null) {
            return DigestUtil.md5Hex("null");
        }
        String json = JSONUtil.toJsonStr(sourceObject);
        return DigestUtil.md5Hex(json);
    }
}
