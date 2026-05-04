import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { LoadingSpinnerComponent, EmptyStateComponent } from '@minierp/shared-ui';
import { PageResponse } from '@minierp/shared-api';

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

    @if (loading()) {
      <me-loading-spinner [label]="'common.loading' | translate" />
    } @else if (rows().length === 0) {
      <me-empty-state
        icon="pi pi-shield"
        [title]="'audit.empty.title' | translate"
      />
    } @else {
      <p-table
        [value]="rows()"
        [paginator]="true"
        [rows]="50"
        responsiveLayout="stack"
        breakpoint="768px"
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
  protected readonly loading = signal(true);
  protected readonly rows = signal<AuditRow[]>([]);

  ngOnInit(): void {
    this.http
      .get<PageResponse<AuditRow>>('/api/v1/audit?page=0&size=50')
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
