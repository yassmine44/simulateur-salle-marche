package tn.esprit.simulateurbackend.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.ActuarialQuote;

public interface ActuarialQuoteRepository extends JpaRepository<ActuarialQuote, Long> {

    List<ActuarialQuote> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);
}
