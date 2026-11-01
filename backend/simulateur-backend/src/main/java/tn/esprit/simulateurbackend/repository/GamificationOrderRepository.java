package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.entity.GamificationOrder;

import java.util.List;

public interface GamificationOrderRepository
        extends JpaRepository<GamificationOrder, Long> {

    List<GamificationOrder>
    findByPortfolioIdOrderByCreatedAtDesc(
            Long portfolioId
    );

    @Modifying
    @Transactional
    void deleteByPortfolioId(Long portfolioId);
}
