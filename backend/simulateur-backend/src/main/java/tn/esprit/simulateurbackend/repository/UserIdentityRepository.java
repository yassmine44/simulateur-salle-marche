package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.AuthProvider;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.entity.UserIdentity;

import java.util.List;
import java.util.Optional;

public interface UserIdentityRepository
        extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity>
    findByProviderAndProviderSubject(
            AuthProvider provider,
            String providerSubject
    );


    boolean existsByProviderAndProviderSubject(
            AuthProvider provider,
            String providerSubject
    );


    List<UserIdentity> findAllByUser(
            User user
    );
}