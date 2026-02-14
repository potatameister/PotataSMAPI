import { registerPlugin } from '@capacitor/core';

export interface PotataBridgePlugin {
  startPatching(options: { path: string }): Promise<{ success: boolean }>;
  pickFolder(): Promise<{ path: string }>;
  pickApk(): Promise<{ path: string }>;
  getSavedFolder(): Promise<{ path: string | null }>;
  getMods(options: { uri: string }): Promise<{ mods: string[] }>;
}

const PotataBridge = registerPlugin<PotataBridgePlugin>('PotataBridge');

export default PotataBridge;
