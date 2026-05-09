import { Injectable } from '@angular/core';
import { PendingSale, SyncedSale } from '../models/pos.models';

interface ReceiptData {
  saleNumber: string;
  orgName: string;
  currency: string;
  receiptWidthMm: number;
  completedAt: string;
  lines: ReceiptLine[];
  subtotal: number;
  taxAmount: number;
  total: number;
  paidCash: number;
  paidCard: number;
  paidMobile: number;
  paidCredit: number;
  changeDue: number;
}

interface ReceiptLine {
  name: string;
  sku: string;
  quantity: number;
  unitPrice: number;
  total: number;
}

@Injectable({ providedIn: 'root' })
export class ReceiptService {

  buildFromSynced(sale: SyncedSale, orgName: string, widthMm: number): ReceiptData {
    return {
      saleNumber: sale.number,
      orgName,
      currency: sale.currency,
      receiptWidthMm: widthMm,
      completedAt: sale.completedAt,
      lines: sale.lines.map((l) => ({
        name: l.snapshotName,
        sku: l.snapshotSku,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        total: l.total,
      })),
      subtotal: sale.subtotal,
      taxAmount: sale.taxAmount,
      total: sale.total,
      paidCash: sale.paidCash,
      paidCard: sale.paidCard,
      paidMobile: sale.paidMobile,
      paidCredit: sale.paidCredit,
      changeDue: sale.changeDue,
    };
  }

  buildFromPending(sale: PendingSale, orgName: string, widthMm: number): ReceiptData {
    const total = sale.lines.reduce((s, l) => s + l.quantity * 0, 0);
    return {
      saleNumber: sale.serverSaleNumber ?? sale.idempotencyKey.slice(-8),
      orgName,
      currency: 'MRU',
      receiptWidthMm: widthMm,
      completedAt: sale.occurredAt,
      lines: sale.lines.map((l) => ({
        name: l.productId,
        sku: '',
        quantity: l.quantity,
        unitPrice: 0,
        total: 0,
      })),
      subtotal: total,
      taxAmount: 0,
      total,
      paidCash: sale.payment.cash ?? 0,
      paidCard: sale.payment.card ?? 0,
      paidMobile: sale.payment.mobile ?? 0,
      paidCredit: sale.payment.credit ?? 0,
      changeDue: 0,
    };
  }

  async printEscPos(data: ReceiptData): Promise<boolean> {
    try {
      if ('usb' in navigator) {
        const device = await (navigator as any).usb.requestDevice({
          filters: [{ classCode: 0x07 }],
        });
        const bytes = this.encodeEscPos(data);
        await device.open();
        if (device.configuration === null) await device.selectConfiguration(1);
        await device.claimInterface(0);
        await device.transferOut(1, bytes);
        await device.close();
        return true;
      }
    } catch {
      // fall through to HTML print
    }
    this.printHtml(data);
    return false;
  }

  printHtml(data: ReceiptData): void {
    const cols = data.receiptWidthMm >= 80 ? 48 : 32;
    const html = this.buildHtmlReceipt(data, cols);
    const win = window.open('', '_blank', 'width=400,height=600');
    if (!win) return;
    win.document.write(html);
    win.document.close();
    win.focus();
    setTimeout(() => {
      win.print();
      win.close();
    }, 500);
  }

  private buildHtmlReceipt(data: ReceiptData, cols: number): string {
    const sep = '─'.repeat(cols);
    const fmt = (n: number) => n.toFixed(2);
    const pad = (left: string, right: string, w: number) => {
      const gap = w - left.length - right.length;
      return left + ' '.repeat(Math.max(1, gap)) + right;
    };
    const date = new Date(data.completedAt).toLocaleString();

    const lineRows = data.lines
      .map((l) => {
        const desc = l.name.slice(0, cols - 12);
        const qty = `${l.quantity}x${fmt(l.unitPrice)}`;
        const tot = fmt(l.total);
        return `<div>${pad(desc, tot, cols)}</div><div style="color:#888;font-size:0.85em">&nbsp;&nbsp;${qty}</div>`;
      })
      .join('');

    return `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
  body { font-family: 'Courier New', monospace; font-size: 12px; width: ${cols}ch; margin: 0 auto; }
  h2 { text-align: center; font-size: 14px; }
  .center { text-align: center; }
  .sep { border-top: 1px dashed #000; margin: 4px 0; }
  .total { font-weight: bold; font-size: 14px; }
  @media print { body { width: 100%; } }
</style></head><body>
<h2>${data.orgName}</h2>
<div class="center">${date}</div>
<div class="center">#${data.saleNumber}</div>
<div class="sep"></div>
${lineRows}
<div class="sep"></div>
<div>${pad('Subtotal', fmt(data.subtotal) + ' ' + data.currency, cols)}</div>
${data.taxAmount > 0 ? `<div>${pad('TVA', fmt(data.taxAmount) + ' ' + data.currency, cols)}</div>` : ''}
<div class="sep"></div>
<div class="total">${pad('TOTAL', fmt(data.total) + ' ' + data.currency, cols)}</div>
<div class="sep"></div>
${data.paidCash > 0 ? `<div>${pad('Espèces', fmt(data.paidCash) + ' ' + data.currency, cols)}</div>` : ''}
${data.paidCard > 0 ? `<div>${pad('Carte', fmt(data.paidCard) + ' ' + data.currency, cols)}</div>` : ''}
${data.paidMobile > 0 ? `<div>${pad('Mobile', fmt(data.paidMobile) + ' ' + data.currency, cols)}</div>` : ''}
${data.changeDue > 0 ? `<div>${pad('Rendu', fmt(data.changeDue) + ' ' + data.currency, cols)}</div>` : ''}
<div class="sep"></div>
<div class="center">Merci pour votre achat!</div>
<br><br><br>
</body></html>`;
  }

  private encodeEscPos(data: ReceiptData): Uint8Array {
    const cols = data.receiptWidthMm >= 80 ? 48 : 32;
    const encoder = new EscPosEncoder(cols);

    const fmt = (n: number) => n.toFixed(2);
    const date = new Date(data.completedAt).toLocaleString();

    encoder
      .initialize()
      .align('center')
      .bold(true)
      .text(data.orgName)
      .feed(1)
      .bold(false)
      .text(date)
      .feed(1)
      .text('#' + data.saleNumber)
      .feed(1)
      .separator(cols)
      .align('left');

    for (const line of data.lines) {
      const desc = line.name.slice(0, cols - 10);
      const tot = fmt(line.total);
      encoder.padLine(desc, tot + ' ' + data.currency, cols).feed(1);
      encoder.text(`  ${line.quantity}x${fmt(line.unitPrice)}`).feed(1);
    }

    encoder
      .separator(cols)
      .padLine('Subtotal', fmt(data.subtotal) + ' ' + data.currency, cols)
      .feed(1);

    if (data.taxAmount > 0) {
      encoder.padLine('TVA', fmt(data.taxAmount) + ' ' + data.currency, cols).feed(1);
    }

    encoder
      .separator(cols)
      .bold(true)
      .padLine('TOTAL', fmt(data.total) + ' ' + data.currency, cols)
      .feed(1)
      .bold(false)
      .separator(cols);

    if (data.paidCash > 0) {
      encoder.padLine('Espèces', fmt(data.paidCash) + ' ' + data.currency, cols).feed(1);
    }
    if (data.paidCard > 0) {
      encoder.padLine('Carte', fmt(data.paidCard) + ' ' + data.currency, cols).feed(1);
    }
    if (data.paidMobile > 0) {
      encoder.padLine('Mobile', fmt(data.paidMobile) + ' ' + data.currency, cols).feed(1);
    }
    if (data.changeDue > 0) {
      encoder.padLine('Rendu', fmt(data.changeDue) + ' ' + data.currency, cols).feed(1);
    }

    encoder
      .separator(cols)
      .align('center')
      .text('Merci pour votre achat!')
      .feed(4)
      .cut();

    return encoder.toBytes();
  }
}

class EscPosEncoder {
  private readonly ESC = 0x1b;
  private readonly GS = 0x1d;
  private readonly LF = 0x0a;
  private readonly bytes: number[] = [];
  private readonly textEncoder = new TextEncoder();

  constructor(private readonly cols: number) {}

  initialize(): this {
    this.bytes.push(this.ESC, 0x40);
    return this;
  }

  align(a: 'left' | 'center' | 'right'): this {
    const n = a === 'left' ? 0 : a === 'center' ? 1 : 2;
    this.bytes.push(this.ESC, 0x61, n);
    return this;
  }

  bold(on: boolean): this {
    this.bytes.push(this.ESC, 0x45, on ? 1 : 0);
    return this;
  }

  text(s: string): this {
    for (const b of this.textEncoder.encode(s)) this.bytes.push(b);
    return this;
  }

  feed(lines: number): this {
    for (let i = 0; i < lines; i++) this.bytes.push(this.LF);
    return this;
  }

  separator(cols: number): this {
    return this.text('-'.repeat(cols)).feed(1);
  }

  padLine(left: string, right: string, cols: number): this {
    const gap = cols - left.length - right.length;
    return this.text(left + ' '.repeat(Math.max(1, gap)) + right);
  }

  cut(): this {
    this.bytes.push(this.GS, 0x56, 0x41, 0x00);
    return this;
  }

  toBytes(): Uint8Array {
    return new Uint8Array(this.bytes);
  }
}
