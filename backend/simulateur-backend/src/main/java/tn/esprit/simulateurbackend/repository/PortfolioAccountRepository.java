package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.PortfolioAccount;

import java.util.Optional;

public interface PortfolioAccountRepository
        extends JpaRepository<PortfolioAccount, Long> {

    Optional<PortfolioAccount> findByUserId(Long userId);
}
