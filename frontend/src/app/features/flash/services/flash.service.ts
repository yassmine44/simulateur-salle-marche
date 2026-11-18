import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import { Observable, Subject } from 'rxjs';

import {
  FlashCandle,
  FlashMlMetrics,
  FlashOrderBookSnapshot,
  FlashPortfolio,
  FlashSession,
  FlashStartRequest
} from '../models/flash.models';

type OrderSide = 'BUY' | 'SELL';


@Injectable({
  providedIn: 'root'
})
export class FlashService implements OnDestroy {

  private readonly apiUrl = 'http://localhost:8081/api/flash';
  private readonly adminUrl = 'http://localhost:8081/api/admin/flash';
  private readonly wsUrl = 'http://localhost:8081/ws';

  private readonly eventsSubject = new Subject<FlashSession>();
  readonly events$ = this.eventsSubject.asObservable();

  private readonly orderBookSubject =
    new Subject<FlashOrderBookSnapshot>();
  readonly orderBook$ = this.orderBookSubject.asObservable();

  private stompClient: Client | null = null;
  private connected = false;


  constructor(private readonly http: HttpClient) {}


  getStatus(): Observable<FlashSession> {
    return this.http.get<FlashSession>(
      `${this.apiUrl}/status`,
      { withCredentials: true }
    );
  }


  getMetrics(): Observable<FlashMlMetrics> {
    return this.http.get<FlashMlMetrics>(
      `${this.apiUrl}/metrics`,
      { withCredentials: true }
    );
  }


  getCandles(symbol: string): Observable<FlashCandle[]> {
    return this.http.get<FlashCandle[]>(
      `${this.apiUrl}/data/${encodeURIComponent(symbol)}`,
      { withCredentials: true }
    );
  }


  getOrderBook(): Observable<FlashOrderBookSnapshot> {
    return this.http.get<FlashOrderBookSnapshot>(
      `${this.apiUrl}/orderbook`,
      { withCredentials: true }
    );
  }


  start(request: FlashStartRequest): Observable<FlashSession> {
    return this.http.post<FlashSession>(
      `${this.adminUrl}/start`,
      request,
      { withCredentials: true }
    );
  }


  stop(): Observable<FlashSession> {
    return this.http.post<FlashSession>(
      `${this.adminUrl}/stop`,
      {},
      { withCredentials: true }
    );
  }


  join(): Observable<FlashPortfolio> {
    return this.http.post<FlashPortfolio>(
      `${this.apiUrl}/sessions/join`,
      {},
      { withCredentials: true }
    );
  }


  getPortfolio(): Observable<FlashPortfolio> {
    return this.http.get<FlashPortfolio>(
      `${this.apiUrl}/sessions/me`,
      { withCredentials: true }
    );
  }


  /**
   * Limit order via authenticated REST. Book updates arrive on
   * /topic/multiplayer/orderbook (STOMP). Optional STOMP submit also
   * exists at /app/multiplayer/order for clients that prefer messaging.
   */
  placeOrder(
    side: OrderSide,
    quantity: number,
    price: number
  ): Observable<FlashPortfolio> {
    return this.http.post<FlashPortfolio>(
      `${this.apiUrl}/sessions/orders`,
      { side, quantity, price },
      { withCredentials: true }
    );
  }


  /** STOMP limit-order submit (same matching engine as REST). */
  placeOrderViaStomp(
    side: OrderSide,
    quantity: number,
    price: number
  ): void {
    if (!this.stompClient?.connected) {
      return;
    }
    this.stompClient.publish({
      destination: '/app/multiplayer/order',
      body: JSON.stringify({ side, quantity, price })
    });
  }


  connect(): void {
    if (this.connected || this.stompClient?.active) {
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(this.wsUrl) as WebSocket,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected = true;
        client.subscribe('/topic/flash/events', (message: IMessage) => {
          if (!message.body) {
            return;
          }
          try {
            this.eventsSubject.next(
              JSON.parse(message.body) as FlashSession
            );
          } catch {
            // ignore malformed frames
          }
        });
        client.subscribe('/topic/multiplayer/orderbook', (message: IMessage) => {
          if (!message.body) {
            return;
          }
          try {
            this.orderBookSubject.next(
              JSON.parse(message.body) as FlashOrderBookSnapshot
            );
          } catch {
            // ignore
          }
        });
      },
      onDisconnect: () => {
        this.connected = false;
      },
      onStompError: () => {
        this.connected = false;
      }
    });

    this.stompClient = client;
    client.activate();
  }


  disconnect(): void {
    this.connected = false;
    void this.stompClient?.deactivate();
    this.stompClient = null;
  }


  ngOnDestroy(): void {
    this.disconnect();
    this.eventsSubject.complete();
    this.orderBookSubject.complete();
  }
}
