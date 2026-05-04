import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { LoadingSpinnerComponent, EmptyStateComponent } from '@minierp/shared-ui';
import { PageResponse } from '@minierp/shared-api';

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

    @if (loading()) {
      <me-loading-spinner [label]="'common.loading' | translate" />
    } @else if (rows().length === 0) {
      <me-empty-state
        icon="pi pi-building"
        [title]="'organizations.empty.title' | translate"
        [description]="'organizations.empty.description' | translate"
      />
    } @else {
      <p-table
        [value]="rows()"
        [paginator]="true"
        [rows]="20"
        responsiveLayout="stack"
        breakpoint="768px"
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

  protected readonly loading = signal(true);
  protected readonly rows = signal<OrganizationRow[]>([]);

  ngOnInit(): void {
    this.http
      .get<PageResponse<OrganizationRow>>('/api/v1/organizations?page=0&size=50')
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
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
