import { Injectable } from '@angular/core';

declare global {
  interface Window {
    grecaptcha?: {
      enterprise: {
        ready(callback: () => void): void;

        execute(
          siteKey: string,
          options: {
            action: string;
          }
        ): Promise<string>;
      };
    };
  }
}

@Injectable({
  providedIn: 'root'
})
export class RecaptchaService {

  /*
   * Mets ici le ID / Site Key
   * de "Trading Room Local".
   *
   * Cette clé est PUBLIQUE.
   */
  private readonly siteKey =
    '6Lf1EtstAAAAAJVaO96oz1T5e5b0_vOqD4GWOQ7t';

  private scriptPromise?: Promise<void>;


  private loadScript(): Promise<void> {

    if (window.grecaptcha?.enterprise) {
      return Promise.resolve();
    }

    if (this.scriptPromise) {
      return this.scriptPromise;
    }

    this.scriptPromise =
      new Promise<void>((resolve, reject) => {

        const existingScript =
          document.getElementById(
            'recaptcha-enterprise-script'
          );

        if (existingScript) {

          existingScript.addEventListener(
            'load',
            () => resolve()
          );

          return;
        }

        const script =
          document.createElement('script');

        script.id =
          'recaptcha-enterprise-script';

        script.src =
          `https://www.google.com/recaptcha/enterprise.js?render=${encodeURIComponent(
            this.siteKey
          )}`;

        script.async = true;
        script.defer = true;

        script.onload =
          () => resolve();

        script.onerror =
          () => reject(
            new Error(
              'Impossible de charger reCAPTCHA.'
            )
          );

        document.head.appendChild(script);
      });

    return this.scriptPromise;
  }


  async execute(
    action: string
  ): Promise<string> {

    await this.loadScript();

    if (!window.grecaptcha?.enterprise) {

      throw new Error(
        'reCAPTCHA Enterprise indisponible.'
      );
    }

    return new Promise<string>(
      (resolve, reject) => {

        window.grecaptcha!
          .enterprise
          .ready(async () => {

            try {

              const token =
                await window.grecaptcha!
                  .enterprise
                  .execute(
                    this.siteKey,
                    {
                      action
                    }
                  );

              resolve(token);

            } catch (error) {

              reject(error);
            }

          });

      }
    );
  }
}