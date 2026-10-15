package tn.esprit.simulateurbackend.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/*
 * Annule les ordres DAY arrivés à échéance (minuit).
 */
@Service
public class OrderExpiryService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private final OrderRepository orderRepository;
    private final LinkedOrderService linkedOrderService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderExpiryService(
            OrderRepository orderRepository,
            LinkedOrderService linkedOrderService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.linkedOrderService = linkedOrderService;
        this.eventPublisher = eventPublisher;
    }


    @Transactional
    public int expireDueOrders(
            Instant now
    ) {

        List<Order> expired =
                orderRepository.findExpired(now, OPEN_STATUSES);

        Set<String> symbols = new LinkedHashSet<>();

        for (Order order : expired) {

            order.cancel(CancelReason.EXPIRED);

            linkedOrderService.cancelSiblings(order, CancelReason.EXPIRED);

            symbols.add(order.getAssetSymbol());
        }

        orderRepository.saveAll(expired);

        symbols.forEach(symbol ->
                eventPublisher.publishEvent(new OrderBookChangedEvent(symbol))
        );

        return expired.size();
    }
}
