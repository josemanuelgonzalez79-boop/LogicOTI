export type LevelType = 'floor' | 'exterior' | 'roof' | 'basement' | 'technical';

export interface LevelDefinition {
  id: string;
  name: string;
  slug: string;
  order: number;
  type: LevelType;
  icon: string;
  mapPath: string;
  mapAvailable: boolean;
}
