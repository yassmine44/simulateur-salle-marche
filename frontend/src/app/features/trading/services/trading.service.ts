import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import {
  AssetResponse,
  CreateOrderRequest,
  OrderBookResponse,
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