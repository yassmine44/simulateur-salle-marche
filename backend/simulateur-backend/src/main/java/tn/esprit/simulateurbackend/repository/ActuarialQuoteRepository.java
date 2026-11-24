package tn.esprit.simulateurbackend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import tn.esprit.simulateurbackend.entity.ActuarialQuote;

public interface ActuarialQuoteRepository extends JpaRepository<ActuarialQuote, Long> {

    List<ActuarialQuote> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    /** Vue administrateur : derniers devis, tous utilisateurs. */
    List<ActuarialQuote> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant since);

    @Query("select count(distinct q.userEmail) from ActuarialQuote q")
    long countDistinctUsers();

    /** Volume et prime moyenne par version du modèle de fréquence. */
    @Query("""
            select q.frequencyModelVersion as version,
                   count(q) as quotes,
                   avg(q.commercialPremium) as averageCommercialPremium,
                   max(q.createdAt) as lastQuoteAt
            from ActuarialQuote q
            group by q.frequencyModelVersion
            order by max(q.createdAt) desc
            """)
    List<VersionUsage> usageByModelVersion();

    interface VersionUsage {
        String getVersion();
        long getQuotes();
        Double getAverageCommercialPremium(); // avg() renvoie toujours un Double en JPQL
        Instant getLastQuoteAt();
    }
}
