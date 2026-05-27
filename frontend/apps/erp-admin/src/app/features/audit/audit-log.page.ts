import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { LoadingSpinnerComponent, EmptyStateComponent } from '@minierp/shared-ui';
import { PageResponse } from '@minierp/shared-api';
import { firstValueFrom } from 'rxjs';

interface AuditRow {
  id: string;
  occurredAt: string;
  action: string;
  entityType: string;
  entityId: string;
  actorUserId: string;
  ipAddress: string;
}

@Component({
  selector: 'erp-admin-audit-log',
  standalone: true,
  imports: [
    CommonModule, DatePipe, TranslateModule, TableModule, TagModule,
    LoadingSpinnerComponent, EmptyStateComponent,
  ],
  template: `
    <header class="mb-4">
      <h1 class="text-2xl font-bold text-gray-800">{{ 'audit.title' | translate }}</h1>
      <p class="text-gray-600 mt-1">{{ 'audit.subtitle' | translate }}</p>
    </header>

    @if (loading() && total() === 0) {
      <me-loading-spinner [label]="'common.loading' | translate" />
    } @else if (total() === 0) {
      <me-empty-state
        icon="pi pi-shield"
        [title]="'audit.empty.title' | translate"
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
            <th>{{ 'audit.column.when' | translate }}</th>
            <th>{{ 'audit.column.action' | translate }}</th>
            <th>{{ 'audit.column.entity' | translate }}</th>
            <th>{{ 'audit.column.actor' | translate }}</th>
            <th>{{ 'audit.column.ip' | translate }}</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-row>
          <tr>
            <td>{{ row.occurredAt | date: 'short' }}</td>
            <td><p-tag [value]="row.action" severity="info" /></td>
            <td>{{ row.entityType }} <span class="text-xs text-gray-500">/ {{ row.entityId }}</span></td>
            <td class="font-mono text-xs">{{ row.actorUserId }}</td>
            <td>{{ row.ipAddress }}</td>
          </tr>
        </ng-template>
      </p-table>
    }
  `,
})
export class AuditLogPage implements OnInit {
  private readonly http = inject(HttpClient);
  @ViewChild('table') private table?: Table;
  protected readonly pageSize = 50;
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly rows = signal<AuditRow[]>([]);

  ngOnInit(): void {
    // Rows fetched on demand via the p-table's onLazyLoad.
  }

  protected async loadChunk(event: TableLazyLoadEvent) {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.loading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<PageResponse<AuditRow>>(`/api/v1/audit?page=${page}&size=${rows}`)
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
}
