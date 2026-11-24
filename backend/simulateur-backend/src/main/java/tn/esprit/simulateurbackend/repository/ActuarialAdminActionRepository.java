package tn.esprit.simulateurbackend.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.ActuarialAdminAction;

public interface ActuarialAdminActionRepository extends JpaRepository<ActuarialAdminAction, Long> {

    List<ActuarialAdminAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
