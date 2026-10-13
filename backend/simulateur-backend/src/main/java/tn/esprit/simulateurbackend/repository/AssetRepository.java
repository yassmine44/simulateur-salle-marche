package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.Asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository
        extends JpaRepository<Asset, Long> {

    Optional<Asset>
    findBySymbolIgnoreCase(
            String symbol
    );

    Optional<Asset>
    findBySymbolIgnoreCaseAndActiveTrue(
            String symbol
    );

    boolean
    existsBySymbolIgnoreCase(
            String symbol
    );

    List<Asset>
    findAllByActiveTrueOrderBySymbolAsc();
    List<Asset> findByActiveTrueOrderBySymbolAsc();
}