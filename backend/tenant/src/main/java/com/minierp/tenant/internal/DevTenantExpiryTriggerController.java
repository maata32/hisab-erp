package com.minierp.tenant.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ⚠️ TEMPORAIRE — À SUPPRIMER AVANT LA MISE EN PRODUCTION ⚠️
 *
 * Déclencheur MANUEL du job {@code @Scheduled} d'expiration des tenants (grâce → suspension),
 * pour la recette/QA : le job {@link TenantExpiryJob#sweep()} tourne sur cron (07:00) et n'est
 * pas déclenchable autrement, ce qui rendait le cas TEN-11 « Bloqué ». Opération système
 * (balaye tous les tenants) → réservée au super-admin.
 *
 * Garde-fou : {@code @Profile("!prod")} → ce contrôleur n'est JAMAIS enregistré quand le profil
 * Spring {@code prod} est actif. À retirer (supprimer ce fichier) avant la livraison en prod.
 */
@RestController
@Profile("!prod")
@RequestMapping("/api/v1/dev/jobs/tenants")
@RequiredArgsConstructor
@Tag(name = "DEV — Déclencheurs de jobs (TEMPORAIRE, à retirer avant prod)")
public class DevTenantExpiryTriggerController {

    private final TenantExpiryJob job;

    /** Rejoue {@code TenantExpiryJob.sweep()} (grâce/expiration des tenants, cron 0 0 7). */
    @PostMapping("/expiry-sweep")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> expirySweep() {
        job.sweep();
        return Map.of("job", "TenantExpiryJob.sweep", "status", "executed",
                "note", "Les tenants en fin de période de grâce sont suspendus ; vérifier le statut des organisations.");
    }
}
