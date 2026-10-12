package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.AssetResponse;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository
            assetRepository;


    public AssetService(
            AssetRepository assetRepository
    ) {

        this.assetRepository =
                assetRepository;
    }


    public List<AssetResponse>
    getActiveAssets() {

        return assetRepository
                .findAllByActiveTrueOrderBySymbolAsc()
                .stream()
                .map(AssetResponse::from)
                .toList();
    }
}