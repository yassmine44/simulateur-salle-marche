import { Routes } from '@angular/router';

import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';

import { AppLayoutComponent } from './layouts/app-layout/app-layout.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';

import { authGuard } from './guards/auth.guard';

export const routes: Routes = [

  // PUBLIC

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


  // PRIVATE APPLICATION

  {
    path: 'app',

    component: AppLayoutComponent,

    canActivate: [authGuard],

    children: [

      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },

      {
        path: 'dashboard',
        component: DashboardComponent
      }

    ]
  },


  // UNKNOWN ROUTE

  {
    path: '**',
    redirectTo: ''
  }

];