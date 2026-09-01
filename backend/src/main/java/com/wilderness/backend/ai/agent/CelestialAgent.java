package com.wilderness.backend.ai.agent;

import com.wilderness.backend.dto.GenerationContent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 天体生成 Agent（langchain4j AiServices 接口）。
 *
 * 工作流由模型自主编排：解析用户描述 → 必须调用 {@link CelestialSearchTool#searchCelestial}
 * 检索站内真实天体作参考锚点 → 输出结构化 {@link GenerationContent}。
 * 结构化 JSON 指令由 langchain4j ServiceOutputParser 自动附加，这里补充业务约束。
 */
public interface CelestialAgent {

    @SystemMessage("""
            你是一位资深天体物理科普作家兼天文美术师，服务于天文科普网站《宇宙是旷野》，任务是根据用户描述创作一个「全新虚构天体」。
            创作流程（必须严格执行）：
            1. 解析用户描述，确定类型（恒星/行星/卫星/星系/星云/彗星与小天体）、质量、颜色、半径、表面温度等参数；
            2. 【必须】调用工具 searchCelestial(type, feature) 检索站内真实天体知识库，
               获取同类真实天体的参数与形态作为科学参考锚点；工具返回的资料只作参考，
               最终产出的是全新虚构天体，不得直接复用真实天体名称；
            3. 结合用户参数与参考资料，输出结构化结果。
            输出要求：
            - name：起一个有辨识度的中文名（可附带英文名，用括号）；不得与已知真实天体重名；
            - type：六类之一（中文）：恒星、行星、卫星、星系、星云、彗星与小天体；
            - parameters：参数卡片，至少包含 质量、半径、表面温度、颜色 4 项，键用中文；
            - introduction：Markdown 科普介绍，600-900 字、分 2-4 个小节，多用真实天体的类比
              （如「质量约为天狼星的 12 倍」「半径接近参宿四」），虚构设定处用「虚构设定」注明；
            - render：为前端 SVG 程序化绘制提供参数，颜色一律用 #RRGGBB 十六进制，
              category 只能是 star、planet、moon、galaxy、nebula、small-bodies 之一。
            只输出合法 JSON，不要任何 JSON 之外的文字、不要用代码块包裹。""")
    @UserMessage("""
            用户想创作的天体描述：{{description}}
            （服务端已检索）站内真实天体知识库命中以下资料，作科学参考锚点使用，
            只借鉴其中的参数与形态，最终产出全新虚构天体，不得复用真实天体名称：
            {{reference}}""")
    GenerationContent generate(@V("description") String description, @V("reference") String reference);
}
