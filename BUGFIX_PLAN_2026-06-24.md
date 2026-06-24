# Plan de correction des bugs — recette du 2026-06-24

Document de passation pour une **session neuve** dédiée à la correction. Tous les bugs ci-dessous ont été **trouvés en exécution réelle** (API + navigateur) lors de la recette ; voir `Cahier_de_recettes_mini-ERP_execute_2026-06-24.xlsx` (feuille « Bugs & Anomalies ») et les captures `recette_captures_2026-06-24/`.

## Environnement de correction / vérification
- App déjà lancée : backend `:8080`, admin `:4200`, POS `:4201`, Postgres dans le conteneur `minierp-postgres` (`docker exec minierp-postgres psql -U minierp -d minierp`).
- Tenant de recette **isolé** pour re-tester : code `recette`, admin `recette@recette.local` / `Recette1234!`. Super-admin : `root@minierp.local` / `Root12345!` (login via tenant `demo`).
- Données : produits REC-001/002 (sans lot), REC-003 (lot/expiration), entrepôts MAIN/SEC, partenaires CLI/FRN/MIX-DEMO, caisse REG-01.
- Backend = Spring Modulith (Java 21, Maven multi-module). Suite IT de référence : ~130 verte. Après chaque lot de corrections : `cd backend && ./mvnw -q test` (ou ciblé par module) + recompiler les fronts si touchés.
- Convention transverse à NE PAS « corriger » : les violations `@Valid` sortent en **HTTP 422** (et non 400) avec le bon code métier — c'est intentionnel, laissé OK en recette.

---

## P0 — Sécurité (à corriger en premier)

### BUG-1 (AUTH-04) — Verrouillage de compte inopérant
- **Fichier** : `backend/identity/src/main/java/com/minierp/identity/internal/AuthService.java` (`login()` `@Transactional` l.50 ; `setLockedUntil` l.67 ; `throw BadCredentialsException` l.70).
- **Cause** : le compteur d'échecs et `lockedUntil` sont posés puis `BadCredentialsException` (RuntimeException) est levée dans la **même** transaction `@Transactional` → rollback Spring → les incréments ne sont jamais persistés. Le compte ne se verrouille jamais.
- **Fix** : persister l'échec hors de la transaction qui rollback. Ex. méthode dédiée `recordFailedLogin(userId)` en `@Transactional(propagation = REQUIRES_NEW)` appelée avant de lever l'exception, ou commit explicite du compteur. Vérifier aussi la remise à zéro du compteur au succès (l.74).
- **Vérif** : 5 logins erronés puis 1 bon → attendu **403 `auth.account_locked`** (et non 200).

### BUG-2 (SEC-02) — Fuite inter-tenant sur le catalogue
- **Fichier** : `backend/catalog/src/main/java/com/minierp/catalog/internal/ProductService.java` (`get()` l.56-57 : `products.findById(id)` sans garde de tenant ; idem `update` l.122, `addUom`/packagings l.152, images…).
- **Cause** : `findById` court-circuite le filtre tenant Hibernate → un token du tenant A lit un produit du tenant B (renvoie 200 + données). NB : `GET /users/{id}` est correct (404 inter-tenant) → s'en inspirer.
- **Fix** : garde de tenant sur tous les accès par id du module catalog (vérifier `tenantId == TenantContext` sinon `NotFoundException`), ou s'assurer que le filtre tenant s'applique au repository. **Auditer les autres modules** pour le même motif (`findById` direct exposé par id).
- **Vérif** : token `recette`, `GET /products/{id-d-un-produit-demo}` → **404**.

---

## P1 — 500 au lieu d'une erreur/contrat propre

### BUG-3 (NOTIF-02) — PUT config notification → 500
- **Fichier** : `backend/notifications/.../NotificationConfigService` (`upsertConfig`).
- **Cause probable** : l'entité est construite sans `tenantId` avant `save` (violation NOT NULL/RLS).
- **Fix** : positionner `tenantId` depuis le contexte avant `save`. **Vérif** : `PUT /notifications/config/{eventCode}` → 200.

### BUG-4 (PROD-14 + DEP-11) — Upload > 5 Mo → 500
- **Contexte** : image produit (`POST /products/{id}/images/upload`) et pièce jointe dépense (`POST /expenses/{id}/attachments`).
- **Cause** : le dépassement de taille (max 5 Mo) lève une exception non mappée → 500.
- **Fix** : capter `MaxUploadSizeExceededException` (et/ou vérifier la taille explicitement dans le service d'attachement partagé) → exception métier mappée en **422 `error.attachment.too_large`**. Centraliser dans le `@ControllerAdvice` existant. **Vérif** : upload 6 Mo → 422 `error.attachment.too_large` ; ≤ 5 Mo → 200.

### BUG-5 (WH-08) — DELETE entrepôt → 500 (attendu 405)
- **Contexte** : `DELETE /inventory/warehouses/{id}` ; `OPTIONS` montre `Allow: PATCH,OPTIONS`. La désactivation se fait via `PATCH active=false`.
- **Fix** : pas de handler DELETE → renvoyer un **405 propre** (et corriger le 500). Décider si l'UI doit exposer désactivation/édition (actuellement non).
- **Bonus même famille** : `GET /inventory/warehouses/{id}` (unitaire) renvoie 500 ; `GET /pos/registers/{id}` (unitaire) renvoie 500 — à corriger (la liste fonctionne).

### BUG-6 (DEV-11) — Annuler un devis DRAFT → 500
- **Fichier** : service Quotes (`backend/sales/...`), transition `PATCH /quotes/{id}/status?status=CANCELLED`.
- **Fix** : autoriser/gérer la transition DRAFT→CANCELLED sans 500. **Vérif** : devis DRAFT → CANCELLED → 200.

### BUG-7 (FAC-07) — PUT facture → 500 (attendu 404/405)
- **Contexte** : pas d'édition de facture (intention « lignes verrouillées »), mais `PUT /invoices/{id}` renvoie 500.
- **Fix** : renvoyer un **405** propre (ou 404). **Vérif** : `PUT /invoices/{id}` → 405.

### BUG-8 (BC-10 = PDF-10) — PDF Bon de commande → 500
- **Fichiers** : `backend/document/src/main/resources/templates/pdf/purchase-order.html` (l.82 réf. `line.quantityReceived`) ; `backend/purchase/src/main/java/com/minierp/purchase/internal/PurchaseService.java` (`buildPurchaseOrderVars`, `LineModel` ~l.739-740 sans ce champ).
- **Fix** : ajouter `quantityReceived` au `LineModel` **ou** retirer la colonne du template. **Vérif** : `GET /purchase-orders/{id}/pdf` → 200 `application/pdf`.

---

## P1 — Écrans front cassés (route inexistante côté API)

### BUG-9 (PRC-01) — Écran Tarifs
- **Fichier** : `frontend/apps/erp-admin/src/app/features/pricing/price-tier-list.page.ts:79` appelle `/api/v1/pricing/price-tiers` (n'existe pas) ; le backend expose `/api/v1/pricing/tiers`.
- **Fix** : aligner le front sur `/pricing/tiers`. **VÉRIFIER le mapping DTO** (l'UI attend p.ex. `isDefault`/`sortOrder` ; le DTO backend expose `defaultTier` et pas de `sortOrder`) → ajuster l'interface/affichage. **Vérif** : l'écran liste RETAIL/WHOLESALE/VIP.

### BUG-10 (TRF-09) — Écran Transferts de stock
- **Fichier** : `frontend/apps/erp-admin/src/app/features/inventory/stock-transfer-list.page.ts` (l.185, 198, 203, 215) appelle `/api/v1/inventory/stock-transfers...` ; le backend expose `/api/v1/inventory/transfers`.
- **Fix** : remplacer les 4 occurrences par `/inventory/transfers`. Vérifier le payload de création (l'UI envoyait `lines:[]`). **Vérif** : lister/créer/exécuter/annuler un transfert depuis l'UI.

---

## P2 — Logique métier / données

### BUG-11 (PART-22/23) — Retrait de crédit client cassé
- **Cause** : colonne `customer_credit_usages.payment_id` **NOT NULL** (migration `backend/bootstrap/src/main/resources/db/changelog/0014-customer.xml`) alors que `WithdrawCreditRequest.paymentId` est optionnel → INSERT en 409 `error.data_integrity` sur le chemin nominal (sans paymentId).
- **Fix** : **nouvelle** migration Liquibase (ne pas éditer 0014) rendant `payment_id` **nullable** ; vérifier le service de crédit partenaire (entité `CustomerCreditUsage`). **Vérif** : `POST /partners/{id}/credits/{cid}/withdraw` avec `{amount, notes}` (sans paymentId) → 200, `remaining` décrémenté.

### BUG-12 (VAR-04) — Re-génération de la matrice de variantes
- **Contexte** : 2ᵉ `PUT /products/{id}/attributes` → 409 `error.data_integrity` ; les variants retirés restent `active=true`.
- **Fix** : rendre l'opération idempotente (upsert) et **désactiver** les variants retirés au lieu de réinsérer. Lire le service d'affectation d'attributs / génération de variantes (module catalog). **Vérif** : ré-appliquer / retirer une valeur → 200, variants obsolètes `active=false`.

### BUG-13 (PRC-10) — Paliers de quantité (minQty) non persistés
- **Cause** : la clé d'upsert de `PricingService.upsert` ignore `minQty` → le 2ᵉ palier écrase le 1ᵉʳ (même variant+uom+tier+validFrom).
- **Fix** : inclure `minQty` dans la clé d'upsert. **Vérif** : 2 paliers (1→100, 10→80) coexistent ; `resolve` qty=5 → 100, qty=12 → 80.

### BUG-14 (LOT-22) — Lots BLOCKED invisibles
- **Cause** : `findForDashboard` filtre `status IN ('ACTIVE','EXPIRED')` → un lot BLOCKED disparaît de la liste « Tous » (au lieu d'un tag « Bloqué » + bouton Détruire).
- **Fix** : inclure BLOCKED (et DESTROYED/EXHAUSTED si pertinent) dans la requête de liste, aligné avec l'attente UI. **Vérif** : bloquer un lot → il reste visible avec le bon tag ; le bouton Détruire devient atteignable (débloque aussi LOT-26).

### BUG-15 (BL-05) — Plafond de livraison non appliqué côté API
- **Cause** : `POST /deliveries` + `record` acceptent une quantité **> facturée** (livré 8 sur une facture de 5).
- **Fix** : valider côté serveur que la quantité commandée/enregistrée ≤ reste à livrer de la facture (module `delivery`). **Vérif** : tenter de livrer plus que facturé → refus (422 code métier).

### Durcissement de validation côté serveur (classe d'anomalies, ex. DEV-02)
Plusieurs gardes ne sont **qu'en UI** : `POST /quotes` sans ligne → 201, sans client → 500. À traiter en passe transverse (valider au niveau API : lignes requises, client requis → 422 propre, pas 500/201). Optionnel selon priorité.

### Non-bug
- **REP-20** : pas un bug serveur — `top-products?limit=N` est correct mais le jeu de données n'a que 2 produits vendus ; nécessite plus de données pour démontrer. Aucune correction.

---

## Ordre conseillé
1. P0 sécurité (BUG-1, BUG-2) + audit du motif `findById` inter-tenant.
2. P1 « 500 » (BUG-3→8) — souvent un mapping d'exception ou un champ manquant.
3. P1 fronts (BUG-9, BUG-10) — rapides, fort impact visible.
4. P2 données/logique (BUG-11→15) + migration DB.
5. (optionnel) durcissement validation API.

Après chaque groupe : recompiler, `./mvnw test` (module concerné), et **re-jouer le cas** via l'API/Playwright sur le tenant `recette`.
