package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.TradeResponse;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;

    public TradeService(
            TradeRepository tradeRepository,
            UserRepository userRepository
    ) {
        this.tradeRepository =
                tradeRepository;

        this.userRepository =
                userRepository;
    }


    public List<TradeResponse> getMyTrades(
            String authenticatedEmail
    ) {

        String email =
                authenticatedEmail
                        .trim()
                        .toLowerCase(Locale.ROOT);


        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Utilisateur introuvable."
                                )
                        );


        return tradeRepository
                .findAllForUser(
                        user.getId()
                )
                .stream()
                .map(trade ->
                        TradeResponse.from(
                                trade,
                                user.getId()
                        )
                )
                .toList();
    }
}