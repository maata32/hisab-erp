# Synthèse — Rejeu complet de la recette sur `main` (2026-06-24)

**Verdict : 409 OK · 0 KO · 9 Bloqué · 1 N/A sur 419 cas. Les 19 cas KO du 2026-06-24 sont
tous confirmés corrigés. Aucune régression.**

## Contexte & méthode
- **Branche testée** : `main` (correctifs P0→P2 de la recette du 2026-06-24 mergés, commit `a355ca7`). Vérifié en amont sur l'app live : `/pricing/tiers`→200 (BUG-9), `/inventory/transfers`→200 (BUG-10), route inconnue→404 (durcissement), produit cross-tenant→404 (BUG-2).
- **Tenant isolé neuf** : `recette2` (« Recette Main 0624 », MIXTE, plan ENTERPRISE, MRU/fr), provisionné par le vrai flux *inscription → approbation super-admin (`root`) → activation*, puis seedé (UoM, 3 grilles tarifaires, 2 entrepôts, client + fournisseur, 5 produits dont un périssable, stock d'ouverture).
- **Exécution** : API réelle (Node `fetch`) + visuel **Playwright** (login UI réel, ~140 captures). Un agent par domaine a rejoué chaque cas contre l'app live (backend :8080, admin :4200, POS :4201).
- **Incident & remédiation** : la limite d'usage de session a interrompu 9 des 10 agents en cours de route. Les scripts de test qu'ils avaient écrits (et les données + captures qu'ils avaient produites) étaient persistés sur disque : ils ont été **réexécutés directement** (sans agent) pour récolter les résultats, puis chaque cas KO a été **revérifié manuellement** contre l'API live.

## Les 19 cas KO du 2026-06-24 → tous OK
| Cas | Bug | Vérif sur main |
|---|---|---|
| AUTH-04 | verrouillage compte inopérant | 5 échecs → 403 `auth.account_locked`, persisté (user jetable) |
| SEC-02 | fuite inter-tenant `GET /products/{id}` | produit `demo` lu depuis `recette2` → 404 |
| NOTIF-02 | PUT notif config → 500 | jsonb→text (migr. 0079), upsert enabled=false OK |
| PROD-14 / DEP-10 / DEP-11 | upload >5 Mo → 500 | 422 `error.attachment.too_large`, 2 Mo accepté |
| WH-08 | DELETE entrepôt → 500 | 405 `error.method_not_allowed` |
| DEV-11 | annuler devis DRAFT → 500 | enum CANCELLED, transition OK |
| BC-10 / PDF-10 | PDF bon de commande → 500 | template corrigé, PDF 200 application/pdf |
| PRC-01 | écran Tarifs `/price-tiers` cassé | `/pricing/tiers` → 200, 3 grilles |
| TRF-09 | écran Transferts cassé | front → `/inventory/transfers` (200) ; ancienne route → 404 |
| PART-22 / PART-23 | retrait crédit sans paiement → 409 | `payment_id` nullable (migr. 0080), retrait 200 |
| VAR-04 | régénération variantes → 409 | 2e PUT attributes → 200, variants désactivés (non supprimés) |
| PRC-10 | paliers minQty non persistés | clé unique min_qty (migr. 0081), 2 paliers coexistent |
| LOT-22 | lots BLOCKED invisibles | filtre statut retiré, lot bloqué reste listé |
| BL-05 | sur-livraison non plafonnée | livrer > facturé → 422 `error.delivery.exceeds_invoiced` |
| REP-20 | top-products (couverture données) | endpoint OK avec données suffisantes |

## Aucun KO réel à ce rejeu — 13 « KO » des scripts étaient des faux positifs
Les scripts d'agents (interrompus, non revérifiés par l'étape adversariale) ont marqué 13 cas KO. **Revérification manuelle live : tous faux-KO** (erreurs de harnais, pas de bugs) :
- **PROD-06** : (dé)activation via `PATCH /products/{id}` (le script utilisait `DELETE`, non mappé). Conforme.
- **PROD-07** : recherche via `?q=` (le script utilisait `?search=`, ignoré). `q=Savon` → filtré, conforme.
- **UOM-12 / UOM-13** : `/uoms/convert` attend des **UUID** (le script passait les codes « G »/« KG »). Avec IDs : 2500 G→KG = 2,5 ; inter-catégories → 422 `category_mismatch`. *(NB hygiène : un code non-UUID renvoie 500 au lieu de 400 — mineur, à durcir.)*
- **BL-06** : comportement **conforme** (BL enregistrable malgré stock insuffisant, confirmation → 422). Le script l'a mal jugé.
- **PDF-03** : permission OK — sans jeton 401, STOCK_KEEPER 403, admin 200 PDF.
- **PDF-07 / PDF-10 / PDF-11 / PDF-12 / PDF-13** : cascade d'un setup achat invalide (lignes sans `unitCost` → 422). Recréés correctement → tous les PDF (reçu paiement, BC, facture/réception/avoir fournisseur) **200 application/pdf**.
- **VAR-04** : non seulement plus de 409, mais comportement exact attendu (4 variants conservés, 2 désactivés, 2 actifs).

## 9 cas Bloqué (légitimes — limites d'infrastructure, pas des défauts)
- **Jobs `@Scheduled` non déclenchables sans accès code/actuator** : LOT-18 (alertes expiration 06:00), LOT-21 (marquage EXPIRED 06:30), TEN-11 (expiration tenant 07:00). Logique couverte par la suite IT backend (130 verts).
- **FEFO / consommation de lot non branchés aux flux sortants** (ventes/POS/livraison) : LOT-13, LOT-14, LOT-15. Le helper `/lots/select-fefo` fonctionne isolément ; aucun endpoint de consommation exposé. *(Écart d'architecture connu, pas une régression.)*
- **Hors-ligne POS** (file Dexie, coupure réseau) non simulable hors navigateur instrumenté : POS-10, POS-24. La garantie serveur (idempotence + `/pos/sales/sync`) est vérifiée (POS-11/12).
- **BRC-17** : le chemin `error.reception.already_posted` n'est pas atteignable via l'API publique (la garde existe mais `/record` mène toujours à RECEIVED).

**AUTH-08** (Bloqué au 2026-06-24) a été **débloqué** ce rejeu : sur un tenant SUSPENDED, un admin standard → 403 `auth.tenant_suspended`, mais le même user promu super-admin → 200 (contournement super-admin confirmé).

## Livrables
- `Cahier_de_recettes_mini-ERP_execute_2026-06-24_main.xlsx` — classeur rempli (Statut/Testeur/Date/Observations sur les 419 cas, tableau de bord, feuille **Bugs & Anomalies**). La copie du 2026-06-24 reste intacte.
- `recette_captures_2026-06-24_main/` — 140 captures d'écran de preuve (par domaine D01→D10).
- Le présent fichier de synthèse.

## Approfondissement des 2 points de vigilance

### 1. `/uoms/convert` renvoie 500 sur paramètre non-UUID → CORRIGÉ
- **Cause racine** : `UomController.convert` déclare `from`/`to` en `UUID`. Un appel avec un code (« G ») lève `MethodArgumentTypeMismatchException`, non gérée par `GlobalExceptionHandler` → elle tombait dans le catch-all `Exception` → **500 `error.internal`**.
- **Correctif** (`backend/shared/.../GlobalExceptionHandler.java`) : nouveau `@ExceptionHandler(MethodArgumentTypeMismatchException)` → **400 `error.invalid_parameter`** (avec le nom du paramètre en `details`), + clé i18n fr/en/ar. Durcissement transversal : **tout** endpoint avec un paramètre UUID/typé mal formé répond désormais 400 propre au lieu de 500.
- **Vérifié live** (après rebuild conteneur) : code « G » → 400 `error.invalid_parameter` ; IDs valides G→KG → 200 (résultat 2,5) ; inter-catégorie G→L → 422 `error.uom.category_mismatch`. Comportement fonctionnel inchangé, seule la réponse d'erreur est assainie.

### 2. FEFO / consommation de lot non branchés aux flux sortants → DIAGNOSTIC (non corrigé)
- **Constat précis** : les lots sont **créés** en entrée (`GoodsReceiptService.receiveLot` à la réception d'achat, et au stock d'ouverture) mais **jamais décrémentés** en sortie. La livraison (`DeliveryService` → `stockOps.issue`) et le POS (`PosService` → `stockOps.issueAllowNegative`) ne touchent que le **stock total** ; `StockOperationsImpl` n'a aucune logique de lot (le module inventory ne dépend même pas de lot-expiry). Le helper `LotService.selectFEFO` + `consumeAllocations` existe et fonctionne (validé en isolé, `/lots/select-fefo` → 200) mais **aucun flux sortant ne l'invoque** ; `LotOperations` n'est injecté que dans `GoodsReceiptService` (entrée).
- **Conséquence** : pour un produit à lots/expiration, les quantités par lot ne font que croître ; le stock total reste la seule source de vérité à la vente. Pas une régression (état d'architecture présent depuis l'origine), mais un écart fonctionnel réel (FEFO non opérant de bout en bout). Cohérent avec les cas LOT-13/14/15 restés Bloqué.
- **Recommandation** (changement conséquent, non réalisé ici car il touche le cœur vente/livraison/POS + retours + idempotence POS et mérite une décision produit) : injecter `LotOperations` dans `DeliveryService` et `PosService` ; pour les variantes `tracksLots`/`trackExpiry`, appeler `selectFEFO(variant, warehouse, qty)` puis `consumeAllocations(...)` au moment du débit de stock, dans la même transaction ; gérer lots BLOCKED exclus, stock lot insuffisant, et la restauration de lot sur retour/avoir. À cadrer comme une évolution dédiée (avec ITs de bout en bout) plutôt qu'un correctif de recette.
