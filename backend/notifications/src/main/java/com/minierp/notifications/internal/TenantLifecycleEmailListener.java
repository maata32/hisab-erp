package com.minierp.notifications.internal;

import com.minierp.notifications.api.EmailSender;
import com.minierp.tenant.events.TenantApprovedEvent;
import com.minierp.tenant.events.TenantRegisteredEvent;
import com.minierp.tenant.events.SubscriptionPaymentRecordedEvent;
import com.minierp.tenant.events.TenantRejectedEvent;
import com.minierp.tenant.events.TenantSuspendedEvent;
import com.minierp.tenant.events.TenantTrialExpiringEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends transactional e-mails for tenant lifecycle events. Listens asynchronously
 * after commit; e-mail failures are swallowed (logged) so they never roll back or
 * retry the originating business transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantLifecycleEmailListener {

    private final EmailSender email;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @ApplicationModuleListener
    public void onRegistered(TenantRegisteredEvent e) {
        boolean fr = isFr(e.locale());
        String subject = fr ? "Votre inscription a bien été reçue"
                            : "We received your registration";
        String body = fr
                ? "Bonjour " + e.recipientName() + ",\n\n"
                    + "Votre demande d'inscription pour « " + e.organizationName() + " » (code « " + e.tenantCode()
                    + " ») a bien été reçue. Elle est en attente de validation par notre équipe.\n"
                    + "Vous recevrez un e-mail dès qu'elle sera approuvée.\n\n— Mini-ERP"
                : "Hello " + e.recipientName() + ",\n\n"
                    + "Your registration request for \"" + e.organizationName() + "\" (code \"" + e.tenantCode()
                    + "\") has been received and is pending review.\n"
                    + "You will get an e-mail once it is approved.\n\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "registration");
    }

    @ApplicationModuleListener
    public void onApproved(TenantApprovedEvent e) {
        boolean fr = isFr(e.locale());
        String loginUrl = frontendUrl + "/auth/login";
        String subject = fr ? "Votre compte Mini-ERP est activé"
                            : "Your Mini-ERP account is active";
        String body = fr
                ? "Bonjour " + e.recipientName() + ",\n\n"
                    + "Bonne nouvelle ! L'inscription de « " + e.organizationName() + " » a été approuvée.\n"
                    + "Vous pouvez vous connecter avec le code organisation « " + e.tenantCode() + " » :\n"
                    + loginUrl + "\n\n— Mini-ERP"
                : "Hello " + e.recipientName() + ",\n\n"
                    + "Good news! The registration for \"" + e.organizationName() + "\" has been approved.\n"
                    + "You can sign in with the organization code \"" + e.tenantCode() + "\":\n"
                    + loginUrl + "\n\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "approval");
    }

    @ApplicationModuleListener
    public void onRejected(TenantRejectedEvent e) {
        boolean fr = isFr(e.locale());
        String reason = e.reason() == null || e.reason().isBlank()
                ? (fr ? "(non précisée)" : "(not specified)") : e.reason();
        String subject = fr ? "Votre demande d'inscription n'a pas été retenue"
                            : "Your registration request was declined";
        String body = fr
                ? "Bonjour " + e.recipientName() + ",\n\n"
                    + "Nous sommes désolés, votre demande d'inscription pour « " + e.organizationName()
                    + " » n'a pas été retenue.\nMotif : " + reason + "\n\n— Mini-ERP"
                : "Hello " + e.recipientName() + ",\n\n"
                    + "We are sorry — your registration request for \"" + e.organizationName()
                    + "\" was declined.\nReason: " + reason + "\n\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "rejection");
    }

    @ApplicationModuleListener
    public void onTrialExpiring(TenantTrialExpiringEvent e) {
        boolean fr = isFr(e.locale());
        String subject = fr ? "Votre période d'essai se termine bientôt"
                            : "Your trial is ending soon";
        String body = fr
                ? "Bonjour,\n\nLa période d'essai de « " + e.organizationName() + " » se termine dans "
                    + e.daysLeft() + " jour(s). Activez un abonnement pour continuer sans interruption.\n\n— Mini-ERP"
                : "Hello,\n\nThe trial for \"" + e.organizationName() + "\" ends in "
                    + e.daysLeft() + " day(s). Activate a subscription to keep using Mini-ERP.\n\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "trial-expiring");
    }

    @ApplicationModuleListener
    public void onSuspended(TenantSuspendedEvent e) {
        boolean fr = isFr(e.locale());
        String subject = fr ? "Votre compte Mini-ERP a été suspendu"
                            : "Your Mini-ERP account has been suspended";
        String body = fr
                ? "Bonjour,\n\nL'accès à « " + e.organizationName() + " » a été suspendu. "
                    + "Contactez-nous ou régularisez votre abonnement pour le réactiver.\n\n— Mini-ERP"
                : "Hello,\n\nAccess to \"" + e.organizationName() + "\" has been suspended. "
                    + "Contact us or renew your subscription to reactivate it.\n\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "suspension");
    }

    @ApplicationModuleListener
    public void onPaymentRecorded(SubscriptionPaymentRecordedEvent e) {
        boolean fr = isFr(e.locale());
        String amount = e.amount().stripTrailingZeros().toPlainString() + " " + e.currency();
        String duration = formatDuration(fr, e.years(), e.months());
        String start = fmtDate(e.periodStart());
        String end = fmtDate(e.periodEnd());
        String subject = fr ? "Paiement enregistré — " + e.organizationName()
                            : "Payment recorded — " + e.organizationName();
        String body = fr
                ? "Bonjour,\n\nNous confirmons l'enregistrement de votre paiement d'abonnement pour « "
                    + e.organizationName() + " ».\n\n"
                    + "Montant : " + amount + "\n"
                    + "Durée : " + duration + "\n"
                    + "Période couverte : du " + start + " au " + end + "\n\n"
                    + "Merci.\n— Mini-ERP"
                : "Hello,\n\nWe confirm your subscription payment for \"" + e.organizationName() + "\".\n\n"
                    + "Amount: " + amount + "\n"
                    + "Duration: " + duration + "\n"
                    + "Covered period: " + start + " to " + end + "\n\n"
                    + "Thank you.\n— Mini-ERP";
        send(e.recipientEmail(), subject, body, "payment");
    }

    private static String formatDuration(boolean fr, int years, int months) {
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(fr ? " an(s)" : " year(s)");
        if (months > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(months).append(fr ? " mois" : " month(s)");
        }
        return sb.length() == 0 ? (fr ? "0 mois" : "0 months") : sb.toString();
    }

    private static String fmtDate(java.time.Instant i) {
        return i.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
    }

    private void send(String to, String subject, String body, String kind) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping tenant {} e-mail — no recipient address", kind);
            return;
        }
        try {
            email.sendText(to, subject, body);
        } catch (RuntimeException ex) {
            log.warn("Failed to send tenant {} e-mail to {}: {}", kind, to, ex.getMessage());
        }
    }

    private static boolean isFr(String locale) {
        return locale == null || locale.isBlank() || locale.toLowerCase().startsWith("fr");
    }
}
