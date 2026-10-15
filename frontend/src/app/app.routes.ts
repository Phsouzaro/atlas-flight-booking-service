import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';
import { LoginPage } from './features/login/login.page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, title: 'Entrar · Atlas' },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'chat' },
      {
        path: 'chat',
        title: 'Assistente · Atlas',
        loadComponent: () => import('./features/chat/chat.page').then((m) => m.ChatPage),
      },
      {
        path: 'reservas',
        title: 'Minhas reservas · Atlas',
        loadComponent: () => import('./features/bookings/bookings.page').then((m) => m.BookingsPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
