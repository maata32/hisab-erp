package com.hisaberp.notifications.internal;

import com.hisaberp.notifications.api.EmailSender;
import com.hisaberp.tenant.events.OrganizationStatusChangedEvent;
import com.hisaberp.tenant.events.SubscriptionPaymentRecordedEvent;
import com.hisaberp.tenant.events.TenantRegisteredEvent;
import com.hisaberp.tenant.events.TenantTrialExpiringEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Transactional e-mails for tenant lifecycle. Listens asynchronously after commit; failures are
 * swallowed (logged) so they never roll back the originating business transaction. Messages are
 * localized to the tenant's language (fr / en / ar). Every status transition is covered by
 * {@link #onStatusChanged} (the generic {@code OrganizationStatusChangedEvent}); registration and
 * the trial-expiring reminder keep their own events (not status changes).
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
        String name = e.recipientName();
        String org = e.organizationName();
        String code = e.tenantCode();
        String subject;
        String body;
        switch (lang(e.locale())) {
            case "ar" -> {
                subject = "تم استلام تسجيلك";
                body = "مرحبًا " + name + "،\n\nتم استلام طلب تسجيل « " + org + " » (الرمز « " + code
                        + " ») وهو قيد المراجعة.\nستصلك رسالة عند الموافقة.\n\n— Hisab ERP";
            }
            case "en" -> {
                subject = "We received your registration";
                body = "Hello " + name + ",\n\nYour registration request for \"" + org + "\" (code \"" + code
                        + "\") has been received and is pending review.\nYou will get an e-mail once it is approved.\n\n— Hisab ERP";
            }
            default -> {
                subject = "Votre inscription a bien été reçue";
                body = "Bonjour " + name + ",\n\nVotre demande d'inscription pour « " + org + " » (code « " + code
                        + " ») a bien été reçue. Elle est en attente de validation.\n"
                        + "Vous recevrez un e-mail dès qu'elle sera approuvée.\n\n— Hisab ERP";
            }
        }
        send(e.recipientEmail(), subject, body, "registration");
    }

    /** One e-mail per tenant status transition (approved, activated, past-due, suspended, archived…). */
    @ApplicationModuleListener
    public void onStatusChanged(OrganizationStatusChangedEvent e) {
        String l = lang(e.locale());
        String org = e.organizationName();
        String code = e.tenantCode();
        String loginUrl = frontendUrl + "/auth/login";
        String reasonSuffix = reasonSuffix(l, e.reason());
        String subject;
        String body;
        switch (e.newStatus()) {
            case "TRIAL" -> {
                subject = switch (l) {
                    case "ar" -> "تم تفعيل حسابك في Hisab ERP";
                    case "en" -> "Your Hisab ERP account is active";
                    default -> "Votre compte Hisab ERP est activé";
                };
                body = switch (l) {
                    case "ar" -> "مرحبًا،\n\nتمت الموافقة على تسجيل « " + org + " ».\nسجّل الدخول برمز المنظمة « "
                            + code + " »:\n" + loginUrl + "\n\n— Hisab ERP";
                    case "en" -> "Hello,\n\nThe registration for \"" + org + "\" has been approved.\n"
                            + "Sign in with the organization code \"" + code + "\":\n" + loginUrl + "\n\n— Hisab ERP";
                    default -> "Bonjour,\n\nL'inscription de « " + org + " » a été approuvée.\n"
                            + "Connectez-vous avec le code organisation « " + code + " » :\n" + loginUrl + "\n\n— Hisab ERP";
                };
            }
            case "ACTIVE" -> {
                subject = switch (l) {
                    case "ar" -> "اشتراكك نشط — " + org;
                    case "en" -> "Subscription active — " + org;
                    default -> "Abonnement actif — " + org;
                };
                body = switch (l) {
                    case "ar" -> "مرحبًا،\n\nأصبح اشتراك « " + org + " » نشطًا. شكرًا لكم.\n\n— Hisab ERP";
                    case "en" -> "Hello,\n\nThe subscription for \"" + org + "\" is now active. Thank you.\n\n— Hisab ERP";
                    default -> "Bonjour,\n\nL'abonnement de « " + org + " » est désormais actif. Merci.\n\n— Hisab ERP";
                };
            }
            case "PAST_DUE" -> {
                subject = switch (l) {
                    case "ar" -> "انتهت فترة اشتراكك — " + org;
                    case "en" -> "Subscription past due — " + org;
                    default -> "Abonnement échu — " + org;
                };
                body = switch (l) {
                    case "ar" -> "مرحبًا،\n\nانتهت فترة اشتراك « " + org + " ». يرجى التجديد لتفادي تعليق الحساب.\n\n— Hisab ERP";
                    case "en" -> "Hello,\n\nThe subscription for \"" + org + "\" has lapsed. Please renew to avoid suspension.\n\n— Hisab ERP";
                    default -> "Bonjour,\n\nL'abonnement de « " + org + " » est échu. Régularisez pour éviter la suspension.\n\n— Hisab ERP";
                };
            }
            case "SUSPENDED" -> {
                subject = switch (l) {
                    case "ar" -> "تم تعليق حسابك في Hisab ERP";
                    case "en" -> "Your Hisab ERP account has been suspended";
                    default -> "Votre compte Hisab ERP a été suspendu";
                };
                body = switch (l) {
                    case "ar" -> "مرحبًا،\n\nتم تعليق الوصول إلى « " + org + " »." + reasonSuffix
                            + " اتصل بنا أو جدّد اشتراكك لإعادة التفعيل.\n\n— Hisab ERP";
                    case "en" -> "Hello,\n\nAccess to \"" + org + "\" has been suspended." + reasonSuffix
                            + " Contact us or renew your subscription to reactivate it.\n\n— Hisab ERP";
                    default -> "Bonjour,\n\nL'accès à « " + org + " » a été suspendu." + reasonSuffix
                            + " Contactez-nous ou régularisez votre abonnement pour le réactiver.\n\n— Hisab ERP";
                };
            }
            case "ARCHIVED" -> {
                boolean rejected = "PENDING".equals(e.oldStatus());
                subject = switch (l) {
                    case "ar" -> rejected ? "لم يتم قبول طلب تسجيلك" : "تمت أرشفة حسابك في Hisab ERP";
                    case "en" -> rejected ? "Your registration request was declined" : "Your Hisab ERP account was archived";
                    default -> rejected ? "Votre demande d'inscription n'a pas été retenue" : "Votre compte Hisab ERP a été archivé";
                };
                body = switch (l) {
                    case "ar" -> (rejected ? "مرحبًا،\n\nنأسف، لم يتم قبول طلب تسجيل « " + org + " »."
                            : "مرحبًا،\n\nتمت أرشفة « " + org + " ».") + reasonSuffix + "\n\n— Hisab ERP";
                    case "en" -> (rejected ? "Hello,\n\nWe are sorry — the registration request for \"" + org + "\" was declined."
                            : "Hello,\n\nThe account \"" + org + "\" has been archived.") + reasonSuffix + "\n\n— Hisab ERP";
                    default -> (rejected ? "Bonjour,\n\nNous sommes désolés, la demande d'inscription pour « " + org + " » n'a pas été retenue."
                            : "Bonjour,\n\nLe compte « " + org + " » a été archivé.") + reasonSuffix + "\n\n— Hisab ERP";
                };
            }
            default -> {
                return; // other statuses (e.g. back to PENDING) — no e-mail
            }
        }
        send(e.recipientEmail(), subject, body, "status:" + e.newStatus());
    }

    @ApplicationModuleListener
    public void onTrialExpiring(TenantTrialExpiringEvent e) {
        String org = e.organizationName();
        long days = e.daysLeft();
        String subject;
        String body;
        switch (lang(e.locale())) {
            case "ar" -> {
                subject = "تنتهي فترتك التجريبية قريبًا";
                body = "مرحبًا،\n\nتنتهي الفترة التجريبية لـ « " + org + " » خلال " + days
                        + " يوم. فعّل اشتراكًا للاستمرار دون انقطاع.\n\n— Hisab ERP";
            }
            case "en" -> {
                subject = "Your trial is ending soon";
                body = "Hello,\n\nThe trial for \"" + org + "\" ends in " + days
                        + " day(s). Activate a subscription to keep using Hisab ERP.\n\n— Hisab ERP";
            }
            default -> {
                subject = "Votre période d'essai se termine bientôt";
                body = "Bonjour,\n\nLa période d'essai de « " + org + " » se termine dans " + days
                        + " jour(s). Activez un abonnement pour continuer sans interruption.\n\n— Hisab ERP";
            }
        }
        send(e.recipientEmail(), subject, body, "trial-expiring");
    }

    @ApplicationModuleListener
    public void onPaymentRecorded(SubscriptionPaymentRecordedEvent e) {
        String l = lang(e.locale());
        String org = e.organizationName();
        String amount = e.amount().stripTrailingZeros().toPlainString() + " " + e.currency();
        String duration = formatDuration(l, e.years(), e.months());
        String start = fmtDate(e.periodStart());
        String end = fmtDate(e.periodEnd());
        String subject;
        String body;
        switch (l) {
            case "ar" -> {
                subject = "تم تسجيل الدفعة — " + org;
                body = "مرحبًا،\n\nنؤكد تسجيل دفعة اشتراككم لـ « " + org + " ».\n\n"
                        + "المبلغ: " + amount + "\n"
                        + "المدة: " + duration + "\n"
                        + "الفترة المغطّاة: من " + start + " إلى " + end + "\n\n"
                        + "شكرًا.\n— Hisab ERP";
            }
            case "en" -> {
                subject = "Payment recorded — " + org;
                body = "Hello,\n\nWe confirm your subscription payment for \"" + org + "\".\n\n"
                        + "Amount: " + amount + "\n"
                        + "Duration: " + duration + "\n"
                        + "Covered period: " + start + " to " + end + "\n\n"
                        + "Thank you.\n— Hisab ERP";
            }
            default -> {
                subject = "Paiement enregistré — " + org;
                body = "Bonjour,\n\nNous confirmons l'enregistrement de votre paiement d'abonnement pour « "
                        + org + " ».\n\n"
                        + "Montant : " + amount + "\n"
                        + "Durée : " + duration + "\n"
                        + "Période couverte : du " + start + " au " + end + "\n\n"
                        + "Merci.\n— Hisab ERP";
            }
        }
        send(e.recipientEmail(), subject, body, "payment");
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

    /** Tenant UI language: "ar" | "en" | "fr" (default). */
    private static String lang(String locale) {
        if (locale == null || locale.isBlank()) return "fr";
        String l = locale.toLowerCase();
        if (l.startsWith("ar")) return "ar";
        if (l.startsWith("en")) return "en";
        return "fr";
    }

    private static String reasonSuffix(String lang, String reason) {
        if (reason == null || reason.isBlank() || reason.startsWith("auto:")) return "";
        return switch (lang) {
            case "ar" -> "\nالسبب: " + reason;
            case "en" -> "\nReason: " + reason;
            default -> "\nMotif : " + reason;
        };
    }

    private static String formatDuration(String lang, int years, int months) {
        String y = switch (lang) { case "ar" -> " سنة"; case "en" -> " year(s)"; default -> " an(s)"; };
        String m = switch (lang) { case "ar" -> " شهر"; case "en" -> " month(s)"; default -> " mois"; };
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(y);
        if (months > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(months).append(m);
        }
        return sb.length() == 0 ? "0" + m : sb.toString();
    }

    private static String fmtDate(java.time.Instant i) {
        return i.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
    }
}
