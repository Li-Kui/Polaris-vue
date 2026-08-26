<template>
  <div class="app-container">
    <WorkflowPage
      appearance="admin"
      :can-edit="canEdit"
      :can-publish="canPublish"
      :can-execute="canExecute"
      :can-approve="canApprove"
    />
  </div>
</template>

<script>
import WorkflowPage from '@/components/workflow/WorkflowPage.vue'
import useUserStore from '@/store/modules/user'

export default {
  name: 'AiWorkflow',
  components: {
    WorkflowPage
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    permissions() {
      return this.userStore.permissions || []
    },
    isSuperAdmin() {
      return (this.userStore.roles || []).includes('admin')
        || this.permissions.includes('*:*:*')
        || this.permissions.includes('workflow:admin')
    },
    canEdit() {
      return this.isSuperAdmin
        || this.permissions.includes('workflow:edit')
    },
    canPublish() {
      return this.isSuperAdmin
        || this.permissions.includes('workflow:publish')
    },
    canExecute() {
      return this.isSuperAdmin
        || this.permissions.includes('workflow:execute')
    },
    canApprove() {
      return this.isSuperAdmin
        || this.permissions.includes('workflow:approve')
    }
  }
}
</script>
