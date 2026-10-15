import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import {
  AssetResponse,
  CandleInterval,
  CreateOcoRequest,
  CreateOrderRequest,
  MarketOverviewResponse,
  ModifyOrderRequest,
  OrderBookResponse,
  OrderBookStreamEvent,
  OrderPreviewResponse,
  OrderResponse,
  TradeResponse
} from '../models/trading.models';


@Injectable({
  providedIn: 'root'
})
export class TradingService {

  private readonly apiUrl =
    'http://localhost:8081/api';


  constructor(
    private readonly http: HttpClient
  ) {}


  // ==========================================
  // ASSETS
  // ==========================================

  getAssets(): Observable<AssetResponse[]> {

    return this.http.get<AssetResponse[]>(
      `${this.apiUrl}/assets`,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // CREATE ORDER
  // ==========================================

  createOrder(
    request: CreateOrderRequest
  ): Observable<OrderResponse> {

    return this.http.post<OrderResponse>(
      `${this.apiUrl}/orders`,
      request,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // ORDER BOOK
  // ==========================================

  getOrderBook(
    symbol: string
  ): Observable<OrderBookResponse> {

    const normalizedSymbol =
      symbol.trim().toUpperCase();

    return this.http.get<OrderBookResponse>(
      `${this.apiUrl}/order-book/${encodeURIComponent(normalizedSymbol)}`,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // MARKET DATA
  // ==========================================

  // Dernier cours, statistiques 24 h,
  // flux des transactions et chandeliers.

  getMarketOverview(
    symbol: string,
    interval: CandleInterval
  ): Observable<MarketOverviewResponse> {

    const normalizedSymbol =
      symbol.trim().toUpperCase();

    return this.http.get<MarketOverviewResponse>(
      `${this.apiUrl}/market/${encodeURIComponent(normalizedSymbol)}`,
      {
        params: {
          interval
        },
        withCredentials: true
      }
    );
  }


  // ==========================================
  // ORDER BOOK STREAM (SSE)
  // ==========================================

  /*
   * Émet 'connected' à l'ouverture du flux,
   * 'changed' à chaque modification du carnet
   * et 'disconnected' en cas de coupure.
   *
   * EventSource se reconnecte automatiquement.
   */

  watchOrderBook(
    symbol: string
  ): Observable<OrderBookStreamEvent> {

    const normalizedSymbol =
      symbol.trim().toUpperCase();

    return new Observable<OrderBookStreamEvent>(
      (subscriber) => {

        const source =
          new EventSource(
            `${this.apiUrl}/order-book/${encodeURIComponent(normalizedSymbol)}/stream`,
            {
              withCredentials: true
            }
          );

        source.addEventListener(
          'connected',
          () => subscriber.next('connected')
        );

        source.addEventListener(
          'order-book-changed',
          () => subscriber.next('changed')
        );

        source.onerror =
          () => subscriber.next('disconnected');

        return () => source.close();
      }
    );
  }


  // ==========================================
  // MY ORDERS
  // ==========================================

  getMyOrders(): Observable<OrderResponse[]> {

    return this.http.get<OrderResponse[]>(
      `${this.apiUrl}/orders/me`,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // MY TRADES
  // ==========================================

  getMyTrades(): Observable<TradeResponse[]> {

    return this.http.get<TradeResponse[]>(
      `${this.apiUrl}/trades/me`,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // PREVIEW
  // ==========================================

  // Simule l'ordre sans l'envoyer.

  previewOrder(
    request: CreateOrderRequest
  ): Observable<OrderPreviewResponse> {

    return this.http.post<OrderPreviewResponse>(
      `${this.apiUrl}/orders/preview`,
      request,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // OCO
  // ==========================================

  // Protège des titres détenus : take-profit + stop-loss liés.

  createOco(
    request: CreateOcoRequest
  ): Observable<OrderResponse[]> {

    return this.http.post<OrderResponse[]>(
      `${this.apiUrl}/orders/oco`,
      request,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // MODIFY ORDER
  // ==========================================

  // L'ordre est annulé et remplacé :
  // la réponse est le nouvel ordre.

  modifyOrder(
    orderId: number,
    request: ModifyOrderRequest
  ): Observable<OrderResponse> {

    return this.http.put<OrderResponse>(
      `${this.apiUrl}/orders/${orderId}`,
      request,
      {
        withCredentials: true
      }
    );
  }


  // ==========================================
  // CANCEL ORDER
  // ==========================================

  cancelOrder(
    orderId: number
  ): Observable<OrderResponse> {

    return this.http.patch<OrderResponse>(
      `${this.apiUrl}/orders/${orderId}/cancel`,
      {},
      {
        withCredentials: true
      }
    );
  }
}