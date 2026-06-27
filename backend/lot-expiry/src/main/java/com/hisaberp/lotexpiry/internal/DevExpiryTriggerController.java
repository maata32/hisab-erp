package com.hisaberp.lotexpiry.internal;

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
 * Déclencheurs MANUELS des jobs {@code @Scheduled} d'expiration de lots, pour la recette/QA :
 * ces jobs tournent sur cron (06:00 / 06:30) et ne sont pas déclenchables autrement, ce qui
 * rendait les cas LOT-18 / LOT-21 « Bloqué » en recette. Ils invoquent la MÊME logique que
 * le planificateur (mêmes méthodes de {@link ExpiryAlertJob}), dans le contexte tenant de
 * l'appelant (la RLS restreint donc l'effet au tenant courant).
 *
 * Garde-fou : {@code @Profile("!prod")} → ce contrôleur n'est JAMAIS enregistré quand le profil
 * Spring {@code prod} est actif. À retirer (supprimer ce fichier) avant la livraison en prod.
 */
@RestController
@Profile("!prod")
@RequestMapping("/api/v1/dev/jobs/lots")
@RequiredArgsConstructor
@Tag(name = "DEV — Déclencheurs de jobs (TEMPORAIRE, à retirer avant prod)")
public class DevExpiryTriggerController {

    private final ExpiryAlertJob job;

    /** Rejoue {@code ExpiryAlertJob.scanExpiringLots()} (alertes d'expiration, cron 0 0 6). */
    @PostMapping("/scan-expiring")
    @PreAuthorize("hasAuthority('lot:read')")
    public Map<String, Object> scanExpiring() {
        job.scanExpiringLots();
        return Map.of("job", "scanExpiringLots", "status", "executed",
                "note", "Alertes [LOT-EXPIRY] émises dans les logs serveur pour les lots proches de péremption.");
    }

    /** Rejoue {@code ExpiryAlertJob.markExpiredLots()} (passage EXPIRED, cron 0 30 6). */
    @PostMapping("/mark-expired")
    @PreAuthorize("hasAuthority('lot:update')")
    public Map<String, Object> markExpired() {
        job.markExpiredLots();
        return Map.of("job", "markExpiredLots", "status", "executed",
                "note", "Les lots ACTIVE dont la péremption est passée sont marqués EXPIRED ; vérifier via GET /api/v1/lots.");
    }
}
