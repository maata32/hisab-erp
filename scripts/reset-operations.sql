-- =============================================================================
-- reset-operations.sql
-- Vide toutes les donnees operationnelles (ventes, achats, livraisons, stock,
-- paiements, depenses, tresorerie, notifications, audit, evenements modulith,
-- balances derivees) pour repartir d'un etat propre en dev/test manuel.
--
-- CONSERVE le referentiel : parties (partners), produits, depots, caisses,
-- coffres, comptes bancaires, categories, UoM, identite/tenants/roles, configs
-- de notifications.
--
-- Usage :
--   psql -h localhost -U minierp -d minierp -f scripts/reset-operations.sql
--
-- A executer avec le role proprietaire des tables (minierp) afin de pouvoir
-- TRUNCATE meme avec RLS active.
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- Etat AVANT (sanity check : le referentiel doit etre non vide apres execution)
SELECT 'AVANT' AS phase,
       (SELECT count(*) FROM parties)    AS parties,
       (SELECT count(*) FROM products)   AS products,
       (SELECT count(*) FROM warehouses) AS warehouses,
       (SELECT count(*) FROM sales)      AS sales,
       (SELECT count(*) FROM invoices)   AS invoices,
       (SELECT count(*) FROM payments)   AS payments;

TRUNCATE TABLE
    -- Ventes / facturation
    sale_lines,
    sales,
    invoice_lines,
    invoices,
    credit_note_lines,
    credit_notes,
    quote_lines,
    quotes,
    sale_number_sequences,
    document_number_sequences,

    -- Paiements & avoirs client
    payment_allocations,
    payments,
    customer_credit_usages,
    customer_credits,

    -- Allocations unifiees (AllocationEngine — changeset 0051)
    allocations,

    -- Livraisons
    delivery_lines,
    deliveries,

    -- Achats
    purchase_invoice_lines,
    purchase_invoices,
    purchase_order_lines,
    purchase_orders,

    -- Stock / mouvements / transferts / comptages
    stock_transfer_lines,
    stock_transfers,
    inventory_count_lines,
    inventory_counts,
    stock_movements,
    stocks,

    -- Lots & expirations (operationnel)
    lot_movements,
    expired_lot_destructions,
    product_lots,

    -- Depenses & revenus divers
    expenses,
    incomes,

    -- Tresorerie / POS sessions (configs gardees : cash_registers, vaults, bank_accounts)
    cash_movements,
    cash_sessions,
    vault_movements,
    bank_transactions,

    -- Notifications & audit
    notification_logs,
    audit_log,

    -- Modulith events
    event_publication,

    -- Balances derivees
    ar_balances,
    ap_balances
RESTART IDENTITY CASCADE;

-- Etat APRES (toutes les tables operationnelles ci-dessus doivent etre a 0)
SELECT 'APRES' AS phase,
       (SELECT count(*) FROM parties)    AS parties,
       (SELECT count(*) FROM products)   AS products,
       (SELECT count(*) FROM warehouses) AS warehouses,
       (SELECT count(*) FROM sales)      AS sales,
       (SELECT count(*) FROM invoices)   AS invoices,
       (SELECT count(*) FROM payments)   AS payments;

COMMIT;
