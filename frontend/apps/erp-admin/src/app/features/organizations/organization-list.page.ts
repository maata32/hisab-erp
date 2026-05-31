import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { LoadingSpinnerComponent, EmptyStateComponent } from '@minierp/shared-ui';
import { PageResponse } from '@minierp/shared-api';
import { firstValueFrom } from 'rxjs';

interface OrganizationRow {
  id: string;
  code: string;
  name: string;
  type: string;
  status: string;
  currency: string;
  locale: string;
}

@Component({
  selector: 'erp-admin-organization-list',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    TableModule,
    TagModule,
    LoadingSpinnerComponent,
    EmptyStateComponent,
  ],
  template: `
    <header class="mb-4">
      <h1 class="text-2xl font-bold text-gray-800">{{ 'organizations.title' | translate }}</h1>
      <p class="text-gray-600 mt-1">{{ 'organizations.subtitle' | translate }}</p>
    </header>

    @if (loading() && total() === 0) {
      <me-loading-spinner [label]="'common.loading' | translate" />
    } @else if (total() === 0) {
      <me-empty-state
        icon="pi pi-building"
        [title]="'organizations.empty.title' | translate"
        [description]="'organizations.empty.description' | translate"
      />
    } @else {
      <p-table #table
        [value]="rows()"
        [lazy]="true" (onLazyLoad)="loadChunk($event)"
        [totalRecords]="total()" [rows]="pageSize"
        [scrollable]="true" scrollHeight="600px"
        [virtualScroll]="true" [virtualScrollItemSize]="48"
        styleClass="bg-white rounded-lg border border-gray-200"
      >
        <ng-template pTemplate="header">
          <tr>
            <th>{{ 'organizations.code' | translate }}</th>
            <th>{{ 'organizations.name' | translate }}</th>
            <th>{{ 'organizations.type' | translate }}</th>
            <th>{{ 'organizations.status' | translate }}</th>
            <th>{{ 'organizations.currency' | translate }}</th>
            <th>{{ 'organizations.locale' | translate }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-row>
          <tr>
            <td><span class="font-mono text-xs">{{ row.code }}</span></td>
            <td>{{ row.name }}</td>
            <td>{{ row.type }}</td>
            <td><p-tag [value]="row.status" [severity]="severity(row.status)" /></td>
            <td>{{ row.currency }}</td>
            <td>{{ row.locale }}</td>
          </tr>
        </ng-template>
      </p-table>
    }
  `,
})
export class OrganizationListPage implements OnInit {
  private readonly http = inject(HttpClient);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly rows = signal<OrganizationRow[]>([]);

  ngOnInit(): void {
    // The <p-table> that emits (onLazyLoad) only renders once total() > 0, so the
    // lazy callback can never fire on its own — kick off the first page here.
    void this.loadChunk({ first: 0, rows: this.pageSize });
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows || this.pageSize; // || (not ??) so virtual-scroll's initial rows:0 falls back to pageSize

    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<PageResponse<OrganizationRow>>(`/api/v1/organizations?page=${page}&size=${rows}`)
      );
      const items = res.content ?? [];
      const totalElements = res.totalElements ?? items.length;
      const arr = first === 0 ? new Array(totalElements) : [...this.rows()];
      arr.length = totalElements;
      for (let i = 0; i < items.length; i++) arr[first + i] = items[i];
      this.rows.set(arr);
      this.total.set(totalElements);
    } catch {
      this.rows.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  severity(status: string): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    switch (status) {
      case 'ACTIVE': return 'success';
      case 'TRIAL': return 'info';
      case 'PAST_DUE': return 'warning';
      case 'SUSPENDED':
      case 'ARCHIVED': return 'danger';
      default: return 'secondary';
    }
  }
}
