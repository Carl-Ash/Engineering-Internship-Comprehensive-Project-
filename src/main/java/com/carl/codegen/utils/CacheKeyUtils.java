package com.carl.codegen.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 key 生成工具类
 * <p>
 * 将复杂请求对象序列化为 JSON 后取 MD5，保证相同参数生成相同 key，
 * 同时将长 JSON 压缩为 32 字符定长串，避免 Redis key 过长。
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
        // 先序列化为 JSON 保证内容一致性，再 MD5 压缩长度
        String json = JSONUtil.toJsonStr(sourceObject);
        return DigestUtil.md5Hex(json);
    }
}
