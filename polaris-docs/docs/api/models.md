# 模型列表 (List Models)

获取当前租户可供调用的全部大语言模型配置列表。

### 接口信息

- **请求方式**: `GET`
- **请求路径**: `/platform/api/v1/models`
- **认证方式**: `X-API-Key: sk-xxx`

### 响应参数

| 字段名 | 类型 | 描述 |
|:---|:---|:---|
| `object` | string | 固定值 `"list"` |
| `data` | array | 模型对象列表 |
| `data[].id` | string | 模型唯一标识（用于在对话接口中作为 `model` 参数传入） |
| `data[].object` | string | 固定值 `"model"` |
| `data[].created` | integer | 创建时间戳 (秒) |
| `data[].owned_by`| string | 拥有者标识，如 `"polaris-platform"` |

### 响应示例

```json
{
  "object": "list",
  "data": [
    {
      "id": "qwen-plus",
      "object": "model",
      "created": 1724112000,
      "owned_by": "polaris-platform"
    },
    {
      "id": "deepseek-chat",
      "object": "model",
      "created": 1724112000,
      "owned_by": "polaris-platform"
    }
  ]
}
```
