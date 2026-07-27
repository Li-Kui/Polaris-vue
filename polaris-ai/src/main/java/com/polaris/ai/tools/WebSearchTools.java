package com.polaris.ai.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.polaris.ai.tools.base.AiAgentTool;
import com.polaris.ai.tools.base.AiTool;
import com.polaris.ai.utils.SearchKeyHolder;
import com.polaris.ai.utils.ToolSseHolder;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 联网搜索工具，通过原生 HTTP 调用 Tavily API 实现免除 Maven 外部依赖下载冲突的困扰
 * 
 * @author polaris
 */
@Component
@AiAgentTool("联网搜索接口")
public class WebSearchTools implements AiTool {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    @Tool("当用户需要获取最新的网络实时信息、新闻、天气或进行事实核对时，调用此工具搜索互联网")
    public String searchWeb(String query) {
        log.info(">>> [WebSearchTools] 触发联网搜索工具, query: {}", query);
        sendSse("status", "正在联网搜索...");
        String apiKey = SearchKeyHolder.get();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn(">>> [WebSearchTools] 未配置 Tavily API Key，联网搜索跳过");
            sendSse("status", "");
            return "联网搜索不可用：当前选中的大模型配置中未填写“联网搜索 API Key”(Tavily Key)，请先联系管理员在模型配置中配置。";
        }

        try {
            // 组装 Tavily 请求参数
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("api_key", apiKey.trim());
            requestMap.put("query", query);
            requestMap.put("search_depth", "basic");
            requestMap.put("include_answer", false);
            String jsonRequestBody = JSON.toJSONString(requestMap);

            // 发起 HTTP 请求
            URL url = new URL(TAVILY_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonRequestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error(">>> [WebSearchTools] Tavily API 请求失败, HTTP Code: {}", responseCode);
                return "联网搜索调用失败，HTTP 错误码: " + responseCode;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // 解析 Tavily 结果
            JSONObject responseJson = JSON.parseObject(response.toString());
            JSONArray resultsArray = responseJson.getJSONArray("results");
            if (resultsArray == null || resultsArray.isEmpty()) {
                sendSse("status", "未搜索到相关联网内容");
                return "未搜索到相关联网内容。";
            }

            JSONArray sources = new JSONArray();
            StringBuilder formattedResult = new StringBuilder("已从互联网上搜索到以下相关信息：\n\n");
            for (int i = 0; i < resultsArray.size(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);
                String title = item.getString("title");
                String itemUrl = item.getString("url");
                String content = item.getString("content");

                JSONObject source = new JSONObject();
                source.put("index", i + 1);
                source.put("title", title);
                source.put("url", itemUrl);
                source.put("snippet", content);
                sources.add(source);

                formattedResult.append(String.format("[%d] 标题: %s\n链接: %s\n内容: %s\n\n", 
                        i + 1, title, itemUrl, content));
            }

            JSONObject payload = new JSONObject();
            payload.put("query", query);
            payload.put("count", sources.size());
            payload.put("sources", sources);
            sendSse("search_sources", payload.toJSONString());
            sendSse("status", "已搜索 " + sources.size() + " 个网页");

            log.info(">>> [WebSearchTools] 联网搜索成功，已将网页片段喂给大模型进行提炼...");
            return formattedResult.toString();

        } catch (Exception e) {
            log.error(">>> [WebSearchTools] 联网搜索出现异常: ", e);
            sendSse("status", "联网搜索执行失败");
            return "联网搜索执行失败: " + e.getMessage();
        }
    }

    private void sendSse(String event, String data) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = ToolSseHolder.get();
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.warn(">>> [WebSearchTools] SSE 推送搜索状态失败: {}", event);
        }
    }
}
