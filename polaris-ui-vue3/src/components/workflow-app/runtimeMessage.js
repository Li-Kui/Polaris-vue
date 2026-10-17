import {ElMessage} from 'element-plus'

// Keep public-page feedback inside its local theme; never change the admin's html theme.
const show = (type, message) => ElMessage({
  type,
  message,
  appendTo: document.querySelector('.runtime-page') || document.body
})

export const runtimeMessage = {
  success: message => show('success', message),
  warning: message => show('warning', message),
  error: message => show('error', message)
}
