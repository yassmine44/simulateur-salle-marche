package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.FlashSessionResponse;
import tn.esprit.simulateurbackend.dto.FlashStartRequest;
import tn.esprit.simulateurbackend.service.FlashEventService;

@RestController
@RequestMapping("/api/admin/flash")
@PreAuthorize("hasRole('ADMIN')")
public class FlashAdminController {

    private final FlashEventService flashEventService;

    public FlashAdminController(FlashEventService flashEventService) {
        this.flashEventService = flashEventService;
    }

    @GetMapping("/status")
    public ResponseEntity<FlashSessionResponse> status() {
        return ResponseEntity.ok(flashEventService.status());
    }

    @PostMapping("/start")
    public ResponseEntity<FlashSessionResponse> start(
            @RequestBody(required = false) FlashStartRequest request
    ) {
        return ResponseEntity.ok(
                flashEventService.start(
                        request == null ? new FlashStartRequest(null) : request
                )
        );
    }

    @PostMapping("/stop")
    public ResponseEntity<FlashSessionResponse> stop() {
        return ResponseEntity.ok(flashEventService.stop());
    }
}
