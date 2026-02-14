import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [manualPath, setManualPath] = useState('/sdcard/StardewValley');
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    const init = async () => {
      try {
        const result = await PotataBridge.getSavedFolder();
        if (result.path) {
          setPath(result.path);
          const modResult = await PotataBridge.getMods({ uri: result.path });
          setMods(modResult.mods);
        }
      } catch (err) {
        console.error("Auto-load failed", err);
      }
    };
    init();
  }, []);

  const handlePickFolder = async () => {
    try {
      setStatus("Opening Picker...");
      const result = await PotataBridge.pickFolder();
      if (result.path) {
        setPath(result.path);
        const modResult = await PotataBridge.getMods({ uri: result.path });
        setMods(modResult.mods);
        setStatus(null);
      } else {
        setStatus(null);
      }
    } catch (err) {
      setStatus("Picker Failed - Use Manual Path");
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

  const handleManualPath = async () => {
    if (!manualPath) return;
    setPath(manualPath);
    try {
      const modResult = await PotataBridge.getMods({ uri: manualPath });
      setMods(modResult.mods);
      setStatus(null);
    } catch (err) {
      setStatus("Manual Path Failed");
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

  const handleReset = () => {
    setPath(null);
    setApkPath(null);
    setMods([]);
    setStatus(null);
  };

  return (
    <div className='dashboard'>
      <div className='bento-card hero'>
        <h3>PotataSMAPI</h3>
        <h2>{status || (path ? (apkPath ? 'Ready' : 'APK Required') : 'Setup Required')}</h2>
        
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          {!path ? (
            <button className='play-button' onClick={handlePickFolder}>1. Set Folder</button>
          ) : !apkPath ? (
            <button className='play-button' onClick={handlePickApk}>2. Select APK</button>
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
        <div style={{ fontSize: '0.8rem', color: '#888', marginTop: '4px' }}>
          {path ? mods.join(', ') : (
            <div style={{ display: 'flex', gap: '4px' }}>
              <input 
                type="text" 
                value={manualPath}
                onChange={(e) => setManualPath(e.target.value)}
                style={{ background: '#222', border: '1px solid #444', color: 'white', flex: 1, fontSize: '0.7rem' }}
              />
              <button onClick={handleManualPath} style={{ fontSize: '0.7rem' }}>Set</button>
            </div>
          )}
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
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <h3>Path</h3>
          {path && <button onClick={handleReset} style={{ fontSize: '0.6rem' }}>Reset</button>}
        </div>
        <p style={{ fontSize: '0.6rem', wordBreak: 'break-all', opacity: 0.6 }}>{path || 'No folder set'}</p>
      </div>
    </div>
  )
}

export default App
