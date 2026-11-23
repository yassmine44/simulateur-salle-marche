import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// sockjs-client expects a Node-style global in the browser.
(window as unknown as { global: Window }).global = window;

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
