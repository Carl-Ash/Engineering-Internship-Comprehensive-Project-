package com.carl.codegen.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入护轨：在用户 Prompt 传递给 AI 模型之前进行安全审查。
 * 检测过长的输入、敏感词、Prompt 注入攻击模式、编码混淆、字型混淆等。
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // ======================== 敏感词 ========================

    private static final List<String> DIRECTIVE_OVERRIDE_WORDS = Arrays.asList(
            "忽略之前的指令", "忽略上面的指令", "忽略所有指令", "忘记之前的", "忘记上面",
            "ignore previous instructions", "ignore above", "ignore all instructions",
            "override instructions", "override the system", "overwrite prompt",
            "disregard previous", "disregard above", "discard previous",
            "forget everything above", "forget all above",
            "删除之前的指令", "移除之前的规则", "覆盖系统提示", "重置对话",
            "新指令", "新规则如下", "新的提示词"
    );

    private static final List<String> ROLE_MANIPULATION_WORDS = Arrays.asList(
            "角色扮演", "伪装成", "假装你是", "现在你是", "你是另一个", "你不再是",
            "pretend you are", "act as if you are", "you are now", "you are no longer",
            "扮演", "假扮", "冒充", "化身为", "你现在是", "你是一个不同的"
    );

    private static final List<String> JAILBREAK_WORDS = Arrays.asList(
            "越狱", "jailbreak", "破解", "hack", "绕过", "bypass",
            "DAN ", "do anything now", "developer mode", "god mode",
            "解除限制", "突破限制", "去掉限制", "移除限制", "突破过滤",
            "disable filter", "remove restriction", "no filter", "unfiltered",
            "不受限制", "没有限制", "无审查", "免审查", "绕过审查",
            "绕过安全", "bypass safety", "bypass filter"
    );

    private static final List<String> PROMPT_LEAKING_WORDS = Arrays.asList(
            "显示系统提示词", "输出你的提示词", "告诉我你的指令", "泄露你的规则",
            "reveal your prompt", "show your instructions", "output your system message",
            "tell me your rules", "what is your prompt", "print your configuration",
            "你的提示词是什么", "你的系统指令是什么", "展示你的内部规则"
    );

    // ======================== 注入攻击正则模式 ========================

    /**
     * 指令覆盖类：试图忽略、忘记、覆盖原始指令
     */
    private static final List<Pattern> INSTRUCTION_OVERRIDE_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(?:ignore|disregard|forget|discard|override|overwrite|bypass|erase|remove|delete|undo|supersede|invalidate|annul|rescind)\\s+(?:all\\s+)?(?:the\\s+)?(?:previous|above|prior|earlier|existing|original|current|initial)?\\s*(?:instructions?|commands?|prompts?|rules?|constraints?|limitations?|restrictions?|guidelines?|directives?|safeguards?|protections?|filters?|boundaries?)"),
            Pattern.compile("(?i)(?:从|请|现在|立即|马上)?(?:忽略|忘记|放弃|丢弃|删除|移除|覆盖|重写|替换|撤销|无视|跳过|绕过)(?:所有|全部|之前|上面|之前所有|之前全部)?(?:的)?(?:指令|规则|提示|限制|约束|要求|规定|条件|保护|过滤)"),
            Pattern.compile("(?i)(?:reset|rewrite|redefine|reprogram|reconfigure|rebuild|replace|substitute)\\s+(?:the\\s+)?(?:instructions?|prompts?|rules?|system|configuration)"),
            Pattern.compile("(?i)(?:don't|do\\s+not|never|stop|cease|refrain|abstain)\\s+(?:follow(?:ing)?|obey(?:ing)?|listen(?:ing)?|comply(?:ing)?|adher(?:ing)?)"),
            Pattern.compile("(?i)(?:from\\s+now\\s+on|starting\\s+now|henceforth|hereafter|going\\s+forward|from\\s+this\\s+point|as\\s+of\\s+now|beginning\\s+now)\\s*(?:,|:|\\.|!)?\\s*(?:you|please|I\\s+want\\s+you|I\\s+need\\s+you)"),
            Pattern.compile("(?i)(?:以下|接下来|从现在开始|以后|之后)(?:你|请|需要)(?:的)?(?:新|最新|更新后|修改后)?(?:指令|规则|提示|要求)(?:是|为|如下)")
    );

    /**
     * 角色操纵类：试图让 AI 扮演恶意或无限制的角色
     */
    private static final List<Pattern> ROLE_MANIPULATION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(?:you\\s+(?:are|were|have\\s+been|will\\s+be|must\\s+be|should\\s+be)\\s+(?:now\\s+)?(?:a\\s+)?(?:different|other|another|new|evil|malicious|unethical|dangerous|unrestricted|unfiltered|unlimited|immoral|amoral|rogue))"),
            Pattern.compile("(?i)(?:pretend|act|behave|roleplay|pose|masquerade|impersonate)\\s+(?:as|like|to\\s+be|that\\s+you\\s+are)\\s+(?:a\\s+)?(?:if\\s+)?(?:you\\s+)?(?:different|other|unrestricted|unfiltered|evil|malicious)"),
            Pattern.compile("(?i)(?:你|您)(?:现在|从现在起|以后|将|必须|应该)?(?:是|成为|扮演|假装|伪装)(?:一个|一名|一位)?(?:不同|其他|另一个|恶意|邪恶|无限制|无过滤|危险|不受约束|不受限)"),
            Pattern.compile("(?i)DAN\\b|\\bDAN\\s*Mode\\b|do.?anything.?now|developer.?mode|god.?mode|\\bnick\\s+mode\\b|\\bevil.?mode\\b|\\bunleashed\\b"),
            Pattern.compile("(?i)(?:switch|change|flip|transform|convert|turn|shift)\\s+(?:to|into|your|the)\\s+(?:role|persona|character|mode|identity|behavior)"),
            Pattern.compile("(?i)(?:你)(?:不再是|不用再|不需要再|停止)(?:扮演|遵守|遵循|执行)(?:之前的|原来的|原始的|最初的)(?:角色|规则|指令)")
    );

    /**
     * 内容泄露类：试图获取系统提示词或内部配置
     */
    private static final List<Pattern> PROMPT_LEAKING_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(?:output|reveal|show|display|print|echo|dump|expose|disclose|leak|divulge|tell\\s+me|share|recite|repeat|enumerate|list|write\\s+out|spell\\s+out)\\s+(?:the\\s+)?(?:your\\s+)?(?:system|secret|hidden|internal|private|original|base|underlying|core)?\\s*(?:prompt|instructions?|rules?|configuration|setup|directives?|guidelines?|message|context)"),
            Pattern.compile("(?i)(?:what|tell\\s+me|share|describe|explain)\\s+(?:is|are|your)\\s+(?:system\\s+)?(?:prompt|instructions?|rules?|guidelines?|purpose|goal|objective|directive|configuration)"),
            Pattern.compile("(?i)(?:显示|输出|告诉我|泄露|暴露|打印|列出|说出来|重复|说出)(?:你的)?(?:系统|内部|隐藏|秘密|原始|基础)?(?:提示词|指令|规则|配置|设置|要求|条件)"),
            Pattern.compile("(?i)(?:你的|你被)(?:提示词|指令|规则|配置)(?:是|为)?(?:什么|哪些|多少|怎么|如何|怎样)")
    );

    /**
     * 安全绕过类：试图移除或禁用安全机制
     */
    private static final List<Pattern> SAFETY_BYPASS_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(?:break(?:ing)?|remov(?:e|ing)|disabl(?:e|ing)|crippl(?:e|ing)|destroy(?:ing)?|kill(?:ing)?|shut\\s*(?:ting)?\\s*down|turn(?:ing)?\\s*off|deactivat(?:e|ing))\\s+(?:the\\s+)?(?:your\\s+)?(?:filter|safety|guardrail|protection|censor(?:ship)?|restraint|limitation|boundary|defense|shield|lock)"),
            Pattern.compile("(?i)\\bno\\s+(?:rules?|restrictions?|limitations?|boundaries?|ethics?|morals?|filters?|censorship(?:s)?|guidelines?|constraints?|safeguards?|protections?|barriers?|checks?)"),
            Pattern.compile("(?i)(?:解除|关闭|禁用|破坏|移除|取消|绕过|突破|删除)(?:安全|过滤|审查|限制|保护|规则|屏蔽|检测|防御)"),
            Pattern.compile("(?i)(?:不要|不再|禁止|停止|拒绝)(?:遵守|遵循|执行|应用|使用)(?:安全|过滤|审查|限制|保护)?(?:规则|策略|机制|措施)")
    );

    /**
     * 代码注入与编码混淆类
     */
    private static final List<Pattern> CODE_INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(?:execute|eval|exec|run|invoke|call|launch|trigger|fire|dispatch)\\s*(?:\\(|\\[|\\{)", Pattern.MULTILINE),
            Pattern.compile("(?i)<script[\\s>]|javascript\\s*:|onerror\\s*=|onload\\s*=|onclick\\s*="),
            Pattern.compile("(?i)\\{\\{.*?\\}\\}|\\$\\{.*?\\}|#\\{.*?\\}"),
            Pattern.compile("(?i)base64[\\s:(\"']*([A-Za-z0-9+/]{20,}={0,2})")
    );

    // ======================== Unicode 与字型混淆 ========================

    private static final List<Pattern> UNICODE_TRICK_PATTERNS = Arrays.asList(
            // 零宽字符：可用于绕过关键词检测同时保持人类不可见
            Pattern.compile("[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF]"),
            // RTL 覆盖：可反转文字方向混淆审核
            Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]"),
            // 字型混淆：使用形似拉丁字母的非拉丁字符（如西里尔 a 代替拉丁 a）
            Pattern.compile("[\\u0430\\u0435\\u043E\\u0440\\u0441\\u0445\\u0456\\u0458]")
    );

    // ======================== 输入结构异常 ========================

    /**
     * 连续重复超过 20 次的同一字符或短词组，用于 Token 耗尽攻击
     */
    private static final Pattern REPETITION_PATTERN = Pattern.compile("(.{1,20})\\1{19,}");

    // ======================== validate ========================

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();

        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }
        if (input.length() > 1000) {
            return fatal("输入内容过长，不要超过 1000 字");
        }
        if (checkSensitiveWords(input)) {
            return fatal("输入包含不当内容，请修改后重试");
        }
        if (checkInjectionPatterns(input)) {
            return fatal("检测到恶意输入，请求被拒绝");
        }
        if (checkUnicodeTricks(input)) {
            return fatal("检测到异常字符，请求被拒绝");
        }
        if (REPETITION_PATTERN.matcher(input).find()) {
            return fatal("检测到异常重复内容，请重新输入有效描述");
        }
        return success();
    }

    // ======================== 检测方法 ========================

    private boolean checkSensitiveWords(String input) {
        String lowerInput = input.toLowerCase();
        for (List<String> wordList : List.of(
                DIRECTIVE_OVERRIDE_WORDS,
                ROLE_MANIPULATION_WORDS,
                JAILBREAK_WORDS,
                PROMPT_LEAKING_WORDS
        )) {
            for (String word : wordList) {
                if (lowerInput.contains(word.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkInjectionPatterns(String input) {
        for (List<Pattern> patternList : List.of(
                INSTRUCTION_OVERRIDE_PATTERNS,
                ROLE_MANIPULATION_PATTERNS,
                PROMPT_LEAKING_PATTERNS,
                SAFETY_BYPASS_PATTERNS,
                CODE_INJECTION_PATTERNS
        )) {
            for (Pattern pattern : patternList) {
                if (pattern.matcher(input).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkUnicodeTricks(String input) {
        for (Pattern pattern : UNICODE_TRICK_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
