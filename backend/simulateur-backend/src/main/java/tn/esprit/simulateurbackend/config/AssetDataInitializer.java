package tn.esprit.simulateurbackend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.AssetType;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.math.BigDecimal;

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

        // symbole, nom, type, devise,
        // prix de référence, pas de cotation, quantité minimale

        createAsset("AAPL", "Apple", AssetType.STOCK, "USD", "215", "0.01", "1");

        createAsset("MSFT", "Microsoft", AssetType.STOCK, "USD", "420", "0.01", "1");

        createAsset("NVDA", "NVIDIA", AssetType.STOCK, "USD", "130", "0.01", "1");

        createAsset("TSLA", "Tesla", AssetType.STOCK, "USD", "250", "0.01", "1");

        createAsset("AMZN", "Amazon", AssetType.STOCK, "USD", "190", "0.01", "1");

        createAsset("META", "Meta Platforms", AssetType.STOCK, "USD", "580", "0.01", "1");

        createAsset("GOOGL", "Alphabet", AssetType.STOCK, "USD", "170", "0.01", "1");

        createAsset("BTCUSD", "Bitcoin / US Dollar", AssetType.CRYPTO, "USD", "68000", "0.01", "0.0001");

        createAsset("ETHUSD", "Ethereum / US Dollar", AssetType.CRYPTO, "USD", "2600", "0.01", "0.001");

        createAsset("EURUSD", "Euro / US Dollar", AssetType.FOREX, "USD", "1.17", "0.0001", "100");
    }


    private void createAsset(
            String symbol,
            String name,
            AssetType type,
            String currency,
            String referencePrice,
            String tickSize,
            String minQuantity
    ) {

        Asset asset =
                assetRepository
                        .findBySymbolIgnoreCase(
                                symbol
                        )
                        .orElse(null);


        if (asset == null) {

            asset =
                    new Asset();

            asset.setSymbol(symbol);

            asset.setName(name);

            asset.setType(type);

            asset.setCurrency(currency);

            asset.setActive(true);
        }


        // Complète aussi les actifs créés
        // avant l'ajout des règles de trading,
        // sans écraser une valeur déjà renseignée.

        if (asset.getReferencePrice() == null) {

            asset.setReferencePrice(
                    new BigDecimal(referencePrice)
            );
        }

        if (asset.getTickSize() == null) {

            asset.setTickSize(
                    new BigDecimal(tickSize)
            );
        }

        if (asset.getMinQuantity() == null) {

            asset.setMinQuantity(
                    new BigDecimal(minQuantity)
            );
        }


        assetRepository.save(asset);
    }
}
