package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.AssetType;

public record AssetResponse(

        Long id,

        String symbol,

        String name,

        AssetType type,

        String currency,

        boolean active

) {

    public static AssetResponse from(
            Asset asset
    ) {

        return new AssetResponse(

                asset.getId(),

                asset.getSymbol(),

                asset.getName(),

                asset.getType(),

                asset.getCurrency(),

                asset.isActive()
        );
    }
}