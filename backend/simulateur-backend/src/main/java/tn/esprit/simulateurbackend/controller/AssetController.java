package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.AssetResponse;
import tn.esprit.simulateurbackend.service.AssetService;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService
            assetService;


    public AssetController(
            AssetService assetService
    ) {

        this.assetService =
                assetService;
    }


    @GetMapping
    public ResponseEntity<List<AssetResponse>>
    getAssets() {

        return ResponseEntity.ok(
                assetService.getActiveAssets()
        );
    }
}