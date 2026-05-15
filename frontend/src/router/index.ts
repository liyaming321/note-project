import { createRouter, createWebHistory } from 'vue-router'

import NoteDetailView from '@/views/NoteDetailView.vue'
import NoteEditView from '@/views/NoteEditView.vue'
import NoteListView from '@/views/NoteListView.vue'
import SettingsView from '@/views/SettingsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'notes',
      component: NoteListView
    },
    {
      path: '/notes/new',
      name: 'note-create',
      component: NoteEditView
    },
    {
      path: '/notes/:id',
      name: 'note-detail',
      component: NoteDetailView,
      props: true
    },
    {
      path: '/notes/:id/edit',
      name: 'note-edit',
      component: NoteEditView,
      props: true
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView
    }
  ]
})

export default router
