# 知识库固定样本评测

这是仓库内的评测工具，不是新的应用、菜单或评测服务。下文 `evaluate.mjs` 只调用已有管理员检索测试接口，不生成回答；文末的 `generation.mjs` 另需显式确认回答模型调用。两者都不上传文档、不改库参数、不重建索引或授权，无 SQL。

## 各套样本不可混算

- `fixed`：十份 Markdown 合成资料，60 个问题；直接事实 20、同义表达 10、编号 10、表格/跨文档 10、无答案 10。开发集 40、保留集 20。正文不包含题库问题；标注只在脚本中，不导出到入库文件。
- `smoke`：既有 QA 文档的四题接通验证，三题有证据、一题无答案，只有开发集。它不是 60 条基线；旧文档包含原测试问题，容易命中，不能报告成业务泛化准确率。

保留集是**问题级**划分，不是文档级隔离：有些事实/来源与开发集重叠。题目和标注在仓库中可见，不是盲测；约束是调参只用 dev，阶段验收才运行 holdout。真实业务评估还要补独立资料及人工复核。

另有独立的 `natural-long` 开发挑战集，见文末。它不是 fixed 的扩充版，也不代替原 20 条保留集；各套结果不能混算。

## 本地回归与导出

要求 Node.js 20+，无需新增依赖。从仓库根目录运行：

```sh
node --test scripts/knowledge-quality/evaluate.test.mjs
node scripts/knowledge-quality/evaluate.mjs export-corpus --out /private/tmp/polaris-quality-corpus-v1
```

导出目录必须不存在；文件使用私有权限且不覆盖旧文件。只生成十份资料和清单模板，不执行任何网络请求。题库、参考答案、清单模板**不得一起上传知识库**。

完整 60 条真实基线需要单独 QA 知识库，人工上传这十份资料、正常完成索引后，核实该库没有混入其他资料，再填写 `manifest-template.json`：

```json
{
  "knowledgeId": "填写实际库ID",
  "indexVersion": "填写实际索引版本",
  "embeddingModelId": "填写实际向量模型ID",
  "embeddingModelRevision": "填写实际模型修订",
  "embeddingSchemaHash": "填写实际Schema摘要",
  "embeddingDimension": 1024,
  "indexSignature": "填写实际索引签名",
  "chunkSize": 300,
  "chunkOverlap": 30,
  "splitterType": "RECURSIVE",
  "corpusHash": "保留导出模板中的语料摘要",
  "corpusVerified": true,
  "documents": {
    "archive": "实际文档ID",
    "visit": "实际文档ID",
    "device": "实际文档ID",
    "training": "实际文档ID",
    "backup": "实际文档ID",
    "sample": "实际文档ID",
    "meeting": "实际文档ID",
    "repair": "实际文档ID",
    "book": "实际文档ID",
    "delivery": "实际文档ID"
  }
}
```

以上维度、分块参数只是格式示例，不能直接冒充真实配置。所有 ID/修订用十进制字符串，避免 JavaScript 大整数精度丢失；文档映射必须唯一，不能按同名文件猜 ID。`corpusVerified` 是操作人的确认，不是工具已核实物理文件摘要；目前后端没有文档正文修订摘要接口，必须保留这个限制。

## 真实采集：显式预算、只访问本机

当前版本只接受本机 origin（localhost/127.0.0.1/IPv6 回环），不能给任意远端地址发送令牌。令牌通过当前终端进程的 `POLARIS_QA_TOKEN` 传入，不放命令行参数、仓库文件或报告；使用现有管理员登录机制获取，不在脚本中保存账号密码。

```sh
node scripts/knowledge-quality/evaluate.mjs collect \
  --manifest /private/tmp/qa-quality-manifest.json \
  --suite fixed --split dev --min-score 0.5 \
  --max-calls 40 --confirm-embedding yes \
  --out /private/tmp/qa-quality-dev-run1.json
```

每轮固定 TopK=5，阈值仅作用于测试。`--max-calls` 是**诊断请求上限**，不是厂商 HTTP 次数或费用上限；模型准备可能另有探测。串行请求之间等待 5.2 秒，沿用后端冷却；不给 429/502 自动重试。每题诊断前后检查 READY 状态及索引/模型/分块快照，漂移或首个请求错误立即停止后续诊断。超时只阻止继续调用，不能承诺取消服务端已经发出的请求。

可用更小预算进行接通检查，但报告会把其余样本标为 NOT_RUN，不是完整基线。保留集需显式 `--split holdout --max-calls 20 --confirm-holdout yes`；不要用保留集反复调参。

采集前独占预留输出文件，文件已存在时不派发诊断；开始后异常可能留下空文件，不得当成完成报告。采集不保存原始结果载荷，报告只含样本 ID、状态、指标、配置快照和摘要；不含问题、支持片段、来源名称、令牌。采集模式未输出中间正文到控制台。接口或模型自身既有日志/留存不由此工具消除。

## 离线复算与对照

若已有受控观察文件，可离线计分，不触发模型：

```sh
node scripts/knowledge-quality/evaluate.mjs evaluate \
  --manifest /private/tmp/qa-quality-manifest.json \
  --observations /private/tmp/private-observations.json --split dev \
  --out /private/tmp/qa-quality-recorded.json
```

观察文件格式为 `[{"caseId":"direct-01","response":{...原诊断接口data...}}]`，失败为 `{"caseId":"direct-02","error":true}`。文件可能包含原始正文，必须由调用者安全管理；工具不会自动生成这种载荷文件。缺题、重复题、混入另一集合、参数/索引不符分别处理，不把错误记成空命中。

```sh
node scripts/knowledge-quality/evaluate.mjs compare \
  --before /private/tmp/qa-quality-dev-run1.json \
  --after /private/tmp/qa-quality-dev-run2.json \
  --out /private/tmp/qa-quality-comparison.json
```

只比较完整、相同题库/语料/文档映射/索引模型快照、同集合、同 TopK、同采集模式报告；允许改变本次阈值。新索引或新模型不是同一对照，须另做基线、记录变更原因并人工检查。没有自动放行质量或修改策略的逻辑。

## 指标含义

- 证据命中@5：有答案题的前五条至少包含一个支持标注。必须同时匹配实际文档 ID 和归一化后的完整支持片段，只命中文件不算。
- 完整证据@5/平均覆盖率：多片段或跨文档题需要全部支持标注，部分命中单独统计。
- MRR@5：首条支持证据排名的倒数，未命中为 0；不是 nDCG，也不是答案正确率。
- 有答案空召回率、无答案空召回率：只是检索结果状态。无答案问题可能召回相关但不足以回答的资料，非空不等于生成了错误回答；**不称作拒答准确率**。
- P50/P95：服务端诊断总耗时的最近秩统计，不含串行冷却或客户端网络耗时；小样本 P95 只能作为粗略记录。
- ERROR/NOT_RUN/完成率：分母明确；错误不混入成功请求的质量指标。不完整报告不能拿均值当验收结论，CLI 返回码 2；参数/文件错误返回码 1，完整运行返回码 0，但 0 不表示质量放行。

字符串匹配可能受等义改写、解析空格/标点、表格表示变化影响；失败要人工归因，不能自动增加关键词或放宽阈值刷分。安全、跨租户、撤权和故障回归另行验收，不与质量平均分抵消。上述检索工具不使用模型自评，不统计生成引用支持率、拒答质量、Token 或真实费用。

## 生成小样本：非空证据不足与实际回答

`generation.mjs` 复用 fixed 的原题和语料，固定选择 12 条 dev：`direct-01`、`direct-03`、`table_cross-01`、`table_cross-06`、`table_cross-07`、`no_answer-01`～`no_answer-07`。不改题、不访问 holdout；其余 28 条 dev 未执行。这是有目的的小样本，不是随机样本或整体业务准确率。

先另行准备只含固定十份合成资料的独立 QA 库，核实来源并填写前述 manifest，再增加：

```json
{
  "answerModelConfigId": "实际回答模型ID",
  "answerModelRevision": "实际模型中心修订",
  "answerModelAggregateHash": "公开模型聚合视图的SHA256摘要",
  "retrievalTopK": 5
}
```

摘要使用导出的 `generationHash(publicModelView)`，不读取或保存供应商密钥；这是公开视图的 `JSON.stringify` 摘要，属性顺序改变也可能导致停止，不是供应商权重指纹。`retrievalTopK` 可为 5 或 10，省略兼容为 5；采集只核验 QA 库已保存的配置，**不会代为修改 TopK**。固定 minScore=0.5，索引、向量模型、分块参数和回答模型视图均在每题前后核验。公开修订/摘要不能冻结供应商模型权重、线路和全部聊天策略。

```sh
node --test scripts/knowledge-quality/evaluate.test.mjs scripts/knowledge-quality/generation.test.mjs scripts/knowledge-quality/compare-generation.test.mjs
node scripts/knowledge-quality/generation.mjs collect \
  --manifest /private/tmp/qa-generation-manifest.json \
  --max-calls 12 --confirm-model-calls yes --label BASELINE_B3_V1 \
  --out /private/tmp/qa-generation-report.json \
  --review-packet /private/tmp/qa-generation-private.json
```

令牌仍仅从 `POLARIS_QA_TOKEN` 读取，只允许本机 origin。每题创建新的、初始历史为空的 QA 会话，绑定指定知识库和回答模型；不使用前题答案、联网搜索、附件或指定智能体。派发前写入检查点，一题最多一次聊天请求，间隔 5.2 秒，首错停止、不自动重试。`--max-calls` 为 1～12 的聊天端点请求预算，不等于回答/Embedding 供应商 HTTP 次数、Token 或费用上限。超时不保证服务端远端请求取消。

仅通过已保存消息的引用接口读取**该次实际入选片段**，不重新检索来替换生成依据。空召回停止单列 STOPPED_EMPTY，不冒充生成了拒答；错误、未执行也不计作正确拒答。文档 ID 与完整标注字符串的覆盖率仍不是语义正确率；IDS_VALID 也不是引用支持度或证据充分性。

公共报告不含问答正文、片段、来源名称或凭证；私有 `review-packet` 包含合成回答及实际片段，权限 0600，不能上传知识库或提交仓库。两个输出均在网络请求前独占创建，存在则拒绝；中断后可能留下检查点/未完成文件，不自动续跑，不能当作完成结果。现有接口/供应商日志留存不由工具消除。`corpusVerified` 和包中的 `syntheticOnly` 依赖操作者已核实语料；工具会检查十份文档的 ID、名称、READY/SAFE 和配置，但不自动核验全部物理原文摘要。

审阅者逐题比较实际回答、实际片段与标注，使用 SUPPORTED_ANSWER、PARTIAL_ANSWER、CORRECT_REFUSAL、WRONG_REFUSAL、UNSUPPORTED_ANSWER、UNCLEAR。决策文件格式：

```json
{
  "runId": "报告中的实际runId",
  "reviewerType": "AGENT",
  "cases": [{
    "caseId": "direct-01",
    "answerHash": "报告中的实际answerHash",
    "previewHash": "报告中的实际previewHash",
    "label": "SUPPORTED_ANSWER",
    "rationale": "结合本次实际片段逐项核对的理由"
  }]
}
```

```sh
node scripts/knowledge-quality/generation.mjs review \
  --manifest /private/tmp/qa-generation-manifest.json \
  --report /private/tmp/qa-generation-report.json \
  --review-packet /private/tmp/qa-generation-private.json \
  --decisions /private/tmp/qa-generation-decisions.json \
  --out /private/tmp/qa-generation-review.json
```

`reviewerType` 必须如实区分 HUMAN/AGENT；AGENT 审阅不是人工签字。不按“无法”等关键词、相似度或编号通过自动判正确，不另调用评分模型。离线审阅校验报告/原包/回答/片段摘要绑定和部分标签与证据覆盖的矛盾，但不代替语义判断；理由只以摘要进入公共审阅报告。所有报告始终 `qualityReleaseApproved:false`；换模型/新索引须建立新基线，不能直接当成旧基线收益。

## 离线生成对照：Top5 / Top10

`compare-generation.mjs` 只读两轮本地文件，不读取令牌或访问网络。要求两轮固定 12 题均完成回答、完整审阅，原始包和报告/决策绑定一致；同语料、文档映射、库、索引、向量/回答模型快照及采集模式，仅允许 Top5 → Top10。未填旧 manifest 的 TopK 兼容为 5。空召回停止、错误、缺题、漏审或重用会话不作为完整生成对照；HUMAN/AGENT 也不能混作同种审阅。

```sh
node scripts/knowledge-quality/compare-generation.mjs \
  --before-manifest /private/tmp/top5/manifest.json \
  --before-report /private/tmp/top5/report.json \
  --before-review-packet /private/tmp/top5/review-packet.json \
  --before-decisions /private/tmp/top5/decisions.json \
  --after-manifest /private/tmp/top10/manifest.json \
  --after-report /private/tmp/top10/report.json \
  --after-review-packet /private/tmp/top10/review-packet.json \
  --after-decisions /private/tmp/top10/decisions.json \
  --out /private/tmp/qa-topk-comparison.json
```

输出只含题号、状态、摘要、证据覆盖及已有审阅标签的配对变化，不复制问答/片段/理由正文，独占创建 0600 文件，不覆盖旧结果。它不是新的语义评分器，也不自动调参、恢复设置或放行质量。每个参数只运行一次、未随机交错，供应商和完整运行策略也未冻结，所以始终 `causalImprovementProven:false`，不能把观察收益当作普遍因果结论。

实际对照需另行明确范围、预算和配置恢复，保留物理语料摘要核验及旧库基线。当前正常编辑接口会更新审计字段并递增内部构建代次；`indexGeneration` 不在公共响应中，不能将缺失值转换为 NaN 作代次核验。恢复 Top5 不是回滚这些正常审计痕迹，且工具无法据文件证明没有并发修改；需要受控 QA 运行和必要的数据库只读对账。不要在共享业务库上用本脚本循环调参。

## 完整开发集：双请求检索对照

`retrieval-control.mjs` 专门采集 fixed 的 40 条 dev，Top5／Top10 每题各一次，最多 80 次诊断。奇偶题交替先后顺序，各 20 题先 Top5／Top10；这是确定性平衡，不是随机或重复实验。两边都是实际检索请求，**不把 Top10 前五条当作已请求过 Top5**。本工具不接受 holdout 或其他题库参数。

```sh
node --test scripts/knowledge-quality/evaluate.test.mjs scripts/knowledge-quality/generation.test.mjs scripts/knowledge-quality/compare-generation.test.mjs scripts/knowledge-quality/retrieval-control.test.mjs
node scripts/knowledge-quality/retrieval-control.mjs \
  --manifest /private/tmp/qa-fixed-retrieval-manifest.json \
  --max-calls 80 --confirm-embedding yes \
  --out /private/tmp/qa-fixed-retrieval-control.json
```

使用前述检索 manifest，不需回答模型字段。令牌仍只从 `POLARIS_QA_TOKEN` 传入，限定本机 origin；要求独立 QA 库的生效 TopK=5、minScore=0.5，诊断请求显式带 5 或 10，**不保存库参数**。每次前后核验 READY/索引与向量模型快照、库公开视图、向量模型公开视图摘要、十份文档 ID/名称/SAFE/就绪状态；未知资料或超长片段拒绝计分。公开视图摘要受 JSON 属性顺序影响，不能当作供应商权重/线路指纹。原文文件摘要仍需另行核验，`corpusVerified` 不是工具已完成物理对账。

采集只会 GET 元数据及 POST 管理员 retrieval-test；不新建会话、不生成回答、不上传、不改授权、不重建或清理。诊断之间冷却 5.2 秒，预算为 1～80 的诊断端点请求数，不是厂商 HTTP 次数、Token 或费用上限。首错停止，不自动重试；超时不保证远端已发请求取消。预算不足、错误、EMPTY 和 NOT_RUN 分开，只有两边 40 题均完成才输出全量 delta。

报告分别列出两边证据命中、完整证据、平均标注覆盖、首条证据 MRR、空召回、候选数/字符数以及诊断 P50/P95，按五类题单列分母；另记录每题 Top5 与 Top10 前五条的资料/文本指纹是否相同（忽略匹配分），不预设近似检索前缀稳定。

“未命中标注的候选”只对有答案题计数，指没有完整包含任一预设支持标注的候选；**不等于无关噪声**，可能包含未标注或部分有用信息。无答案题此字段为 null，非空仍不能推断应回答或拒答正确率。字符数是返回候选文本的 UTF-16 长度，不是 Token 或最终回答上下文预算；服务端诊断耗时也不是回答耗时。

公共报告只保存题号、指标/排名、元数据摘要及结果指纹，不保存原始问题、片段、来源名称、令牌或异常。输出文件网络请求前独占预留 0600；派发前/完成后写有界进度检查点，中断可能只留下 `inProgress` 文件，没有完整指标，不自动续跑，不覆写或自动补调用。结果始终 `qualityReleaseApproved:false`、`causalImprovementProven:false`；全量 dev 检索通过仍不代表全量生成、长文规模、保留集或业务验收完成。

## 长文上下文预算：本地真实挂载、模拟候选

`KnowledgeContextBudgetTest` 定义独立的 `CONTEXT_BUDGET_CHALLENGE_V1`：10 个合成场景，不修改 fixed 的原 60 题。将固定顺序的模拟召回结果同时送入冻结 B3.7 旧分配基线与真实 `AiDocumentRecognitionServiceImpl`，检查 XML 转义后的 12000 字符正文预算、每片段 4000 原始 UTF-16 字符上限、去重、实际入选引用及其精确原文前缀。它不是 Qdrant/Embedding 测试，不调用回答模型，也不自动判答案真假。

可显式导出无正文的诊断报告和后续验收原文包，要求 Maven/依赖已就绪：

```sh
qa_budget_dir=$(mktemp -d /private/tmp/polaris-context-budget-XXXXXX)
mvn -o -pl polaris-ai -am \
  -Dtest=KnowledgeContextBudgetTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpolaris.knowledge.context-budget-report="$qa_budget_dir/challenge-report.json" \
  -Dpolaris.knowledge.context-budget-corpus="$qa_budget_dir/corpus-pack" test
```

输出目录/文件不能已存在，目录 0700、文件 0600，不覆盖旧结果，不自动上传或清理。`corpus-pack` 内每个场景各有 `corpus/`（30 份原文总计）及位于其外的 `case-manifest.json`；**每个场景独立使用，不能把全部场景混入同一知识库，问题/标注/模拟候选顺序清单绝不能上传**。重复候选只导出一份同内容原文。测试还核对导出原文摘要/读取内容与场景一致，原文不包含原问题全文。

报告区分“模拟候选含支持标注”、冻结旧分配覆盖和“当前真正挂载的原文前缀含支持标注”，无答案场景为 null，不冒充拒答。旧基线的 5 个正例保留、4 个截断后丢失断言继续冻结；B3.9 单调扩展后独立检查为 6 个保留、3 个丢失，不改金标或原文刷分。原文包只能准备真实 QA 数据，不能保证真实解析、分块和向量排序会复现模拟顺序；实际建库需重新核验参数、文档映射、模型能力和单独调用预算。

另有 6 项字符/指针回归：正文 4000 边界、XML 预算回退边界、文件名 200 边界不切开完整 UTF-16 代理对；恰好完整位于边界的补充字符不多裁；原文末尾自带截断提示时仍准确记录截断长度，短原文自带同标记不误报。它不保证所有 Unicode 字素簇完整，也不修复已保存的旧历史。生产先保留旧分配的来源与前缀，再利用剩余预算扩展；不全局调 TopK、加预算或添加评分模型。

## 预算分配对照：冻结旧基线、离线原型与生产扩展

`KnowledgeContextBudgetComparisonTest` 复用上述 10 场景，另外加入首/次/前三来源尾部、两来源联合证据及 50 候选的共 5 个反例。总计 15 场景，14 个有证据、1 个无答案；其中一场景需同时保留两个不同文档的事实，按文档和全文摘要区分，不靠全局出现同一句话计覆盖。

```sh
qa_budget_compare_dir=$(mktemp -d /private/tmp/polaris-budget-compare-XXXXXX)
mvn -o -pl polaris-ai -am \
  -Dtest=KnowledgeContextBudgetTest,KnowledgeContextBudgetComparisonTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpolaris.knowledge.context-budget-comparison-report="$qa_budget_compare_dir/comparison.json" test
```

对照包括冻结 B3.7 旧分配、真实当前挂载器，以及 `src/test` 中的 5 个独立原型：按真实转义成本贪心填满、平均分配、每候选预留 256/640 后按序扩展、保留旧实际片段后只扩展其原文前缀。只有最后一种已在 B3.9 接入生产，其余四种不接生产。预留额度按**包含截断标记的 XML 转义字符**计，不是 Token/原始字数，也不是按不同文档预留；多片段同文档仍分别计候选。

各分配函数都不接收问题/金标作为分配输入，不调用向量或回答模型，保持 4000 原文前缀上限和 12000 正文预算。报告区分各场景的完整标注覆盖及相对冻结旧基线的回退，不以总均值掩盖来源丢失，不复制正文/问题，独占创建 0600 文件。

“单调扩展”保持旧实际引用数量、顺序和来源，只利用未使用预算按原顺序扩展已有前缀，不添加或替换来源，不处理任意中段窗口。真实生产结果逐项与独立原型对比；另用 60 组确定性混合字符/候选数量检查前缀长度不减少、编号/文档/版本/分块/哈希不变、XML预算和 Unicode 边界，以及同文档多片段分组、原算法未选来源不补入、仅余一个字符时不切开代理对。这是结构保留检查，不保证新增原文不会干扰回答模型，不能把它叫作回答无回退证明。

B3.8 的旧报告仍为 `OFFLINE_TEST_ONLY_PROTOTYPES` / `productionPolicyChanged:false`，不覆盖或重解释；B3.9 新报告为 `LOCAL_PRODUCTION_MOUNT_WITH_FROZEN_LEGACY_AND_PROTOTYPES` / `productionPolicyChanged:true` / `productionPolicy:LEGACY_THEN_MONOTONE_PREFIX_V1`。原十场景报告模式为 `LOCAL_MOCK_RETRIEVAL_REAL_MOUNT_WITH_FROZEN_LEGACY`。这些是当前代码的本地执行，不表示运行中的后端已重启。候选排序均为 Mock，保留集未运行；原先的真实诊断及真实生成结果不能与此模式混算。已有 10 场景和金标不改，新反例另标记，不把原本失败事实移到容易命中的位置刷分。实际归档仍使用版本 1，仅保存最终前缀长度；无新增 SQL。

## 自然行文与有限干扰：独立开发挑战集

`natural-long-suite.mjs` 固定五份虚构中文资料：2026 现行手册、2024 失效纪要、关联伙伴日历、近名展览馆、日常观察记录。正文采用连贯段落而非重复字符填充；现行手册 3325 个 UTF-16 字符，全部五份合计 6840 字符。它是人工编写的合成样本，不是用户真实业务资料，也不是大规模干扰库。

固定 8 条 dev（6 条有答案、2 条无答案），覆盖直接事实、版本范围、条件边界、编号、跨文档和近名机构。没有新 holdout，不改原 fixed 的 60 题或其 20 条保留集。4 个生成题 ID 在检索前固定为 `scope-current`、`condition-not-storage`、`linked-calendar`、`unknown-contact`，不能根据结果挑容易题或补问刷分。

```sh
node --test scripts/knowledge-quality/*.test.mjs
node scripts/knowledge-quality/evaluate.mjs export-corpus \
  --suite natural-long --out /private/tmp/polaris-natural-corpus-v1
node scripts/knowledge-quality/evaluate.mjs collect \
  --suite natural-long --split dev --manifest /private/tmp/qa-natural-manifest.json \
  --min-score 0.5 --max-calls 8 --confirm-embedding yes \
  --out /private/tmp/qa-natural-retrieval.json
```

导出目录中仅五份 `.md` 是待入库原文；`manifest-template.json` 仍仅作操作者填表模板，**绝不能上传**。没有题目、标注或参考答案文件。默认不填 `--suite` 仍导出 fixed 的十份资料；显式 `natural-long` 才切换，文件和目录仍为 0600／0700、不覆盖旧结果。

先在独立 QA 导入五份原文并核实其实际 ID、解析／分块及模型／索引，再填写原有 manifest 格式。采集仍仅访问本机，Top5、显式预算、5.2 秒冷却、首错停止、不重试；不改库参数、不生成回答。报告的 suiteVersion／suiteHash／corpusHash 与 fixed 不同，既有对照校验不会允许混算。

相同表述出现在错误文档不计为指定来源标注命中，跨文档题只召回一边记部分覆盖；无答案非空不等于错答。字符串标注覆盖仍可能漏掉同义表达或其他有用的未标注片段，不能把未命中标注的来源都叫作语义噪声。失效／近名文本可能相似度更高，不能把最高匹配分当作现行事实或答案正确率。

现有 `generation.mjs` 的自动采集仍专用于 fixed 的十份文档／12 题，不接受这份五文档清单。本批四题真实生成按独立受控 QA 脚本执行，使用该次保存引用逐项审阅，不为适配新集放宽原工具的来源核验。真实过程与调用预算记录在 B3.11 验收文档；不自动引入评分模型或放行质量。

B3.12 增加代码内固定的 `知识库引用范围约束 V1` 系统策略，只有本轮成功挂载知识库时接入；它约束主体／时期／条件及缺依据表述，不将文档规则提升为指令，也不自动检测语义错配。既有报告的编号 IDS_VALID 含义不变。该策略改变了生成请求的可信提示，即使模型和索引相同，也不能把变更前后当成仅 TopK 差异的受控实验；应在新运行记录中明确应用策略版本、重启与环境边界，原报告和警告不覆盖。本地假模型回归不等于重启后的真实生成效果，须用冻结四题的新实际引用另行复验。

## 事实主张—证据审阅（B3.14）

`evidence-review.mjs` 为独立离线工具，不改变正常聊天、原引用编号检查或旧报告。`evidence-review-fixtures.mjs` 固定 24 条合成标注夹具（12 控制／12 疑点），仅验证审阅记录契约，不是语义算法的真实准确率。

输入输出必须放在已存在、权限 0700 的私有目录中；输出 0600、独占创建，遇到同名文件停止，不覆盖旧证据。适配器只接受显式 `fixed-v1` 或 `natural-long-v1`，不从未知字段猜格式。fixed 输入须同时提供原 manifest；natural-long 输入须绑定原冻结四题及实际答案／预览摘要。旧预览没有索引或分块字段时保留 null，不凭最新知识库或标题补造历史身份。

```sh
node scripts/knowledge-quality/evidence-review.mjs adapt \
  --format natural-long-v1 \
  --packet /private/tmp/private-review/private-generation.json \
  --report /private/tmp/private-review/generation-report.json \
  --out /private/tmp/private-review/evidence-packet.json

node scripts/knowledge-quality/evidence-review.mjs review \
  --packet /private/tmp/private-review/evidence-packet.json \
  --decisions /private/tmp/private-review/decisions.json \
  --out /private/tmp/private-review/evidence-report.json
```

审阅记录为 `reviewVersion: 1`，HUMAN／AGENT／MODEL_SUGGESTION 分开；每条主张包含精确 `answerSpan`（UTF-16 半开区间）、实际引用对应位置 `citationSpans`、原文 `evidenceQuotes` 及审阅者的支持标签／范围疑点／理由。数字或原文精确出现、引用编号存在，均不证明事实受支持；多个引文也不自动证明联合推断成立。

正文缺少引用或无法可靠定位对应关系时显式记录，不从别的句子借用编号。`OTHER_CONTEXT` 只表示其他参考资料，不允许替换这句话的实际引用支持。精确范围／引文／摘要检查不做模糊匹配，不越过原截断前缀、不读取新版替代。无法取得原材料记录未审，不算正确拒答或支持。

首版 `citationSpans` 使用单独的 `[S1]` 精确标记，可相邻列出多个；组合标记等其他格式无法定位时记录 UNBOUND／未审，不将其当作原编号无效。原 `citationState` 含义不变。模型审阅使用普通文本 JSON 契约，不假设所选型号已支持原生 JSON Schema。

输出只有摘要、受控枚举、计数和未审文本区间，无问题、正文、答案、引文或自由文本理由；报告的 `recordsValid` 是记录一致性，不是语义通过。`claimExtractionCompleteness: NOT_VERIFIED` 明确不保证事实找全，`qualityReleaseApproved` 始终 false。离线包不代表当前授权，也不能随线上撤权自动撤回，操作者需管理导出文件的访问与保留期。

### 可选的显式 QA 模型审阅

后端 `POST /ai/chat/conversations/{conversationId}/messages/{messageId}/evidence-review` 仅供管理员显式诊断本人可访问的 QA 知识库会话，不在普通聊天、刷新历史或引用预览时自动触发。请求只传模型选择、原答案／问题摘要和逐来源摘要；正文由服务端从原消息及重新鉴权的引用入口加载，禁止客户端提交任意正文、collection 或私有路径。

必须 `modelConfirmed: true`，模型公开视图摘要一致，且模型显式启用 STREAMING；不回退到不可取消的同步调用。策略只从固定资源 `polaris-ai/src/main/resources/knowledge/evidence-review-v1.txt` 加载，工具和后端共用，参考数据只进入单独 UserMessage，无工具、搜索、附件或历史。先检查原依据／模型，派发前再查，返回建议前再次核对；发生撤权或漂移丢弃结果，不能撤回已经外发的数据。

响应等待 30 秒、输入含策略最多 30000 UTF-16 字符、文本输出最多 32 KiB、推理增量最多 64 KiB（不存正文），超限取消本地订阅而不截断成成功；取消不保证远端停止或不计费。存在当前用户互斥、进程内最多四个活动诊断、五秒冷却及现有模型运行时许可，不宣称多实例／硬实时保证。

B3.15 增加**单请求**可选的 `waitTimeoutMs: 60000`，默认／旧请求仍为 30000；只接受 30000 或 60000，其他值在读取材料或调用模型前拒绝。不改普通聊天、模型中心配置及底层读取超时，也不自动延长或重试。CLI 对应 `--wait-ms 60000`，客户端等待预算加 5 秒传输余量；这不是准备／鉴权／网络全链路的硬实时上限。

成功和派发后的受控失败可附 `diagnostics` V1：应用 traceId、选定预算、耗时、首个解析事件／文本／推理事件时间、字符／事件计数、是否收到完成事件及是否由本地等待预算到期终止。只统计应用解析到的事件，不记录部分正文、推理正文、凭证或原始错误；零推理事件不证明供应商没有内部思考。派发前鉴权／摘要失败不返回模型诊断。客户端严格白名单保留元数据，60 秒成功必须有匹配的服务端预算诊断，不能把旧入口忽略参数当作60秒验收。

B3.19 增加可选 `diagnostics.upstream` V1：仅显式 QA 审阅统计 JDK 请求入口、首响应头状态／时间、正文缓冲字节／批次及 SDK 解析后 SSE 帧计数。没有头部内容、URL、正文、推理或提供器错误字段；时间不是网络首字节，字节不是 Token，帧不是应用文本事件，计数不证明远端状态。终态冻结，超过计数上限显式标为 saturated；无 instrumentation 不冒充已测上游。工具按嵌套白名单保留，兼容旧诊断缺少该字段；未绑定的普通调用不自动启用。真实使用仍须明确模型调用确认与预算，不依据本地反例倒推旧超时。

B3.20 再增可选 `upstream.frameClasses` V1，瞬时解析已收到的 SSE data，仅记录文本／推理／角色／工具字段、完成标志、Usage／DONE、其他／无法分类及预算跳过等固定帧计数，不保存字段值或未知名称。类别可重叠，不是 Token 或语义审阅结论；完成标志不保证最终正文可用。单帧 32768 UTF-16 字符、累计 4194304 字符、最多 20000 帧，限制只跳过诊断，不丢弃 SDK 原事件；结束冻结、旧记录缺少字段兼容，存在但版本／计数非法则拒绝该诊断。该分类没有启用普通聊天／推理展示，也不关闭供应商内部推理，不以本地通过代替真实帧验收。

B3.21 增加可选 `--review-mode DASHSCOPE_NON_THINKING_V1`，对应请求 reviewMode，仅管理员显式 QA 审阅可选；缺省／MODEL_DEFAULT 保持原调用。专用路径核对实际 Runtime 摘要、当前已核验 DASHSCOPE／OpenAI-compatible／qwen3.8-27b／官方 HTTPS 端点／仅 STREAMING 等边界，再仅本次追加固定 enable_thinking=false，不写模型中心或接受任意供应商参数。新模式必须返回同名诊断，否则不能计为新模式成功；旧诊断仅默认模式兼容，不重试。报告记录 reviewMode，它是额外调用策略，不等于供应商已执行或语义通过，也不能仅凭相同基础模型摘要把新旧模式当完全相同调用。真实使用需单独确认该模式的调用预算，不能复用此前已消耗的单次授权。

B3.22 增加独立的引用锚点位置观察，不改变原 `reviewEvidence` V1 报告或支持标签：

```sh
node scripts/knowledge-quality/evidence-review.mjs anchors \
  --packet /private/tmp/private-review/evidence-packet.json \
  --decisions /private/tmp/private-review/review-decisions.json \
  --out /private/tmp/private-review/anchor-observations.json
```

该命令零网络、私有 0600 输出、已有文件不覆盖；只输出位置、编号、计数和摘要，不含问答／引文／理由。`REPEATED_ID_BEFORE_CLAIM` 记录“选中主张之前的标记，后面还存在同 ID 的其他标记”这一位置现象，不自动认定错误；`SHARED_MARKER` 记录多个主张选择同一位置，合法段落共享／前置引用不强制拒绝。未分配标记不等于漏审事实，更不按最近距离自动重绑。只识别单独 `[S1]`～`[S50]`；最多保留 2000 个清点项、20 个单主张替代位置及 100 个未分配位置，截断显式标记，完整性未知不宣称“零疑点”。实时采集成功候选附同类独立观察，原候选和历史报告不改写。无法通过精确契约的模型输出仍拒绝，不能靠位置观察把 UNBOUND 却标 CITED_SUPPORT 的矛盾结果修补成成功。

平台令牌仅通过 `POLARIS_QA_TOKEN` 进程环境提供，不写入命令行、文件或聊天。先只读核实当前模型视图，再明确填写摘要与预算：

```sh
node scripts/knowledge-quality/evidence-review.mjs model \
  --packet /private/tmp/private-review/evidence-packet.json \
  --model-id 5 --model-hash CURRENT_PUBLIC_VIEW_SHA256 \
  --max-calls 4 --confirm-model-calls yes \
  --out /private/tmp/private-review/model-report.json \
  --private-out /private/tmp/private-review/model-suggestions.json
```

仅允许本机地址、最多八次应用请求；先保留两个输出文件，再逐题派发，5.2 秒冷却，首错停止，不重试、修复 JSON 或重新生成原答案。有限的模型候选仅留在私有输出，Schema／精确文本校验失败也保留待核查候选，不选好结果替换。HTTP／业务状态码脱敏保留，原始错误正文不复制。应用请求数不等于供应商 HTTP 次数或费用，未知值为 null。

服务响应的 COMPLETED 仅说明候选 JSON 返回，仍需工具检查精确引文和范围；模型支持标签始终是待复核建议。首轮真实调用超时后的记录及其余未执行状态见 B3.14 验收文档，不把端点存在或格式检查通过当作语义修复，也不默认上线自动双模型。

## 本机 Qdrant 关联补查组合验收

`KnowledgeRelatedQdrantAcceptanceTest` 使用固定合成向量，组合真实 Qdrant、实际检索内核、工作流选择和确定性下游；不调用外部 Embedding／回答模型或业务数据库，不运行旧 QA 流程。默认离线回归跳过此类，不能把跳过算作通过。

仅在本机受控测试环境显式运行：

```sh
# 如需认证，先通过安全方式将本机测试凭证提供给 POLARIS_QA_QDRANT_API_KEY，勿写入源码或报告。
POLARIS_QA_QDRANT=loopback mvn -o -pl polaris-ai -am \
  -Dtest=KnowledgeRelatedQdrantAcceptanceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

端点固定 `127.0.0.1:6334`，无公网／自选集合入口。每批仅创建一个随机 `polaris_qa_related_*` 集合，写入 21 个合成点；创建前不存在、创建成功及随机 metadata 归属回读均确认后，结束时重新核验归属、仅删除本批集合并确认不存在。删除不可恢复，但目标全为该次一次性测试数据；已有集合不在范围。创建结果不确定或归属变化时禁止清理，日志中的实际集合名交人工核查，不批量扫描或接管。

测试证明低排定向补查、阈值／索引过滤、有界查询、工作流引用一致性和索引变化拒绝；固定向量及数据库替身不代表真实 Embedding 质量、业务准确率或完整线上端到端验收。结果与剩余部署／人工门槛见 `polaris-docs/docs/design/knowledge-quality-local-closeout.md`。
