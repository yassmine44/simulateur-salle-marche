package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.simulateurbackend.entity.Trade;

import java.util.List;

public interface TradeRepository
        extends JpaRepository<Trade, Long> {


    List<Trade>
    findByAssetSymbolIgnoreCaseOrderByCreatedAtDesc(
            String assetSymbol
    );


    @Query("""
            SELECT DISTINCT t
            FROM Trade t

            JOIN FETCH t.buyOrder buyOrder
            JOIN FETCH buyOrder.user buyUser

            JOIN FETCH t.sellOrder sellOrder
            JOIN FETCH sellOrder.user sellUser

            WHERE buyUser.id = :userId
               OR sellUser.id = :userId

            ORDER BY t.createdAt DESC
            """)
    List<Trade> findAllForUser(
            @Param("userId") Long userId
    );
}