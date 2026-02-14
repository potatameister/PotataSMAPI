import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const scanMods = async (folderPath: string) => {
    try {
      const modResult = await PotataBridge.getMods({ uri: folderPath });
      setMods(modResult.mods);
    } catch (err) {
      console.error("Scan failed", err);
    }
  };

  const handleInit = async () => {
    try {
      setStatus("Initializing folder...");
      const result = await PotataBridge.initFolder();
      setPath(result.path);
      await scanMods(result.path);
      setStatus(null);
    } catch (err) {
      setStatus("Initialization failed - Check permissions");
    }
  };

  const handlePickApk = async () => {
    try {
      const result = await PotataBridge.pickApk();
      setApkPath(result.path);
    } catch (err) {
      console.error("Failed to pick APK", err);
    }
  };

  const handleLaunch = async () => {
    if (!path || !apkPath) return;
    setIsPatching(true);
    setStatus("Patching...");
    try {
      await PotataBridge.startPatching({ path: apkPath });
      setStatus("Complete! Install the new APK.");
    } catch (err) {
      setStatus("Patching Failed");
    } finally {
      setIsPatching(false);
    }
  };

  return (
    <div className='dashboard'>
      <div className='bento-card hero'>
        <h3>PotataSMAPI</h3>
        <h2>{status || (path ? (apkPath ? 'Ready to Farm' : 'APK Required') : 'Setup Required')}</h2>
        
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          {!path ? (
            <button className='play-button' onClick={handleInit}>1. Initialize</button>
          ) : !apkPath ? (
            <button className='play-button' onClick={handlePickApk}>2. Select Game APK</button>
          ) : (
            <button className='play-button' onClick={handleLaunch} disabled={isPatching}>
              {isPatching ? 'Patching...' : '3. Patch & Launch'}
            </button>
          )}
        </div>
      </div>

      <div className='bento-card mod-count'>
        <h3>Mods</h3>
        <h2>{mods.length}</h2>
        <div style={{ fontSize: '0.8rem', color: '#888', marginTop: '4px', maxHeight: '100px', overflowY: 'auto' }}>
          {path ? (mods.length > 0 ? mods.join(', ') : 'No mods found') : 'Initializing required'}
        </div>
      </div>

      <div className='bento-card smapi-status'>
        <h3>Engine</h3>
        <h2>4.5.1</h2>
        <div className='status-indicator'>
          <div className='dot' style={{ backgroundColor: path ? '#4caf50' : '#f44336' }}></div>
          {path ? 'Ready' : 'Setup'}
        </div>
      </div>

      <div className='bento-card logs' style={{ gridColumn: 'span 2' }}>
        <h3>Work Directory</h3>
        <p style={{ fontSize: '0.6rem', wordBreak: 'break-all', opacity: 0.6 }}>{path || 'Not initialized'}</p>
      </div>
    </div>
  )
}

export default App
