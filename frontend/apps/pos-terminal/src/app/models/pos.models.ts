export interface CachedProductImage {
  id: string;
  url: string;
}

export interface CachedProduct {
  id: string;
  variantId: string;
  sku: string;
  barcode: string | null;
  name: string;
  baseUomId: string;
  defaultTaxRate: number;
  sellable: boolean;
  tracksLots: boolean;
  imageUrl: string | null;
  images: CachedProductImage[];
  price: number;
  priceTierId: string | null;
  currency: string;
  taxInclusive: boolean;
  cachedAt: number;
}

export interface CachedPriceTier {
  id: string;
  code: string;
  name: string;
  isDefault: boolean;
}

export interface CashRegister {
  id: string;
  code: string;
  name: string;
  warehouseId: string;
  defaultPriceTierId: string | null;
  receiptWidthMm: number;
  active: boolean;
}

export interface CashSession {
  id: string;
  registerId: string;
  cashierUserId: string;
  status: 'OPEN' | 'CLOSED' | 'VALIDATED';
  openedAt: string;
  closedAt: string | null;
  validatedAt?: string | null;
  validatedBy?: string | null;
  openingFloat: number;
  expectedClosing: number;
  countedClosing: number | null;
  difference: number | null;
  totalSales: number;
  totalCashIn: number;
  totalCashOut: number;
  note: string | null;
}

export interface CartLine {
  productId: string;
  variantId: string;
  productName: string;
  productSku: string;
  uomId: string;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  taxRate: number;
  taxInclusive: boolean;
  currency: string;
  /** Lot tracking: when true a specific lot may be picked (online); null = automatic FEFO. */
  tracksLots: boolean;
  lotId: string | null;
  lotNumber: string | null;
}

export interface PendingSaleLine {
  variantId: string;
  uomId: string;
  quantity: number;
  unitDiscount: number | null;
  /** Manual lot selection (overrides FEFO server-side); null/absent = automatic FEFO. */
  lotAllocations?: { lotId: string; quantity: number }[] | null;
}

export interface PendingSalePayment {
  cash: number | null;
  card: number | null;
  mobile: number | null;
  credit: number | null;
}

export interface PendingSale {
  localId?: number;
  idempotencyKey: string;
  registerId: string;
  sessionId: string;
  customerId: string | null;
  priceTierId: string | null;
  occurredAt: string;
  note: string | null;
  lines: PendingSaleLine[];
  payment: PendingSalePayment;
  status: 'pending' | 'synced' | 'failed';
  syncError: string | null;
  createdAt: number;
  serverSaleId: string | null;
  serverSaleNumber: string | null;
}

export interface SyncedSale {
  id: string;
  number: string;
  idempotencyKey: string;
  registerId: string;
  sessionId: string;
  status: string;
  currency: string;
  subtotal: number;
  taxAmount: number;
  discountAmount: number;
  total: number;
  paidCash: number;
  paidCard: number;
  paidMobile: number;
  paidCredit: number;
  changeDue: number;
  completedAt: string;
  note: string | null;
  originalSaleId?: string | null;
  voidedAt?: string | null;
  voidReason?: string | null;
  lines: SyncedSaleLine[];
}

export interface SyncedSaleLine {
  id: string;
  lineNumber: number;
  productId: string;
  uomId: string;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  taxRate: number;
  subtotal: number;
  taxAmount: number;
  total: number;
  snapshotName: string;
  snapshotSku: string;
}
