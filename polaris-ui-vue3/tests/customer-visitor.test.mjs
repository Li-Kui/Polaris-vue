import test from 'node:test'
import assert from 'node:assert/strict'
import {createCustomerVisitorClient} from '../src/utils/customerVisitorClient.js'
import {customerChannelLinks, customerChannelSettings} from '../src/utils/customerChannelState.js'

const code = 'a'.repeat(32), id = '11111111-1111-1111-1111-111111111111', token = 'b'.repeat(43)
function fixture() {
  const calls = []
  const client = createCustomerVisitorClient(code, { apiBase: '/dev-api', fetcher: async (url, init) => {
    calls.push({ url, init })
    if (url.endsWith('/messages/stream')) {
      const requestId = JSON.parse(init.body).requestId, turnId = '22222222-2222-2222-2222-222222222222'
      const turn = { id: turnId, requestId, status: 'COMPLETED', answer: '测试回答' }
      return new Response(JSON.stringify({type:'accepted',turnId,requestId})+'\n'+JSON.stringify({type:'completed',turnId,requestId,turn})+'\n', {headers:{'Content-Type':'application/x-ndjson'}})
    }
    return { ok: true, json: async () => ({ code: 200, data: url.endsWith('/sessions') ? { token, session: { id, turns: [] } } : { id } }) }
  } })
  return { client, calls }
}
test('访客创建并发去重，所有请求无平台凭证、Cookie或查询串凭证', async () => {
  const { client, calls } = fixture(); await client.entry()
  const [a,b] = await Promise.all([client.create(),client.create()]); assert.equal(a.data.id,b.data.id)
  assert.equal(calls.filter(item=>item.url.endsWith('/sessions')).length,1)
  await client.get(id); await client.source(id,'q','S1')
  for (const {url,init} of calls) {
    assert.equal(init.credentials,'omit'); assert.equal(init.redirect,'error'); assert.equal(init.cache,'no-store')
    assert.doesNotMatch(url,/Platform-Token|token|tenantId|userId|\?/i); assert.equal(init.headers['Platform-Token'],undefined)
    assert.ok(!url.includes('/console/'))
  }
  assert.equal(calls[0].init.headers['Customer-Session-Token'],undefined)
  assert.equal(calls[2].init.headers['Customer-Session-Token'],token)
})
test('凭证只允许本次会话，关闭后不能读旧会话；流式回答不自动补发', async () => {
  const { client, calls } = fixture(); await client.create()
  assert.throws(()=>client.get('22222222-2222-2222-2222-222222222222'),/其他/)
  await client.stream(id,'original-id','测试问题',()=>{},undefined)
  assert.deepEqual(JSON.parse(calls[1].init.body),{requestId:'original-id',text:'测试问题'})
  await client.close(id); assert.throws(()=>client.get(id),/其他/)
  assert.equal(calls.filter(item=>item.url.endsWith('/messages/stream')).length,1)
})
test('访客链接与悬浮代码只包含入口码，管理员渠道保持旧入口', () => {
  const guest = customerChannelLinks('https://service.example','/prod-api',code,'VISITOR')
  assert.match(guest.entry,/\/platform\/visitor\//); assert.match(guest.widget,/\/widget.js" async/)
  assert.doesNotMatch(guest.widget,/token|userId|tenantId|postMessage/i)
  assert.equal(customerChannelLinks('https://service.example','/prod-api',code).widget,'')
  assert.throws(()=>customerChannelSettings({name:'访客',expiresAt:'2026-11-01',origins:'',capabilityCodes:['order'],accessMode:'VISITOR',visitorDailySessions:200,visitorMessageLimit:20}),/仅开放知识/)
})

test('访客流断开只报告未知结果，不回退 HTTP 重发或更换请求标识', async () => {
  let calls=0
  const client=createCustomerVisitorClient(code,{apiBase:'/dev-api',fetcher:async()=>{
    calls++
    if(calls===1)return{ok:true,json:async()=>({code:200,data:{token,session:{id}}})}
    throw new Error('connection interrupted')
  }})
  await client.create(); await assert.rejects(client.stream(id,'same-request','问题',()=>{}),/connection interrupted/)
  assert.equal(calls,2)
  assert.throws(()=>createCustomerVisitorClient(code,{apiBase:'https://elsewhere.example'}),/同源/)
})

test('页面离开和组件卸载并发关闭只提交一次，关闭失败仍可重试', async () => {
  let closeCalls = 0, release, fail = true
  const client = createCustomerVisitorClient(code, { apiBase: '/dev-api', fetcher: async (url, init) => {
    if (url.endsWith('/sessions')) return { ok: true, json: async () => ({ code: 200, data: { token, session: { id } } }) }
    closeCalls++; assert.equal(init.keepalive, true)
    await new Promise(resolve => { release = resolve })
    if (fail) throw new Error('connection interrupted')
    return { ok: true, json: async () => ({ code: 200 }) }
  } })
  await client.create()
  const first = client.close(id), second = client.close(id)
  assert.equal(closeCalls, 1)
  await assert.rejects(client.close('22222222-2222-2222-2222-222222222222'), /其他/)
  release(); await assert.rejects(first); await assert.rejects(second)
  fail = false
  const retry = client.close(id); release(); await retry
  await client.close(id); assert.equal(closeCalls, 2)
  assert.throws(() => client.get(id), /其他/)
})

test('访客识别、实时票据、结果、取消和播报仅走专用同源接口并携带本次会话凭证', async () => {
  const calls=[]
  const client=createCustomerVisitorClient(code,{apiBase:'/dev-api',fetcher:async(url,init)=>{
    calls.push({url,init})
    if(url.endsWith('/audio'))return new Response(new Blob(['audio'],{type:'audio/mpeg'}))
    return {ok:true,json:async()=>({code:200,data:url.endsWith('/sessions')?{token,session:{id}}:{status:'COMPLETED'}})}
  }})
  await client.create(); await client.speechSettings(id); await client.realtimeTicket(id,'voice')
  const wav=new ArrayBuffer(6444); await client.transcribe(id,'voice',wav)
  await client.speechGet(id,'voice'); await client.speechCancel(id,'voice')
  await client.synthesize(id,'tts','turn',1); const audio=await client.speechAudio(id,'tts')
  assert.equal(audio.type,'audio/mpeg')
  for(const {url,init} of calls.slice(1)) {
    assert.match(url,/\/customer-visitors\/[a-f0-9]+\/sessions\//); assert.doesNotMatch(url,/\/console\/|\?|token=/i)
    assert.equal(init.credentials,'omit'); assert.equal(init.headers['Customer-Session-Token'],token); assert.equal(init.headers['Platform-Token'],undefined)
  }
  assert.equal(calls[3].init.body,wav); assert.equal(calls[3].init.headers['Content-Type'],'audio/wav')
  assert.deepEqual(JSON.parse(calls[6].init.body),{turnId:'turn',partNo:1})
  assert.equal(calls[5].init.keepalive,true)
  assert.throws(()=>client.speechSettings('other-session'),/其他/)
})
