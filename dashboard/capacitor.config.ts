import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.potatameister.smapi',
  appName: 'PotataSMAPI',
  webDir: 'dist',
  android: {
    path: '../android'
  }
};

export default config;
