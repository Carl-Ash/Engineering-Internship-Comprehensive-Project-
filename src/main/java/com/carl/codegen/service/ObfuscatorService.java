package com.carl.codegen.service;

import com.carl.codegen.model.vo.ObfuscateVO;

import java.util.Map;

public interface ObfuscatorService {

    ObfuscateVO obfuscate(String sourceCode, String language, String scheme);

    Map<String, Map<String, String>> getSupportedSchemes();
}
