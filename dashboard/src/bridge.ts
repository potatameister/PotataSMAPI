import { registerPlugin } from '@capacitor/core';

export interface PotataBridgePlugin {
  startPatching(options: { path: string }): Promise<{ success: boolean }>;
  pickFolder(): Promise<{ path: string }>;
}

const PotataBridge = registerPlugin<PotataBridgePlugin>('PotataBridge');

export default PotataBridge;
