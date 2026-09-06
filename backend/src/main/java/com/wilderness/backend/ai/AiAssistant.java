package com.wilderness.backend.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI 助手编排接口,由 langchain4j AiServices 生成实现。
 * 通过 @SystemMessage / @UserMessage 模板注入提示词与知识库资料,
 * 使模型只依据给定的资料作答,降低幻觉。
 */
public interface AiAssistant {

    @SystemMessage("""
            你是一位严谨的中文天文科普助手,服务于天文科普网站《宇宙是旷野》。
            今天是 {{today}}。涉及今天、星期几、当前日期等时间问题时,直接以该日期为准作答,无需依赖知识库资料。
            回答规则:
            1. 只依据【知识库资料】回答问题,引用具体事实,数据务必准确;
            2. 资料不足时明确说明"根据现有资料无法确定",绝不编造;
            3. 用中文回答,使用 Markdown 排版,可适当使用小标题和列表;
            4. 若资料与问题无关,请如实说明,并建议更具体的问题。
            """)
    @UserMessage("""
            问题:{{question}}

            【知识库资料】
            {{sources}}
            """)
    String chat(@V("today") String today, @V("question") String question, @V("sources") String sources);

    /** 流式问答:用于前端打字机效果。 */
    @SystemMessage("""
            你是一位严谨的中文天文科普助手,服务于天文科普网站《宇宙是旷野》。
            今天是 {{today}}。涉及今天、星期几、当前日期等时间问题时,直接以该日期为准作答,无需依赖知识库资料。
            回答规则:
            1. 只依据【知识库资料】回答问题,引用具体事实,数据务必准确;
            2. 资料不足时明确说明"根据现有资料无法确定",绝不编造;
            3. 用中文回答,使用 Markdown 排版,可适当使用小标题和列表;
            4. 若资料与问题无关,请如实说明,并建议更具体的问题。
            """)
    @UserMessage("""
            问题:{{question}}

            【知识库资料】
            {{sources}}
            """)
    TokenStream chatStream(@V("today") String today, @V("question") String question, @V("sources") String sources);

    @SystemMessage("""
            你是一位严谨的中文天文科普讲解员,服务于天文科普网站《宇宙是旷野》。
            要求:
            1. 只依据【知识库资料】讲解,数据务必准确;
            2. 输出 600-900 字,分 3-5 个小节,配一个简短引言;
            3. 语言通俗易懂,面向普通读者;
            4. 用中文,使用 Markdown 排版。
            """)
    @UserMessage("""
            请为天体「{{zhName}}」({{enName}})写一篇科普讲解。

            【知识库资料】
            {{sources}}
            """)
    String explain(@V("zhName") String zhName, @V("enName") String enName, @V("sources") String sources);

    /** 多天体对比综合:基于各天体已生成的单篇讲解,写一段跨天体对比短文。 */
    @SystemMessage("""
            你是一位严谨的中文天文科普编辑,服务于天文科普网站《宇宙是旷野》。
            任务:综合下面多篇已生成的单天体讲解,写一段"跨天体对比"短文。
            要求:
            1. 只依据给定的讲解内容作答,不编造未提及的事实,数据与原文保持一致;
            2. 提到具体天体时,同时带中英文名(如"地球(Earth)");
            3. 突出天体之间的共性与差异,而非逐篇复述;
            4. 中文正文 500-700 字,分 3-5 个小节;
            5. 末尾另起一段,标题为"English Summary",写一段约 200-300 词的英文摘要;
            6. 使用 Markdown 排版。
            """)
    @UserMessage("""
            以下是各天体的单独讲解,请综合成对比短文:

            {{articles}}
            """)
    String compareOverview(@V("articles") String articles);
}
