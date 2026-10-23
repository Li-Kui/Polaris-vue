import assert from 'node:assert/strict'
import test from 'node:test'
import vm from 'node:vm'
import {readFileSync} from 'node:fs'
import {compileScript, compileTemplate, parse} from '@vue/compiler-sfc'
import {
    agentConfigurationState,
    agentKnowledgeSnapshot,
    agentNodeSchema,
    agentSnapshot,
    agentSnapshotMatches,
    agentToolAccessView,
    confirmAgentKnowledge,
    confirmAgentSnapshot,
    normalizeAgentConfig
} from '../src/components/workflow/workflowAgent.js'

const hash='a'.repeat(64), next='b'.repeat(64)
const agent={resourceId:'3',available:true,attributes:{snapshot:{agentId:'3',definitionHash:hash,configVersion:'2.0'},toolAccessMode:'MCP_READ_ONLY',toolBindings:[{alias:'tool_1',purpose:'公开查询'}]}}
const knowledge={resourceId:'7',available:true,attributes:{collaborationSnapshot:{knowledgeId:'7',definitionHash:hash,indexVersion:'2'}}}
const config=confirmAgentSnapshot({},agent)
test('当前 Agent 版本须明确确认，漂移不自动采用新快照',()=>{
  assert.equal(agentSnapshotMatches(config,agent),true)
  const changed={...agent,attributes:{...agent.attributes,snapshot:{...agent.attributes.snapshot,definitionHash:next}}}
  assert.equal(agentSnapshotMatches(config,changed),false)
  assert.equal(confirmAgentSnapshot(config,changed).definitionHash,next)
  assert.equal(agentSnapshot({...agent,available:false}),null)
  assert.equal(config.definitionHash,hash)
})
test('知识单独确认且可以解除，不回退到全局库',()=>{
  const bound=confirmAgentKnowledge(config,knowledge)
  assert.equal(bound.knowledgeId,'7');assert.equal(bound.knowledgeHash,hash)
  assert.equal(Object.hasOwn(confirmAgentKnowledge(bound,null),'knowledgeId'),false)
  assert.equal(agentKnowledgeSnapshot({...knowledge,available:false}),null)
  assert.equal(confirmAgentKnowledge(config,{...knowledge,available:false}),null)
})
test('问题与外发查询分别显式映射，旧任务和内部工具不能充当输入',()=>{
  assert.equal(agentConfigurationState({selectedAgent:agent,config,inputMapping:{question:{value:'private'}}}).code,'CONFIGURING')
  const state=agentConfigurationState({selectedAgent:agent,config,inputMapping:{question:{value:'private'},externalQuery:{value:'public'}}})
  assert.equal(state.code,'READY')
  assert.equal(agentConfigurationState({selectedAgent:agent,config:{task:'old'},inputMapping:{input:{value:'private'}}}).code,'CONFIGURING')
  assert.equal(agentToolAccessView({attributes:{hasTools:true,toolAccessMode:'INTERNAL_READ_ONLY'}}).hasTools,false)
  assert.equal(agentToolAccessView(agent).callLimit,2)
})
test('有知识时可不用工具，空证据配置明确阻止',()=>{
  const noTools={...agent,attributes:{...agent.attributes,toolBindings:[]}}
  const bare=confirmAgentSnapshot({},noTools)
  assert.equal(bare.maxCalls,0)
  assert.equal(agentConfigurationState({selectedAgent:noTools,config:bare,inputMapping:{question:{}}}).code,'CONFIGURING')
  const bound=confirmAgentKnowledge(bare,knowledge)
  assert.equal(agentConfigurationState({selectedAgent:noTools,selectedKnowledge:knowledge,config:bound,inputMapping:{question:{}}}).code,'READY')
})
test('动态输入 Schema 不允许任意 JSON 知识包或自动扩张字段',()=>{
  const schema=agentNodeSchema(config,agent,null,null).inputSchema
  assert.deepEqual(schema.required,['question','externalQuery'])
  assert.equal(schema.additionalProperties,false)
  assert.deepEqual(Object.keys(schema.properties),['question','externalQuery'])
})
test('读取旧节点不迁移配置，当前节点只保留 2.0 字段',()=>{
  const source=readFileSync(new URL('../src/components/workflow/WorkflowWorkbench.vue',import.meta.url),'utf8')
  const method=source.match(/    normalizeAgentNodeConfigs\(\) \{[\s\S]*?\n    \},/)[0].replace(/,$/,'')
  const object=vm.runInNewContext(`({${method}})`,{normalizeAgentConfig})
  const old={type:'agent',typeVersion:'1.0',config:{task:'keep',allowInternalReadTools:true}}
  const current={type:'agent',typeVersion:'2.0',config:{...config,task:'discard'}}
  object.normalizeAgentNodeConfigs.call({definition:{nodes:[old,current]}})
  assert.deepEqual(old.config,{task:'keep',allowInternalReadTools:true})
  assert.equal(Object.hasOwn(current.config,'task'),false)
})
test('协作编辑、运行台账、影响检查和工作台模板编译通过',()=>{
  for(const name of ['workflow/WorkflowAgentEditor.vue','workflow/WorkflowWorkbench.vue','agent/AgentRunDetails.vue','agent/AgentContractImpact.vue']) {
    const path=`../src/components/${name}`
    const {descriptor,errors}=parse(readFileSync(new URL(path,import.meta.url),'utf8'));assert.deepEqual(errors,[])
    const script=compileScript(descriptor,{id:'agent-g2'})
    assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:path,id:'agent-g2',compilerOptions:{bindingMetadata:script.bindings}}).errors,[])
  }
})
