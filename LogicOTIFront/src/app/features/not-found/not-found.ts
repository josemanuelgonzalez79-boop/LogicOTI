import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-not-found',
  imports: [ButtonModule, RouterLink],
  template: `
    <section class="not-found">
      <span>404</span>
      <h1>Página no encontrada</h1>
      <p>La ruta solicitada no existe.</p>
      <a pButton routerLink="/" label="Volver al inicio" icon="pi pi-home"></a>
    </section>
  `,
  styles: `
    .not-found {
      min-height: 65vh;
      display: grid;
      place-content: center;
      justify-items: center;
      text-align: center;
    }
    span {
      color: var(--p-primary-color);
      font-size: 5rem;
      font-weight: 800;
    }
    h1 {
      margin: 0;
    }
    p {
      color: var(--p-text-muted-color);
    }
  `,
})
export class NotFound {}
