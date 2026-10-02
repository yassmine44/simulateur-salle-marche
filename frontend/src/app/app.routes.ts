import { Routes } from '@angular/router';

import { HomeComponent }
  from './pages/home/home.component';

import { LoginComponent }
  from './pages/login/login.component';

import { RegisterComponent }
  from './pages/register/register.component';

import { ForgotPasswordComponent }
  from './pages/forgot-password/forgot-password.component';

import { ResetPasswordComponent }
  from './pages/reset-password/reset-password.component';

import { Oauth2CallbackComponent }
  from './pages/oauth2-callback/oauth2-callback.component';

import { AppLayoutComponent }
  from './layouts/app-layout/app-layout.component';

import { DashboardComponent }
  from './pages/dashboard/dashboard.component';

import { ProfileComponent }
  from './pages/profile/profile.component';

import { AdminUsersComponent }
  from './pages/admin-users/admin-users.component';

import { authGuard }
  from './guards/auth.guard';

import { adminGuard }
  from './guards/admin.guard';


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
  },

  {
    path: 'reset-password',
    component: ResetPasswordComponent
  },

  {
    path: 'auth/oauth2/callback',
    component: Oauth2CallbackComponent
  },


  // =========================
  // PRIVATE APPLICATION
  // =========================

  {
    path: 'app',

    component:
      AppLayoutComponent,

    canActivate: [
      authGuard
    ],

    children: [

      // =====================
      // DEFAULT
      // =====================

      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },


      // =====================
      // USER
      // =====================

      {
        path: 'dashboard',
        component:
          DashboardComponent
      },

      {
        path: 'profile',
        component:
          ProfileComponent
      },


      // =====================
      // ADMIN USERS
      // =====================

      {
        path: 'admin/users',

        component:
          AdminUsersComponent,

        canActivate: [
          adminGuard
        ]
      },


      // =====================
      // ADMIN SECURITY CENTER
      // =====================

      {
        path: 'admin/security',

        canActivate: [
          adminGuard
        ],

        loadComponent: () =>

          import(
            './pages/admin/security/security.component'
          )

            .then(
              module =>
                module.SecurityComponent
            )
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