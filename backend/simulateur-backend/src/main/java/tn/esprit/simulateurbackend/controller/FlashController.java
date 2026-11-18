package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;
import tn.esprit.simulateurbackend.dto.FlashMarketCalendarRequest;
import tn.esprit.simulateurbackend.dto.FlashMarketCalendarResponse;
import tn.esprit.simulateurbackend.dto.FlashMlMetricsDto;
import tn.esprit.simulateurbackend.dto.FlashOrderBookSnapshot;
import tn.esprit.simulateurbackend.dto.FlashOrderRequest;
import tn.esprit.simulateurbackend.dto.FlashPortfolioResponse;
import tn.esprit.simulateurbackend.dto.FlashSessionResponse;
import tn.esprit.simulateurbackend.dto.FlashVoiceInsightRequest;
import tn.esprit.simulateurbackend.dto.FlashVoiceInsightResponse;
import tn.esprit.simulateurbackend.service.FlashEventService;
import tn.esprit.simulateurbackend.service.FlashOrderBookService;
import tn.esprit.simulateurbackend.service.FlashPortfolioService;
import tn.esprit.simulateurbackend.service.FlashVoiceAnalystService;

import java.util.List;

@RestController
@RequestMapping("/api/flash")
public class FlashController {

    private final FlashEventService flashEventService;
    private final FlashPortfolioService portfolioService;
    private final FlashOrderBookService orderBookService;
    private final FlashVoiceAnalystService voiceAnalystService;

    public FlashController(
            FlashEventService flashEventService,
            FlashPortfolioService portfolioService,
            FlashOrderBookService orderBookService,
            FlashVoiceAnalystService voiceAnalystService
    ) {
        this.flashEventService = flashEventService;
        this.portfolioService = portfolioService;
        this.orderBookService = orderBookService;
        this.voiceAnalystService = voiceAnalystService;
    }

    @GetMapping("/status")
    public ResponseEntity<FlashSessionResponse> status() {
        return ResponseEntity.ok(flashEventService.status());
    }

    @GetMapping("/metrics")
    public ResponseEntity<FlashMlMetricsDto> metrics() {
        return ResponseEntity.ok(flashEventService.metrics());
    }

    @GetMapping("/data/{symbol}")
    public ResponseEntity<List<FlashCandleDto>> data(
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(flashEventService.getCandles(symbol));
    }

    @PostMapping("/sessions/join")
    public ResponseEntity<FlashPortfolioResponse> join(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                portfolioService.join(authentication.getName())
        );
    }

    @GetMapping("/sessions/me")
    public ResponseEntity<FlashPortfolioResponse> mySession(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                portfolioService.getPortfolio(authentication.getName())
        );
    }

    @PostMapping("/sessions/orders")
    public ResponseEntity<FlashPortfolioResponse> placeOrder(
            @Valid @RequestBody FlashOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                portfolioService.placeOrder(
                        authentication.getName(),
                        request
                )
        );
    }

    @GetMapping("/orderbook")
    public ResponseEntity<FlashOrderBookSnapshot> orderBook() {
        return ResponseEntity.ok(orderBookService.snapshot());
    }

    @PostMapping("/market-calendar")
    public ResponseEntity<FlashMarketCalendarResponse> marketCalendar(
            @Valid @RequestBody FlashMarketCalendarRequest request
    ) {
        return ResponseEntity.ok(
                voiceAnalystService.generateMarketCalendar(request.symbol())
        );
    }

    @PostMapping("/voice-insight")
    public ResponseEntity<FlashVoiceInsightResponse> voiceInsight(
            @Valid @RequestBody FlashVoiceInsightRequest request
    ) {
        return ResponseEntity.ok(
                voiceAnalystService.analyze(
                        request.symbol(),
                        request.candles(),
                        request.calendarContext()
                )
        );
    }
}
