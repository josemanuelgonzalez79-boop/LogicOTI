import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ColorTheme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly storageKey = 'logicoti-color-theme';
  private readonly darkModeState = signal(false);

  readonly darkMode = this.darkModeState.asReadonly();

  constructor() {
    this.applyTheme(this.readInitialTheme());
  }

  toggle(): void {
    this.applyTheme(this.darkModeState() ? 'light' : 'dark');
  }

  private readInitialTheme(): ColorTheme {
    try {
      const savedTheme = this.document.defaultView?.localStorage.getItem(this.storageKey);

      if (savedTheme === 'dark' || savedTheme === 'light') {
        return savedTheme;
      }
    } catch {
      // La aplicación puede continuar aunque el navegador bloquee el almacenamiento local.
    }

    return 'light';
  }

  private applyTheme(theme: ColorTheme): void {
    const darkMode = theme === 'dark';

    this.document.documentElement.classList.toggle('app-dark', darkMode);
    this.darkModeState.set(darkMode);

    try {
      this.document.defaultView?.localStorage.setItem(this.storageKey, theme);
    } catch {
      // El cambio visual se conserva durante la sesión aunque no se pueda guardar.
    }
  }
}
