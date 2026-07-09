package com.carl.codegen.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObfuscateVO implements Serializable {

    private String obfuscatedCode;

    private static final long serialVersionUID = 1L;
}
