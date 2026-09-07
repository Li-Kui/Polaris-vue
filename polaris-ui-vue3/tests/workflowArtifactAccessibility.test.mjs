import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'

const workbench = await readFile(
  new URL('../src/components/workflow/WorkflowWorkbench.vue', import.meta.url), 'utf8')
const editor = await readFile(
  new URL('../src/components/workflow/WorkflowArtifactEditor.vue', import.meta.url), 'utf8')

test('保存产物使用三步导航并移除无关资源入口', () => {
  assert.match(workbench, /aria-label="保存产物节点配置步骤"/)
  assert.match(workbench, /<strong>保存内容<\/strong>/)
  assert.match(workbench, /<strong>文件设置<\/strong>/)
  assert.match(workbench, /<strong>预览测试<\/strong>/)
  assert.match(workbench, /!isSubWorkflowNode && !isArtifactNode/)
})

test('文件设置解释自动扩展名、保留期和私有下载边界', () => {
  assert.match(editor, /扩展名由系统维护/)
  assert.match(editor, /工作流运行期间不会过期/)
  assert.match(editor, /不生成公开链接/)
  assert.match(editor, /type="button"/)
})

test('预览明确声明不落盘', () => {
  assert.match(workbench, /不会创建数据库记录或真实文件/)
  assert.match(workbench, /无落盘预览/)
})
