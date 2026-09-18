import { Component, computed, input } from '@angular/core';

export type StatusType =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'ALARM'
  | 'OFFLINE'
  | 'MAINTENANCE';

@Component({
  selector: 'app-status-chip',
  standalone: true,
  templateUrl: './status-chip.html',
  styleUrl: './status-chip.scss'
})
export class StatusChip {

  status = input.required<StatusType>();

  label = computed(() => {

    switch (this.status()) {

      case 'ACTIVE':
        return 'Activo';

      case 'INACTIVE':
        return 'Inactivo';

      case 'ALARM':
        return 'Alarma';

      case 'OFFLINE':
        return 'Desconectado';

      case 'MAINTENANCE':
        return 'Mantenimiento';

    }

  });

}