import { AdminUsersComponent } from './pages/admin-users/admin-users.component';
import { adminGuard } from './guards/admin.guard';
import { Routes } from '@angular/router';

import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { ForgotPasswordComponent } from './pages/forgot-password/forgot-password.component';

import { AppLayoutComponent } from './layouts/app-layout/app-layout.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { ProfileComponent } from './pages/profile/profile.component';

import { authGuard } from './guards/auth.guard';
import {
  ResetPasswordComponent
} from './pages/reset-password/reset-password.component';
export const routes: Routes = [

  // =========================
  // PUBLIC
  // =========================

  {
    path: '',
    component: HomeComponent
  },

  {
    path: 'login',
    component: LoginComponent
  },

  {
    path: 'register',
    component: RegisterComponent
  },

  {
    path: 'forgot-password',
    component: ForgotPasswordComponent
  },{
  path: 'reset-password',
  component: ResetPasswordComponent
},


  // =========================
  // PRIVATE APPLICATION
  // =========================

  {
    path: 'app',
    component: AppLayoutComponent,
    canActivate: [authGuard],

    children: [
      { path: 'admin/users', component: AdminUsersComponent, canActivate: [adminGuard] },

      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },

      {
        path: 'dashboard',
        component: DashboardComponent
      },

      {
        path: 'profile',
        component: ProfileComponent
      }

    ]
  },


  // =========================
  // UNKNOWN ROUTE
  // =========================

  {
    path: '**',
    redirectTo: ''
  }

];