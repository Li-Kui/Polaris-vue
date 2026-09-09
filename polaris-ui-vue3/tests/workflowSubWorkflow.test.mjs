import test from 'node:test'
import assert from 'node:assert/strict'
import {
    autoMatchInputs,
    compatibleType,
    initialInput,
    inputIssues,
    isInsideSubWorkflowLoop,
    subWorkflowPorts
} from '../src/components/workflow/workflowSubWorkflow.js'

test('只展开必填对象，可选对象和契约默认值保持未填写',()=>{
  const schema={type:'object',required:['customer'],properties:{
    customer:{type:'object',required:['name'],properties:{name:{type:'string'}}},
    optional:{type:'object',required:['name'],properties:{name:{type:'string'}}},
    limit:{type:'integer',default:0}
  }}
  const tree=initialInput(schema)
  assert.equal(tree.fields.customer.mode,'OBJECT')
  assert.equal(tree.fields.optional.mode,'DEFAULT')
  assert.equal(tree.fields.limit.mode,'DEFAULT')
  assert.deepEqual(inputIssues(tree,schema),['输入.customer.name：请填写或选择来源'])
})
test('未声明输入参数的子流程默认传空对象，不要求额外填写',()=>{
  assert.deepEqual(initialInput({}),{mode:'OBJECT',fields:{}})
  assert.deepEqual(inputIssues(initialInput({}),{}),[])
})

test('固定 false、0、空字符串与 null 均不同于缺失',()=>{
  for(const value of [false,0,'',null])assert.deepEqual(inputIssues({mode:'VALUE',value}),[])
  assert.equal(inputIssues({mode:'VALUE'}).length,1)
  assert.equal(inputIssues({mode:'SOURCE'}).length,1)
  assert.deepEqual(inputIssues({mode:'DEFAULT'},{default:false}),[])
})

test('嵌套数组对象中的错误显示完整位置',()=>{
  const schema={type:'array',items:{type:'array',items:{type:'object',required:['id'],properties:{id:{type:'string'}}}}}
  const tree={mode:'ARRAY',items:[{mode:'ARRAY',items:[initialInput(schema.items.items)]}]}
  assert.deepEqual(inputIssues(tree,schema),['输入[0][0].id：请填写或选择来源'])
})

test('自动匹配只选唯一同名且类型兼容的字段，不猜测对象结构',()=>{
  const schema={type:'object',properties:{name:{type:'string'},age:{type:'number'},rows:{type:'array'},ambiguous:{type:'string'}}}
  const fields=[
    {expression:'$.input.name',type:'string'}, {expression:'$.input.age',type:'integer'},
    {expression:'$.input.rows',type:'array'}, {expression:'$.input.ambiguous',type:'string'},
    {expression:'$.nodes.a.output.ambiguous',type:'string'}, {expression:'$.input.rows[].name',type:'string'}]
  const {tree,matched}=autoMatchInputs(schema,fields)
  assert.equal(matched,2)
  assert.equal(tree.fields.name.expression,'$.input.name')
  assert.equal(tree.fields.age.expression,'$.input.age')
  assert.equal(tree.fields.rows.mode,'DEFAULT')
  assert.equal(tree.fields.ambiguous.mode,'DEFAULT')
  assert.equal(compatibleType({type:'integer'},'number'),false)
})

test('结果处理模式使用稳定出口标识',()=>{
  assert.deepEqual(subWorkflowPorts().map(p=>p.port),['completed'])
  assert.deepEqual(subWorkflowPorts('BRANCH').map(p=>p.port),['completed','incomplete'])
  assert.deepEqual(subWorkflowPorts('DETAILED').map(p=>p.port),['completed','rejected','failed'])
})

test('高级 JSON 支持嵌套原始数据并阻止错误格式',()=>{
  assert.deepEqual(inputIssues({mode:'JSON',json:'[[{"flag":false,"count":0}],null]'}),[])
  assert.equal(inputIssues({mode:'JSON',json:"{'name':'单引号不合法'}"}).length,1)
  assert.equal(inputIssues({mode:'JSON',json:'{} {}'}).length,1)
})

test('只有循环体内的调用可以选择本轮数据，完成出口不能访问',()=>{
  const definition={nodes:[{id:'loop',type:'loop'}],edges:[
    {source:'loop',target:'body',sourcePort:'body'},
    {source:'loop',target:'after',sourcePort:'done'},
    {source:'body',target:'sub'},
    {source:'sub',target:'loop',targetPort:'loop-return'}]}
  assert.equal(isInsideSubWorkflowLoop(definition,'sub'),true)
  assert.equal(isInsideSubWorkflowLoop(definition,'after'),false)
  assert.equal(isInsideSubWorkflowLoop(definition,'loop'),false)
})
