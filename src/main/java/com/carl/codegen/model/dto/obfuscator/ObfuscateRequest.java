package com.carl.codegen.model.dto.obfuscator;

import lombok.Data;

import java.io.Serializable;

@Data
public class ObfuscateRequest implements Serializable {

    private String sourceCode;

    private String language;

    private String scheme;

    private static final long serialVersionUID = 1L;
}
