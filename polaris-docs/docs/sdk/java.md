# Java 接入示例

在 Java 后端项目中，推荐使用 **LangChain4j** 或 **Spring AI** 的 OpenAI 兼容客户端进行快速对接。

## 方案一：使用 LangChain4j 接入

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.17.0</version>
</dependency>
```

### 2. 流式对话调用示例

请用中台首页接入示例中的实际 Base URL 替换下方占位地址，保留部署的 API 代理前缀，参见[快速开始](/guide/quickstart)。

```java
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class PolarisAiDemo {
    public static void main(String[] args) {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl("https://your-polaris-host/prod-api/platform/api/v1")
                .apiKey("sk-your-api-key-here")
                .modelName("polaris-default")
                .temperature(0.7)
                .build();

        model.generate("请为一家科技初创公司构思 3 个有创意的名字。", new dev.langchain4j.model.StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                System.out.print(token);
            }

            @Override
            public void onComplete(dev.langchain4j.model.output.Response response) {
                System.out.println("\n[生成完毕]");
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
            }
        });
    }
}
```
