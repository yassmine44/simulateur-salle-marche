import {
  Component,
  inject,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';

import { HttpErrorResponse } from '@angular/common/http';

import { FormsModule } from '@angular/forms';

import { RouterLink } from '@angular/router';

import { finalize } from 'rxjs';

import { StatementResponse } from '../../models/portfolio.models';

import { PortfolioService }
  from '../../services/portfolio.service';


/*
 * Relevé de compte imprimable.
 *
 * « Télécharger en PDF » ouvre l'impression du navigateur :
 * choisir « Enregistrer au format PDF » produit le document,
 * sans dépendance supplémentaire.
 */
@Component({
  selector: 'app-statement',

  standalone: true,

  imports: [
    CommonModule,
    FormsModule,
    RouterLink
  ],

  templateUrl: './statement.html',

  styleUrl: './statement.scss'
})
export class Statement
  implements OnInit {

  private readonly portfolioService =
    inject(PortfolioService);


  // Mois affiché, au format AAAA-MM.

  month =
    new Date().toISOString().slice(0, 7);

  readonly currentMonth =
    this.month;

  statement:
    StatementResponse | null = null;

  loading = false;

  errorMessage = '';


  ngOnInit(): void {

    this.load();
  }


  load(): void {

    const [year, month] =
      this.month.split('-').map(Number);

    if (!year || !month) {
      return;
    }

    const from =
      `${this.month}-01`;

    // Dernier jour du mois (le serveur ramène une date future à aujourd'hui).

    const lastDay =
      new Date(year, month, 0).getDate();

    const to =
      `${this.month}-${String(lastDay).padStart(2, '0')}`;

    this.loading = true;

    this.errorMessage = '';

    this.portfolioService
      .getStatement(from, to)
      .pipe(
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe({

        next: (statement) => {
          this.statement = statement;
        },

        error: (error) => {

          this.statement = null;

          this.errorMessage =
            error instanceof HttpErrorResponse && error.error?.detail
              ? error.error.detail
              : 'Impossible de générer le relevé.';
        }
      });
  }


  print(): void {

    window.print();
  }
}
