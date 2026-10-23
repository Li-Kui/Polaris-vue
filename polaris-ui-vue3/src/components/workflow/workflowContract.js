export function parseWorkflowContract(inputText, outputText) {
  const inputs = JSON.parse(inputText), outputs = JSON.parse(outputText)
  const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
  if (!object(inputs) || inputs.type !== 'object' || !object(inputs.properties) || !object(outputs)) {
    throw new Error('输入须为含 properties 的 object Schema，输出须为字段到 value／expression 的映射对象')
  }
  return { inputs, outputs }
}
