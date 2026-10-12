package tn.esprit.simulateurbackend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.AssetType;
import tn.esprit.simulateurbackend.repository.AssetRepository;

@Component
public class AssetDataInitializer
        implements CommandLineRunner {

    private final AssetRepository
            assetRepository;


    public AssetDataInitializer(
            AssetRepository assetRepository
    ) {

        this.assetRepository =
                assetRepository;
    }


    @Override
    public void run(
            String... args
    ) {

        createAsset(
                "AAPL",
                "Apple",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "MSFT",
                "Microsoft",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "NVDA",
                "NVIDIA",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "TSLA",
                "Tesla",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "AMZN",
                "Amazon",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "META",
                "Meta Platforms",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "GOOGL",
                "Alphabet",
                AssetType.STOCK,
                "USD"
        );

        createAsset(
                "BTCUSD",
                "Bitcoin / US Dollar",
                AssetType.CRYPTO,
                "USD"
        );

        createAsset(
                "ETHUSD",
                "Ethereum / US Dollar",
                AssetType.CRYPTO,
                "USD"
        );

        createAsset(
                "EURUSD",
                "Euro / US Dollar",
                AssetType.FOREX,
                "USD"
        );
    }


    private void createAsset(
            String symbol,
            String name,
            AssetType type,
            String currency
    ) {

        if (
                assetRepository
                        .existsBySymbolIgnoreCase(
                                symbol
                        )
        ) {

            return;
        }


        Asset asset =
                new Asset();

        asset.setSymbol(symbol);

        asset.setName(name);

        asset.setType(type);

        asset.setCurrency(currency);

        asset.setActive(true);


        assetRepository.save(asset);
    }
}