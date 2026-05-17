import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'notes',
      component: () => import('@/views/NoteListView.vue')
    },
    {
      path: '/notes/new',
      name: 'note-create',
      component: () => import('@/views/NoteEditView.vue')
    },
    {
      path: '/notes/:id',
      name: 'note-detail',
      component: () => import('@/views/NoteDetailView.vue'),
      props: true
    },
    {
      path: '/notes/:id/edit',
      name: 'note-edit',
      component: () => import('@/views/NoteEditView.vue'),
      props: true
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue')
    }
  ]
})

export default router
