import { Component } from '@angular/core';

import { PageHeader } from '../../shared/components/page-header/page-header';
import { AppCard } from '../../shared/components/app-card/app-card';
import { KpiCard } from '../../shared/components/kpi-card/kpi-card';
import { StatusChip } from '../../shared/components/status-chip/status-chip';
import { Loading } from '../../shared/components/loading/loading';
import { EmptyState } from '../../shared/components/empty-state/empty-state';

@Component({
  selector: 'app-ui-showcase',
  standalone: true,
  imports: [
    PageHeader,
    AppCard,
    KpiCard,
    StatusChip,
    Loading,
    EmptyState
  ],
  templateUrl: './ui-showcase.html',
  styleUrl: './ui-showcase.scss'
})
export class UiShowcase {}