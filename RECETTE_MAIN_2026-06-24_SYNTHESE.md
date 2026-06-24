# Synthèse — Rejeu complet de la recette sur `main` (2026-06-24)

**Verdict : 417 OK · 0 KO · 1 Bloqué · 1 N/A sur 419 cas. Les 19 cas KO du 2026-06-24 sont
tous confirmés corrigés. Aucune régression.**

> Mise à jour (suite) : sur les 9 cas Bloqué initiaux, **8 ont été débloqués** lors du suivi —
> POS-10/POS-24 (hors-ligne POS via Playwright), LOT-13/LOT-14 (FEFO câblé), LOT-18/LOT-21/TEN-11
> (jobs `@Scheduled` rendus déclenchables par des endpoints **temporaires** `@Profile("!prod")`),
> et LOT-15 (sélection manuelle de lot implémentée — voir « Suivi 3 »). Il ne reste qu'**1 Bloqué** :
> BRC-17 (garde `error.reception.already_posted` inatteignable via l'API publique).

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

## Cas Bloqué — 9 au rejeu, **1 restant** après suivi
**Débloqués depuis (8)** : AUTH-08 (super-admin sur tenant suspendu, via promotion DB), POS-10/POS-24 (hors-ligne POS via Playwright), LOT-13/LOT-14 (FEFO câblé), LOT-18/LOT-21/TEN-11 (jobs `@Scheduled` rendus déclenchables — « Suivi 2 »), LOT-15 (sélection manuelle de lot implémentée — « Suivi 3 »).

**Reste Bloqué (1) — pas un défaut** :
- **BRC-17** : le chemin `error.reception.already_posted` n'est pas atteignable via l'API publique (la garde défensive existe mais `/record` mène toujours à RECEIVED ; il faudrait forcer un état intermédiaire en base pour la déclencher).

## Livrables
- `Cahier_de_recettes_mini-ERP_execute_2026-06-24_main.xlsx` — classeur rempli (Statut/Testeur/Date/Observations sur les 419 cas, tableau de bord, feuille **Bugs & Anomalies**). La copie du 2026-06-24 reste intacte.
- `recette_captures_2026-06-24_main/` — 144 captures d'écran de preuve (par domaine D01→D10, incl. hors-ligne POS).
- Le présent fichier de synthèse.

## Approfondissement des 2 points de vigilance

### 1. `/uoms/convert` renvoie 500 sur paramètre non-UUID → CORRIGÉ
- **Cause racine** : `UomController.convert` déclare `from`/`to` en `UUID`. Un appel avec un code (« G ») lève `MethodArgumentTypeMismatchException`, non gérée par `GlobalExceptionHandler` → elle tombait dans le catch-all `Exception` → **500 `error.internal`**.
- **Correctif** (`backend/shared/.../GlobalExceptionHandler.java`) : nouveau `@ExceptionHandler(MethodArgumentTypeMismatchException)` → **400 `error.invalid_parameter`** (avec le nom du paramètre en `details`), + clé i18n fr/en/ar. Durcissement transversal : **tout** endpoint avec un paramètre UUID/typé mal formé répond désormais 400 propre au lieu de 500.
- **Vérifié live** (après rebuild conteneur) : code « G » → 400 `error.invalid_parameter` ; IDs valides G→KG → 200 (résultat 2,5) ; inter-catégorie G→L → 422 `error.uom.category_mismatch`. Comportement fonctionnel inchangé, seule la réponse d'erreur est assainie.

### 2. FEFO / consommation de lot non branchés aux flux sortants → **CONSTRUIT + vérifié**
- **Constat initial** : les lots étaient **créés** en entrée (`GoodsReceiptService.receiveLot`, stock d'ouverture) mais **jamais décrémentés** en sortie. Livraison et POS ne touchaient que le **stock total** ; le helper `selectFEFO`/`consumeAllocations` existait mais aucun flux sortant ne l'invoquait.
- **Réalisé** : nouvel hook `LotOperations.consumeFefoIfTracked(variant, warehouse, qty, refType, refId)` dans `LotService` — consomme en FEFO (péremption la plus proche d'abord) les lots ACTIVE, marque EXHAUSTED, enregistre les mouvements `SALE_OUT`. **Tolérant** (décision produit) : si les lots ne couvrent pas la quantité, consomme l'existant + log d'avertissement, sans bloquer (le stock total reste l'autorité). Hook symétrique `restoreLotsOnReturn` (mouvement `RETURN_IN`, ravive EXHAUSTED→ACTIVE). Détection « produit suivi » par **présence de lots** (un lot n'existe que pour un produit suivi) — évite toute résolution catalogue qui pourrait empoisonner la transaction appelante.
- **Câblage** : appelé après le débit de stock dans `DeliveryService.recordDelivery`, `PosService.createSale` (couvre aussi la **resync hors-ligne**, `syncSales` passant par `createSale`) ; restauration dans `PosService.voidSale` et `CreditNoteReturnEventListener` (retours/avoirs). Dépendances de module ajoutées (`delivery`→`lotexpiry::api`, `pos`→`lotexpiry::api`) + déclarations Spring Modulith mises à jour.
- **Vérifié** : suite IT backend **133/0/0** (3 nouveaux tests FEFO : ordre FEFO, tolérance au manque de lot, no-op produit non suivi) ; **E2E live** sur recette2 — vente POS de 7 sur un produit à 2 lots (A exp proche qty5, B qty10) → A 5→0 EXHAUSTED, B 10→8 (FEFO respecté), `SALE_OUT` enregistrés ; void → lots restaurés (total 15). Débloque **LOT-13** et **LOT-14**.
- **Hors périmètre (documenté)** : la **sélection manuelle** d'un lot (court-circuit FEFO, LOT-15) reste non exposée ; la restauration de retour vise le lot survivant le plus récent (pas de création d'un lot de retour à péremption devinée quand aucun lot ne survit — seulement un log).

### Suivi — hors-ligne POS (POS-10 / POS-24) rejoué
Rejoué pour de vrai via Playwright (`context.setOffline`) contre le POS (:4201) : vente saisie **hors-ligne** → file locale Dexie (`pendingSales`, statut `pending` + `idempotencyKey`), bandeau « Mode hors-ligne » + reçu de repli affichés (captures `D09/POS-10_*`). À la **reconnexion** → resynchro auto (`POST /pos/sales/sync`) → entrée Dexie `synced` (serverSaleId) et **exactement 1 vente** côté serveur (idempotence, pas de doublon). Débloque POS-10/POS-24.

**Anomalie distincte découverte → CORRIGÉE** : `/pos/sales/sync` partait en 500 (`UnexpectedRollbackException`) dès qu'une vente du lot échouait (ex. `error.pricing.no_price`) — la transaction partagée passait `rollback-only`, bloquant la synchro des autres. Fix : `PosService.syncSales` n'est plus `@Transactional` ; chaque vente passe par `self.createSale` (réf proxy `@Lazy`) donc sa propre transaction → une vente fautive ne rollback que la sienne. Test de non-régression + vérif live (batch 1 OK + 1 KO → 200 `ACCEPTED`+`ERROR`, la bonne persistée).

### Suivi 2 — jobs `@Scheduled` rendus déclenchables (LOT-18 / LOT-21 / TEN-11)
Les 3 jobs planifiés (alertes lots 06:00, marquage EXPIRED 06:30, expiration tenant 07:00) n'étaient pas déclenchables → « Bloqué ». Ajout de **déclencheurs manuels TEMPORAIRES**, à retirer avant prod :
- `POST /api/v1/dev/jobs/lots/scan-expiring` (LOT-18), `POST /api/v1/dev/jobs/lots/mark-expired` (LOT-21) — `DevExpiryTriggerController` (lot-expiry), permission `lot:read`/`lot:update`, exécutés dans le contexte tenant de l'appelant (RLS).
- `POST /api/v1/dev/jobs/tenants/expiry-sweep` (TEN-11) — `DevTenantExpiryTriggerController` (tenant), `hasRole('SUPER_ADMIN')`.

Chaque contrôleur invoque la **même** méthode que le planificateur et est **gardé par `@Profile("!prod")`** : jamais enregistré quand le profil `prod` est actif (la prod utilise `SPRING_PROFILES_ACTIVE=prod`). En-tête « ⚠️ TEMPORAIRE — À SUPPRIMER AVANT LA MISE EN PRODUCTION » + isolé dans 2 fichiers dédiés → suppression triviale avant livraison. **Vérifié live** : mark-expired → lot ACTIVE→EXPIRED ; scan-expiring → alerte `[LOT-EXPIRY]` dans les logs ; sweep → tenant PAST_DUE→SUSPENDED, et tenant-admin → 403.

### Suivi 3 — sélection manuelle de lot (LOT-15) implémentée
La consommation pouvait être automatique (FEFO) mais pas **manuelle** (choisir un lot précis) → LOT-15 « Bloqué ». Ajout :
- Nouveau champ optionnel **`lotAllocations: [{lotId, quantity}]`** sur la ligne de vente POS (`CreateSaleRequest.SaleLineRequest`, constructeur secondaire rétro-compatible → FEFO si absent).
- Nouveau SPI **`LotOperations.consumeExplicitLots(variant, warehouse, expectedQty, allocations, …)`** : **valide** (chaque lot ACTIVE du bon variant/entrepôt, quantité suffisante, somme des allocations == quantité de la ligne) puis consomme exactement les lots désignés, **court-circuitant FEFO** ; sinon **422** (`error.lot.allocation_invalid` / `_insufficient` / `_qty_mismatch`, i18n fr/en/ar).
- `PosService.createSale` : si la ligne porte des `lotAllocations` → `consumeExplicitLots`, sinon `consumeFefoIfTracked`.

**Vérifié** : 137 IT (3 nouveaux : override FEFO + 2 cas de rejet). **E2E live** : vente ciblant le lot à péremption la plus lointaine (B) → B 10→6, le lot FEFO le plus proche (A) reste intact à 5 ; allocations dont la somme ≠ la quantité de ligne → 422 `error.lot.allocation_qty_mismatch`. *(Exposé côté API ; un sélecteur de lot dans l'UI POS serait une amélioration front distincte.)* Débloque LOT-15.

### Suivi 4 — complétion de la fonctionnalité lots (vente + livraison + retours + UI)
Extension cohérente de la gestion des lots au-delà de la vente POS :
- **Livraison** — sélection manuelle de lot au moment du *record* : champ optionnel `lotAllocations` sur `LineDelivered` (clé par `lineId`) → `consumeExplicitLots` (court-circuite FEFO), sinon FEFO automatique (déjà câblé). **E2E live** : BL ciblant le lot lointain → ce lot décrémente, le lot FEFO reste intact ; BL sans allocation → lot le plus proche d'abord.
- **Retours** — `restoreLotsOnReturn(productId, …)` **crée un lot de retour** quand aucun lot ne survit (produit suivi → `baseUom` + péremption `shelfLifeDays`), au lieu de seulement logguer ; résolution produit via `catalog.findProductById` (Optional, ne jette pas → pas de transaction empoisonnée). Appelants (avoir client, void POS) passent `productId`. **IT** dédié.
- **UI POS** — sélecteur de lot dans la ligne du panier (en ligne) : « FEFO (auto) » + lots ACTIFS (n° · péremption · reste) ; le choix part en `lotAllocations`. Hors-ligne : masqué (FEFO serveur à la synchro). **Build AOT vert + capture** (sélecteur « FEFO + 2 lots », lot choisi affiché).

**Vérifié global** : **138 IT verts** (+ nouveau test création-lot-au-retour) ; build POS production vert ; E2E live livraison (manuel + FEFO). Branche `feat/lot-feature-completion`.
