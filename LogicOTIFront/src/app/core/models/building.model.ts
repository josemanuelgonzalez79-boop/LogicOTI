export interface Building {
  code: string;
  name: string;
  floors: BuildingFloor[];
  totals: BuildingTotals;
}

export interface BuildingFloor {
  id: number;
  code: string;
  name: string;
  displayOrder: number;
  areas: BuildingArea[];
}

export interface BuildingArea {
  id: number;
  code: string;
  name: string;
  type: string;
  displayOrder: number;
  inventory: AreaInventory;
}

export interface AreaInventory {
  lamps: number;
  motionSensors: number;
  doorSensors: number;
  smokeSensors: number;
  outlets: number;
  switches: number;
  minisplits: number;
}

export interface BuildingTotals {
  floors: number;
  areas: number;
  lamps: number;
  motionSensors: number;
  doorSensors: number;
  smokeSensors: number;
  outlets: number;
  switches: number;
  minisplits: number;
}