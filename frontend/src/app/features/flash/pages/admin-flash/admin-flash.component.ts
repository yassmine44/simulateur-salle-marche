import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Subscription } from 'rxjs';

import { FlashSession } from '../../models/flash.models';
import { FlashService } from '../../services/flash.service';
import {
  DEFAULT_VERIFIED_STOCK,
  VERIFIED_TUNISIAN_STOCKS
} from '../../data/verified-tunisian-stocks';


@Component({
  selector: 'app-admin-flash',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-flash.component.html',
  styleUrl: './admin-flash.component.scss'
})
export class AdminFlashComponent implements OnInit, OnDestroy {

  private readonly flashService = inject(FlashService);

  readonly datasetLabel = 'Dataset: 2018 - 2026 Historical Market';
  readonly verifiedStocks = VERIFIED_TUNISIAN_STOCKS;

  selectedSymbol = DEFAULT_VERIFIED_STOCK;
  busy = false;
  error = '';
  session: FlashSession | null = null;

  private eventSub: Subscription | null = null;


  ngOnInit(): void {
    this.flashService.connect();
    this.eventSub = this.flashService.events$.subscribe(
      session => { this.session = session; }
    );
    this.flashService.getStatus().subscribe({
      next: session => {
        this.session = session;
        if (session?.symbol) {
          this.selectedSymbol = session.symbol;
        }
      }
    });
  }


  ngOnDestroy(): void {
    this.eventSub?.unsubscribe();
  }


  start(): void {
    this.busy = true;
    this.error = '';
    const symbol = (this.selectedSymbol || DEFAULT_VERIFIED_STOCK).trim();
    this.flashService.start({ symbol }).subscribe({
      next: session => {
        this.session = session;
        this.busy = false;
      },
      error: err => {
        this.busy = false;
        this.error =
          err?.status === 403
            ? 'Only ADMIN accounts can start the multiplayer event.'
            : (err?.error?.message || err?.message || 'Unable to start event.');
      }
    });
  }


  stop(): void {
    this.busy = true;
    this.error = '';
    this.flashService.stop().subscribe({
      next: session => {
        this.session = session;
        this.busy = false;
      },
      error: err => {
        this.busy = false;
        this.error =
          err?.status === 403
            ? 'Only ADMIN accounts can stop the multiplayer event.'
            : (err?.error?.message || err?.message || 'Unable to stop event.');
      }
    });
  }
}
