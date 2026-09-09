import test from 'node:test'
import assert from 'node:assert/strict'
import {isPlatformConsolePath} from '../src/utils/consoleRoute.js'

test('租户中台登录页和控制台页面使用中台认证', () => {
  assert.equal(isPlatformConsolePath('/platform/login'), true)
  assert.equal(isPlatformConsolePath('/platform/console'), true)
  assert.equal(isPlatformConsolePath('/platform/console/dashboard'), true)
})

test('管理端中台管理菜单不被误判为租户中台', () => {
  assert.equal(isPlatformConsolePath('/platformManage/tenant'), false)
  assert.equal(isPlatformConsolePath('/platform-management/tenant'), false)
  assert.equal(isPlatformConsolePath('/system/platform-tenant/index'), false)
})

test('相似前缀不进入租户中台认证分支', () => {
  assert.equal(isPlatformConsolePath('/platform'), false)
  assert.equal(isPlatformConsolePath('/platform/console-old'), false)
  assert.equal(isPlatformConsolePath(''), false)
})
