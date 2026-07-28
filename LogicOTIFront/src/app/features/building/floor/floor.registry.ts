import { LevelDefinition } from './floor.models';

export const LEVEL_REGISTRY: Record<string, LevelDefinition> = {
  PB: {
    id: 'PB',
    name: 'Planta Baja',
    slug: 'pb',
    order: 1,
    type: 'floor',
    icon: 'pi pi-home',
    mapPath: 'assets/buildings/oti/levels/pb/map.svg',
  },

  P1: {
    id: 'P1',
    name: 'Piso 1',
    slug: 'p1',
    order: 2,
    type: 'floor',
    icon: 'pi pi-building',
    mapPath: 'assets/buildings/oti/levels/p1/map.svg',
  },

  P2: {
    id: 'P2',
    name: 'Piso 2',
    slug: 'p2',
    order: 3,
    type: 'floor',
    icon: 'pi pi-building',
    mapPath: 'assets/buildings/oti/levels/p2/map.svg',
  },
};