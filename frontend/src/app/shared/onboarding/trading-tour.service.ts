import { Injectable } from '@angular/core';
import { driver, type DriveStep, type Driver } from 'driver.js';

export type TradingTourMode = 'offline' | 'multiplayer';

const STORAGE_KEY: Record<TradingTourMode, string> = {
  offline: 'six7.tour.offline.v1',
  multiplayer: 'six7.tour.multiplayer.v1'
};

/**
 * First-run onboarding walkthrough for Offline Challenges
 * and Multiplayer (Driver.js).
 */
@Injectable({
  providedIn: 'root'
})
export class TradingTourService {

  private running = false;
  private activeTour: Driver | null = null;


  hasCompleted(mode: TradingTourMode): boolean {
    try {
      return localStorage.getItem(STORAGE_KEY[mode]) === '1';
    } catch {
      return false;
    }
  }


  markCompleted(mode: TradingTourMode): void {
    try {
      localStorage.setItem(STORAGE_KEY[mode], '1');
    } catch {
      // ignore quota / private mode
    }
  }


  /** Starts the 5-step tour once per mode when DOM targets exist. */
  startIfNeeded(mode: TradingTourMode, force = false): void {
    if (this.running) {
      return;
    }
    if (!force && this.hasCompleted(mode)) {
      return;
    }
    window.setTimeout(() => this.launch(mode), 500);
  }


  private launch(mode: TradingTourMode): void {
    const steps = this.buildSteps(mode).filter(step => {
      const el = step.element;
      if (typeof el === 'string') {
        return !!document.querySelector(el);
      }
      return !!el;
    });

    if (steps.length < 3) {
      console.warn('[Tour] Not enough targets visible yet', mode);
      return;
    }

    this.running = true;

    const tour = driver({
      showProgress: true,
      progressText: 'Step {{current}} of {{total}}',
      nextBtnText: 'Next',
      prevBtnText: 'Back',
      doneBtnText: 'Finish',
      showButtons: ['next', 'previous', 'close'],
      allowClose: true,
      overlayColor: 'rgba(2, 12, 27, 0.78)',
      stagePadding: 12,
      stageRadius: 14,
      animate: true,
      smoothScroll: true,
      // Keep the focused node interactive and visually above the overlay.
      disableActiveInteraction: false,
      popoverClass: 'six7-tour-popover',
      steps,
      onHighlightStarted: (element) => {
        const node = element as HTMLElement | undefined;
        if (!node?.style) {
          return;
        }
        // Escape parent stacking traps (transformed / overflow containers).
        node.style.setProperty('z-index', '100000010', 'important');
        node.style.setProperty('position', 'relative', 'important');
        node.style.setProperty('opacity', '1', 'important');
        node.style.setProperty('filter', 'none', 'important');
      },
      onDeselected: (element) => {
        const node = element as HTMLElement | undefined;
        if (!node?.style) {
          return;
        }
        node.style.removeProperty('z-index');
        node.style.removeProperty('position');
        node.style.removeProperty('opacity');
        node.style.removeProperty('filter');
      },
      onPopoverRender: (popover) => {
        const closeBtn = popover.wrapper.querySelector(
          '.driver-popover-close-btn'
        ) as HTMLElement | null;
        if (closeBtn) {
          closeBtn.setAttribute('aria-label', 'Skip Tour');
          closeBtn.title = 'Skip Tour';
        }

        const footer = popover.footerButtons;
        if (!footer) {
          return;
        }
        let skip = footer.querySelector('.six7-skip-tour') as HTMLButtonElement | null;
        if (!skip) {
          skip = document.createElement('button');
          skip.type = 'button';
          skip.className = 'six7-skip-tour driver-popover-btn';
          skip.textContent = 'Skip Tour';
          skip.addEventListener('click', () => {
            this.markCompleted(mode);
            this.running = false;
            tour.destroy();
          });
          footer.insertBefore(skip, footer.firstChild);
        }
      },
      onDestroyStarted: () => {
        this.markCompleted(mode);
        this.running = false;
        this.activeTour = null;
        tour.destroy();
      },
      onDestroyed: () => {
        this.running = false;
        this.activeTour = null;
      }
    });

    this.activeTour = tour;
    tour.drive();
  }


  private buildSteps(mode: TradingTourMode): DriveStep[] {
    return [
      {
        element: '[data-tour="chart"]',
        popover: {
          title: 'Welcome to the simulation',
          description:
            'Welcome to the simulation! This chart shows the real-time movement of your selected stock.',
          side: 'bottom',
          align: 'start'
        }
      },
      {
        element: '[data-tour="desk-output"]',
        popover: {
          title: 'Step 2: AI Voice Commentator & Market Data',
          description:
            'This panel displays live insights from our built-in AI desk about market trends, '
            + 'volatility signals, and trading advice as candles form.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: mode === 'offline'
          ? '[data-tour="quant-analysis"]'
          : '[data-tour="portfolio-metrics"]',
        popover: {
          title: 'Quantitative signals',
          description: mode === 'offline'
            ? 'Use these quantitative signals to confirm your trades. If you are offline, these values are simulated.'
            : 'Track your cash, shares, and P&L here while the synchronized market stream advances.',
          side: 'top',
          align: 'center'
        }
      },
      {
        element: '[data-tour="market-calendar"]',
        popover: {
          title: 'Market Calendar',
          description:
            'Check upcoming macroeconomic events. These define the market sentiment for the session.',
          side: 'left',
          align: 'start'
        }
      },
      {
        element: '[data-tour="trade-actions"]',
        popover: {
          title: 'Execute trades',
          description:
            'When you are ready, use these controls to execute your trades and manage your virtual capital. Good luck!',
          side: 'top',
          align: 'center'
        }
      }
    ];
  }
}
