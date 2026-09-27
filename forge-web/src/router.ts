import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('./views/Layout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '控制台' } },
        { path: 'projects', component: () => import('./views/Projects.vue'), meta: { title: '项目管理' } },
        { path: 'projects/:id', component: () => import('./views/ProjectDetail.vue'), meta: { title: '项目详情' } },
        { path: 'builds', component: () => import('./views/Builds.vue'), meta: { title: '构建任务' } },
        { path: 'artifacts', component: () => import('./views/Artifacts.vue'), meta: { title: '制品库' } },
        { path: 'environments', component: () => import('./views/Environments.vue'), meta: { title: '环境管理' } },
        { path: 'credentials', component: () => import('./views/Credentials.vue'), meta: { title: '凭据管理' } },
        { path: 'deployments', component: () => import('./views/DeployCenter.vue'), meta: { title: '部署中心' } },
        { path: 'settings', component: () => import('./views/Settings.vue'), meta: { title: '系统设置' } }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(to => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.loggedIn) return '/login'
  if (to.path === '/login' && auth.loggedIn) return '/dashboard'
  return true
})

export default router
