<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const handleLogout = async () => {
  await userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">短链平台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/link">短链管理</el-menu-item>
        <el-menu-item index="/group">分组管理</el-menu-item>
        <el-menu-item index="/dashboard">数据看板</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="username">{{ userStore.username || '已登录' }}</span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.logo {
  height: 60px;
  line-height: 60px;
  padding: 0 20px;
  font-weight: 600;
  font-size: 18px;
  color: #111827;
  border-bottom: 1px solid #e5e7eb;
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.username {
  color: #374151;
  font-size: 14px;
}
</style>
