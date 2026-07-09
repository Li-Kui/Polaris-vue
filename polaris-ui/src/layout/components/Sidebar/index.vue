<template>
    <div :class="['sidebar-theme-wrapper', {'has-logo':showLogo}, settings.sideTheme]" :style="{ backgroundColor: settings.sideTheme === 'theme-dark' ? variables.menuBackground : variables.menuLightBackground }">
        
        <!-- 双轨道排版 - 左侧极窄分类轨道 -->
        <div v-if="polarisLayout === '2'" class="polaris-narrow-rail">
            <el-tooltip
                v-for="route in rootRouters"
                :key="route.path"
                :content="route.meta ? route.meta.title : ''"
                placement="right"
            >
                <div 
                    :class="['rail-item', { active: activeRootPath === route.path }]"
                    @click="handleRootClick(route)"
                >
                    <svg-icon v-if="route.meta && route.meta.icon" :icon-class="route.meta.icon" />
                    <i v-else class="el-icon-menu"></i>
                </div>
            </el-tooltip>
        </div>

        <!-- 菜单展示轨道 -->
        <div class="polaris-menu-rail">
            <logo v-if="showLogo" :collapse="isCollapse" />
            <el-scrollbar :class="settings.sideTheme" wrap-class="scrollbar-wrapper">
                <el-menu
                    :active-text-color="settings.theme"
                    :background-color="settings.sideTheme === 'theme-dark' ? variables.menuBackground : variables.menuLightBackground"
                    :collapse="isCollapse"
                    :collapse-transition="false"
                    :default-active="activeMenu"
                    :text-color="settings.sideTheme === 'theme-dark' ? variables.menuColor : variables.menuLightColor"
                    :unique-opened="true"
                    mode="vertical"
                >
                    <sidebar-item
                        v-for="(route, index) in activeChildrenRouters"
                        :key="route.path + index"
                        :base-path="resolvePathForChild(route)"
                        :item="route"
                    />
                </el-menu>
            </el-scrollbar>
        </div>

    </div>
</template>

<script>
import path from 'path'
import {isExternal} from '@/utils/validate'
import {mapGetters, mapState} from "vuex"
import Logo from "./Logo"
import SidebarItem from "./SidebarItem"
import variables from "@/assets/styles/variables.scss"

export default {
    components: { SidebarItem, Logo },
    data() {
        return {
            activeRootPath: ''
        }
    },
    computed: {
        ...mapState(["settings"]),
        ...mapGetters(["sidebarRouters", "sidebar"]),
        polarisLayout() {
            return this.settings.polarisLayout
        },
        rootRouters() {
            return this.sidebarRouters.filter(r => !r.hidden)
        },
        activeChildrenRouters() {
            if (this.polarisLayout !== '2') {
                return this.sidebarRouters
            }
            const activeRoute = this.rootRouters.find(r => r.path === this.activeRootPath)
            if (!activeRoute) return []
            if (activeRoute.children && activeRoute.children.length > 0) {
                return activeRoute.children
            }
            return [activeRoute]
        },
        activeMenu() {
            const route = this.$route
            const { meta, path } = route
            // if set path, the sidebar will highlight the path you set
            if (meta.activeMenu) {
                return meta.activeMenu
            }
            return path
        },
        showLogo() {
            return this.$store.state.settings.sidebarLogo
        },
        variables() {
            return variables
        },
        isCollapse() {
            return !this.sidebar.opened
        }
    },
    watch: {
        $route: {
            handler(route) {
                const path = route.path
                const matchedRoot = this.rootRouters.find(r => {
                    if (r.path === '/' && r.children) {
                        return r.children.some(child => path === '/' + child.path || path.startsWith('/' + child.path + '/'))
                    }
                    return path.startsWith(r.path + '/') || path === r.path
                })
                if (matchedRoot) {
                    this.activeRootPath = matchedRoot.path
                }
            },
            immediate: true
        }
    },
    methods: {
        resolvePathForChild(route) {
            const rootPath = this.activeRootPath
            const childPath = route.path
            if (isExternal(childPath)) {
                return childPath
            }
            if (isExternal(rootPath)) {
                return rootPath
            }
            return path.resolve(rootPath, childPath)
        },
        handleRootClick(route) {
            this.activeRootPath = route.path
            // 如果是无子级的独立节点，直接跳转
            if (!route.children || route.children.length === 0) {
                this.$router.push(route.path)
            } else {
                // 如果有子级，默认跳转第一个可见的子级菜单
                const firstChild = route.children.find(c => !c.hidden)
                if (firstChild) {
                    let targetPath = route.path === '/' ? '/' + firstChild.path : route.path + '/' + firstChild.path
                    this.$router.push(targetPath)
                } else {
                    this.$router.push(route.path)
                }
            }
        }
    }
}
</script>
