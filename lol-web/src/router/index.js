import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { guestOnly: true } },
  { path: '/register', component: () => import('../views/Register.vue'), meta: { guestOnly: true } },
  { path: '/', component: () => import('../views/Home.vue'), meta: { requiresAuth: true } },
  // 玩家资料页：战绩 / 段位 / 大区 / 主玩位置。从组员、在线列表等任何地方点进来都走这里，
  // 是独立路由所以可以直接刷新和分享链接。
  {
    path: '/player/:id',
    name: 'player',
    component: () => import('../views/PlayerDetail.vue'),
    meta: { requiresAuth: true }
  },
  // 开黑房间：一支队伍一个聊天室，创建队伍时就有了。独立路由，可以刷新和分享。
  {
    path: '/teamup/:id',
    name: 'teamupRoom',
    component: () => import('../views/TeamupRoom.vue'),
    meta: { requiresAuth: true }
  },
  // 兜底：原来访问不存在的路径会渲染出一片空白，没有任何解释
  { path: '/:pathMatch(.*)*', component: () => import('../views/NotFound.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    // 记下来原本想去哪，登录后送回去
    next({ path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } })
  } else if (to.meta.guestOnly && token) {
    next('/')
  } else {
    next()
  }
})

export default router
