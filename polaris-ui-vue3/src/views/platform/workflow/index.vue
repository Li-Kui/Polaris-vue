<template>
  <div class="platform-workflow-page">
    <WorkflowPage
      appearance="platform"
      :can-edit="canEdit"
      :can-publish="canPublish"
      :can-execute="canExecute"
      :can-debug="canDebug"
      :can-approve="canApprove"
    />
  </div>
</template>

<script>
import WorkflowPage from '@/components/workflow/WorkflowPage.vue'
import usePlatformUserStore from '@/store/modules/platformUser'

export default {
  name: 'PlatformWorkflow',
  components: {
    WorkflowPage
  },
  computed: {
    platformUserStore() {
      return usePlatformUserStore()
    },
    permissions() {
      return this.platformUserStore.permissions || []
    },
    isTenantAdmin() {
      return this.platformUserStore.user?.role === 'admin'
        || this.permissions.includes('workflow:admin')
    },
    canEdit() {
      return this.isTenantAdmin || this.permissions.includes('workflow:edit')
    },
    canPublish() {
      return this.isTenantAdmin || this.permissions.includes('workflow:publish')
    },
    canExecute() {
      return !!this.platformUserStore.token
        || this.isTenantAdmin
        || this.permissions.includes('workflow:execute')
    },
    canDebug() {
      return this.isTenantAdmin || this.permissions.includes('workflow:debug')
    },
    canApprove() {
      return !!this.platformUserStore.token
        && (this.isTenantAdmin || this.permissions.includes('workflow:approve'))
    }
  }
}
</script>

<style scoped>
.platform-workflow-page {
  min-height: 100%;
  padding: 20px;
  background:
    radial-gradient(circle at 100% 0, rgb(109 93 252 / 10%), transparent 34%),
    radial-gradient(circle at 0 100%, rgb(31 184 205 / 8%), transparent 30%);
}
</style>
