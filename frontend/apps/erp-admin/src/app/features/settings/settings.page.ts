import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputSwitchModule } from 'primeng/inputswitch';
import { InputNumberModule } from 'primeng/inputnumber';
import { DropdownModule } from 'primeng/dropdown';
import { TabViewModule } from 'primeng/tabview';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { AUTH_SERVICE } from '@hisaberp/shared-auth';

interface OrgProfile {
  id: string;
  name: string;
  email: string;
  phone: string;
  address: string;
  locale: string;
  timezone: string;
  currency: string;
  code: string;
}

interface PosSettings {
  thermalReceiptWidth: number;
  allowDiscounts: boolean;
  maxDiscountPercent: number;
  defaultPaymentMethod: string;
  requireCustomerForCredit: boolean;
  currencyDecimalPlaces: number;
}

interface InvoiceSettings {
  taxEnabled: boolean;
  defaultTaxRate: number;
  pricesIncludeTax: boolean;
  numberPrefix: string;
  paperSize: 'A4' | 'A5';
}

interface PaymentSettings {
  creditLimitBehavior: string;
  defaultPaymentTermsDays: number;
  allowOverpayment: boolean;
}

@Component({
  selector: 'erp-admin-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    ButtonModule,
    InputTextModule,
    InputSwitchModule,
    InputNumberModule,
    DropdownModule,
    TabViewModule,
    ToastModule,
  ],
  providers: [MessageService],
  template: `
    <p-toast position="top-center" />

    <header class="mb-6">
      <h1 class="text-2xl font-bold text-gray-800">{{ 'settings.title' | translate }}</h1>
      <p class="text-gray-500 text-sm mt-1">{{ 'settings.subtitle' | translate }}</p>
    </header>

    @if (loading()) {
      <div class="flex items-center justify-center py-16">
        <i class="pi pi-spin pi-spinner text-4xl text-primary-600"></i>
      </div>
    } @else {
      <p-tabView>

        <!-- Organization tab -->
        <p-tabPanel [header]="'settings.tab.org' | translate">
          <div class="max-w-xl space-y-4 pt-4">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.code' | translate }}</label>
                <input pInputText [value]="org.code" disabled class="w-full bg-gray-50" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.currency' | translate }}</label>
                <input pInputText [value]="org.currency" disabled class="w-full bg-gray-50" />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.name' | translate }}</label>
                <input pInputText [(ngModel)]="org.name" class="w-full" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.email' | translate }}</label>
                <input pInputText [(ngModel)]="org.email" type="email" class="w-full" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.phone' | translate }}</label>
                <input pInputText [(ngModel)]="org.phone" class="w-full" />
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.address' | translate }}</label>
                <input pInputText [(ngModel)]="org.address" class="w-full" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.locale' | translate }}</label>
                <p-dropdown
                  [(ngModel)]="org.locale"
                  [options]="localeOptions"
                  optionLabel="label"
                  optionValue="value"
                  styleClass="w-full"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.org.timezone' | translate }}</label>
                <p-dropdown
                  [(ngModel)]="org.timezone"
                  [options]="timezoneOptions"
                  optionLabel="label"
                  optionValue="value"
                  styleClass="w-full"
                />
              </div>
            </div>
            <div class="pt-4 mt-2 border-t border-gray-200">
              <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                class="w-full p-button-lg settings-save-btn"
                [loading]="saving()" (click)="saveOrg()"></button>
            </div>
          </div>
        </p-tabPanel>

        <!-- POS Settings tab -->
        <p-tabPanel [header]="'settings.tab.pos' | translate">
          <div class="max-w-xl space-y-5 pt-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.pos.receipt_width' | translate }}</label>
              <p-dropdown
                [(ngModel)]="posSettings.thermalReceiptWidth"
                [options]="receiptWidthOptions"
                optionLabel="label"
                optionValue="value"
                styleClass="w-full sm:w-48"
              />
              <p class="text-xs text-gray-400 mt-1">{{ 'settings.pos.receipt_width_hint' | translate }}</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.pos.default_payment' | translate }}</label>
              <p-dropdown
                [(ngModel)]="posSettings.defaultPaymentMethod"
                [options]="paymentMethodOptions"
                optionLabel="label"
                optionValue="value"
                styleClass="w-full sm:w-48"
              />
            </div>
            <div class="flex items-center gap-3">
              <p-inputSwitch [(ngModel)]="posSettings.allowDiscounts" />
              <label class="text-sm font-medium text-gray-700">{{ 'settings.pos.allow_discounts' | translate }}</label>
            </div>
            @if (posSettings.allowDiscounts) {
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.pos.max_discount' | translate }}</label>
                <p-inputNumber
                  [(ngModel)]="posSettings.maxDiscountPercent"
                  [min]="0" [max]="100" suffix="%"
                  styleClass="w-32"
                />
              </div>
            }
            <div class="flex items-center gap-3">
              <p-inputSwitch [(ngModel)]="posSettings.requireCustomerForCredit" />
              <label class="text-sm font-medium text-gray-700">{{ 'settings.pos.require_customer_credit' | translate }}</label>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.pos.currency_decimals' | translate }}</label>
              <p-inputNumber
                [(ngModel)]="posSettings.currencyDecimalPlaces"
                [min]="0" [max]="4" [showButtons]="true"
                styleClass="w-32"
              />
              <p class="text-xs text-gray-400 mt-1">{{ 'settings.pos.currency_decimals_hint' | translate }}</p>
            </div>
            <div class="pt-4 mt-2 border-t border-gray-200">
              <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                class="w-full p-button-lg settings-save-btn"
                [loading]="saving()" (click)="saveSettings()"></button>
            </div>
          </div>
        </p-tabPanel>

        <!-- Invoice Settings tab -->
        <p-tabPanel [header]="'settings.tab.invoice' | translate">
          <div class="max-w-xl space-y-5 pt-4">
            <div class="flex items-center gap-3">
              <p-inputSwitch [(ngModel)]="invoiceSettings.taxEnabled" />
              <label class="text-sm font-medium text-gray-700">{{ 'settings.invoice.tax_enabled' | translate }}</label>
            </div>
            @if (invoiceSettings.taxEnabled) {
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.invoice.tax_rate' | translate }}</label>
                <p-inputNumber
                  [(ngModel)]="invoiceSettings.defaultTaxRate"
                  [min]="0" [max]="1" [minFractionDigits]="2" [maxFractionDigits]="4"
                  prefix=""
                  styleClass="w-32"
                />
                <p class="text-xs text-gray-400 mt-1">{{ 'settings.invoice.tax_rate_hint' | translate }}</p>
              </div>
              <div class="flex items-center gap-3">
                <p-inputSwitch [(ngModel)]="invoiceSettings.pricesIncludeTax" />
                <label class="text-sm font-medium text-gray-700">{{ 'settings.invoice.prices_include_tax' | translate }}</label>
              </div>
            }
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.invoice.number_prefix' | translate }}</label>
              <input pInputText [(ngModel)]="invoiceSettings.numberPrefix" class="w-32" maxlength="10" />
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.invoice.paper_size' | translate }}</label>
              <p-dropdown [(ngModel)]="invoiceSettings.paperSize" [options]="paperSizeOptions"
                          optionLabel="label" optionValue="value" styleClass="w-64" />
              <span class="text-xs text-gray-500 ms-2 block mt-1">{{ 'settings.invoice.paper_size_hint' | translate }}</span>
            </div>
            <div class="pt-4 mt-2 border-t border-gray-200">
              <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                class="w-full p-button-lg settings-save-btn"
                [loading]="saving()" (click)="saveSettings()"></button>
            </div>
          </div>
        </p-tabPanel>

        <!-- Payment Settings tab -->
        <p-tabPanel [header]="'settings.tab.payment' | translate">
          <div class="max-w-xl space-y-5 pt-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.payment.credit_limit_behavior' | translate }}</label>
              <p-dropdown
                [(ngModel)]="paymentSettings.creditLimitBehavior"
                [options]="creditLimitOptions"
                optionLabel="label"
                optionValue="value"
                styleClass="w-full sm:w-48"
              />
              <p class="text-xs text-gray-400 mt-1">{{ 'settings.payment.credit_limit_hint' | translate }}</p>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">{{ 'settings.payment.terms_days' | translate }}</label>
              <p-inputNumber
                [(ngModel)]="paymentSettings.defaultPaymentTermsDays"
                [min]="0" [max]="365" suffix=" j"
                styleClass="w-32"
              />
            </div>
            <div class="flex items-center gap-3">
              <p-inputSwitch [(ngModel)]="paymentSettings.allowOverpayment" />
              <label class="text-sm font-medium text-gray-700">{{ 'settings.payment.allow_overpayment' | translate }}</label>
            </div>
            <div class="pt-4 mt-2 border-t border-gray-200">
              <button pButton [label]="'common.save' | translate" icon="pi pi-check"
                class="w-full p-button-lg settings-save-btn"
                [loading]="saving()" (click)="saveSettings()"></button>
            </div>
          </div>
        </p-tabPanel>

      </p-tabView>
    }
  `,
})
export class SettingsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly msg = inject(MessageService);
  private readonly i18n = inject(TranslateService);
  private readonly auth = inject(AUTH_SERVICE);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);

  protected org: OrgProfile = {
    id: '', name: '', email: '', phone: '', address: '',
    locale: 'fr', timezone: 'Africa/Nouakchott', currency: 'MRU', code: '',
  };

  protected posSettings: PosSettings = {
    thermalReceiptWidth: 80,
    allowDiscounts: true,
    maxDiscountPercent: 30,
    defaultPaymentMethod: 'CASH',
    requireCustomerForCredit: true,
    currencyDecimalPlaces: 0,
  };

  protected invoiceSettings: InvoiceSettings = {
    taxEnabled: true,
    defaultTaxRate: 0.16,
    pricesIncludeTax: false,
    numberPrefix: 'FAC',
    paperSize: 'A4',
  };

  protected readonly paperSizeOptions = [
    { label: 'A4 (210 × 297 mm)', value: 'A4' },
    { label: 'A5 (148 × 210 mm)', value: 'A5' },
  ];

  protected paymentSettings: PaymentSettings = {
    creditLimitBehavior: 'WARN',
    defaultPaymentTermsDays: 30,
    allowOverpayment: true,
  };

  protected readonly localeOptions = [
    { label: 'Français', value: 'fr' },
    { label: 'English', value: 'en' },
    { label: 'العربية', value: 'ar' },
  ];

  protected readonly timezoneOptions = [
    { label: 'Africa/Nouakchott (GMT+0)', value: 'Africa/Nouakchott' },
    { label: 'Africa/Casablanca (GMT+1)', value: 'Africa/Casablanca' },
    { label: 'Europe/Paris (GMT+1/+2)', value: 'Europe/Paris' },
    { label: 'UTC', value: 'UTC' },
  ];

  protected readonly receiptWidthOptions = [
    { label: '58 mm (32 colonnes)', value: 58 },
    { label: '80 mm (48 colonnes)', value: 80 },
  ];

  protected get paymentMethodOptions() {
    return [
      { label: this.i18n.instant('payments.methods.CASH'), value: 'CASH' },
      { label: this.i18n.instant('payments.methods.CARD'), value: 'CARD' },
      { label: this.i18n.instant('payments.methods.MOBILE_MONEY'), value: 'MOBILE_MONEY' },
      { label: this.i18n.instant('payments.methods.BANK_TRANSFER'), value: 'BANK_TRANSFER' },
    ];
  }

  protected get creditLimitOptions() {
    return [
      { label: this.i18n.instant('settings.creditLimitAction.WARN'), value: 'WARN' },
      { label: this.i18n.instant('settings.creditLimitAction.BLOCK'), value: 'BLOCK' },
      { label: this.i18n.instant('settings.creditLimitAction.OFF'), value: 'OFF' },
    ];
  }

  async ngOnInit(): Promise<void> {
    await Promise.all([this.loadOrg(), this.loadSettings()]);
    this.loading.set(false);
  }

  private async loadOrg(): Promise<void> {
    try {
      const org = await firstValueFrom(
        this.http.get<Partial<OrgProfile> & { id: string; code: string }>('/api/v1/organizations/me')
      );
      this.org = {
        id: org.id,
        code: org.code,
        name: org.name ?? '',
        email: org.email ?? '',
        phone: org.phone ?? '',
        address: org.address ?? '',
        locale: org.locale ?? 'fr',
        timezone: org.timezone ?? 'Africa/Nouakchott',
        currency: org.currency ?? 'MRU',
      };
    } catch {
      // offline or not tenant — skip
    }
  }

  private async loadSettings(): Promise<void> {
    try {
      const settings = await firstValueFrom(
        this.http.get<{
          posSettings?: Partial<PosSettings>;
          invoiceSettings?: Partial<InvoiceSettings>;
          paymentSettings?: Partial<PaymentSettings>;
        }>('/api/v1/settings')
      );
      if (settings.posSettings) {
        this.posSettings = { ...this.posSettings, ...settings.posSettings };
      }
      if (settings.invoiceSettings) {
        this.invoiceSettings = { ...this.invoiceSettings, ...settings.invoiceSettings };
      }
      if (settings.paymentSettings) {
        this.paymentSettings = { ...this.paymentSettings, ...settings.paymentSettings };
      }
    } catch {
      // no settings yet — use defaults
    }
  }

  async saveOrg(): Promise<void> {
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.http.patch(`/api/v1/organizations/${this.org.id}`, {
          name: this.org.name,
          email: this.org.email,
          phone: this.org.phone,
          address: this.org.address,
          locale: this.org.locale,
          timezone: this.org.timezone,
        })
      );
      this.msg.add({ severity: 'success', summary: this.i18n.instant('common.saved'), life: 3000 });
    } catch {
      this.msg.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail: this.i18n.instant('common.saveFailed'), life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }

  async saveSettings(): Promise<void> {
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.http.put('/api/v1/settings', {
          posSettings: this.posSettings,
          invoiceSettings: this.invoiceSettings,
          paymentSettings: this.paymentSettings,
        })
      );
      this.msg.add({ severity: 'success', summary: this.i18n.instant('common.saved'), life: 3000 });
    } catch {
      this.msg.add({ severity: 'error', summary: this.i18n.instant('common.error'), detail: this.i18n.instant('common.saveFailed'), life: 4000 });
    } finally {
      this.saving.set(false);
    }
  }
}
