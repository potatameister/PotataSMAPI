import { registerPlugin } from '@capacitor/core';

export interface PotataBridgePlugin {
  startPatching(options: { path: string }): Promise<{ success: boolean }>;
  pickFolder(): Promise<{ path: string }>;
  getMods(options: { uri: string }): Promise<{ mods: string[] }>;
}

const PotataBridge = registerPlugin<PotataBridgePlugin>('PotataBridge');

export default PotataBridge;
