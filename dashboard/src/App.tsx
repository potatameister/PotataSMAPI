import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  // Auto-load saved folder on startup
  useEffect(() => {
    const loadSavedPath = async () => {
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
    loadSavedPath();
  }, []);

  const handlePickFolder = async () => {
    try {
      setStatus("Opening Picker...");
      const result = await PotataBridge.pickFolder();
      if (result.path) {
        setPath(result.path);
        setStatus("Scanning for mods...");
        const modResult = await PotataBridge.getMods({ uri: result.path });
        setMods(modResult.mods);
        setStatus(null);
      } else {
        setStatus("Selection cancelled");
      }
    } catch (err) {
      console.error("Failed to pick folder", err);
      setStatus("Error: Permission Denied or Picker Failed");
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
    setStatus("Starting Digital Surgery...");
    try {
      await PotataBridge.startPatching({ path: apkPath });
      setStatus("Patching Complete! Please install the modded APK.");
    } catch (err) {
      setStatus("Patching Failed. Check logs.");
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
      {/* Main Status & Play */}
      <div className='bento-card hero'>
        <h3>Stardew Valley</h3>
        <h2>{status || (path ? (apkPath ? 'Ready to Farm' : 'APK Required') : 'Setup Required')}</h2>
        {!path && (
          <p style={{ fontSize: '0.7rem', color: '#ffcc00', marginTop: '4px' }}>
            Tip: Create a NEW folder (e.g. 'StardewMods') to bypass Android restrictions.
          </p>
        )}
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          {!path ? (
            <button className='play-button' onClick={handlePickFolder}>1. Set Folder</button>
          ) : !apkPath ? (
            <button className='play-button' onClick={handlePickApk}>2. Select Game APK</button>
          ) : (
            <button 
              className='play-button' 
              onClick={handleLaunch} 
              disabled={isPatching}
              style={{ opacity: isPatching ? 0.5 : 1 }}
            >
              {isPatching ? 'Patching...' : '3. Patch & Launch'}
            </button>
          )}
        </div>
      </div>

      {/* Mod Manager */}
      <div className='bento-card mod-count'>
        <h3>Mods</h3>
        <h2>{mods.length}</h2>
        <div style={{ maxHeight: '60px', overflowY: 'auto', fontSize: '0.8rem', color: '#888', marginTop: '4px' }}>
          {path ? (mods.length > 0 ? mods.join(', ') : 'No mods found') : 'Set folder to start'}
        </div>
      </div>

      {/* SMAPI Status */}
      <div className='bento-card smapi-status'>
        <h3>Engine</h3>
        <h2>4.5.1</h2>
        <div className='status-indicator'>
          <div className='dot' style={{ backgroundColor: path ? '#4caf50' : '#f44336' }}></div>
          {path ? 'Stable' : 'Offline'}
        </div>
      </div>

      {/* Settings/Reset (New Card) */}
      <div className='bento-card logs' style={{ gridColumn: 'span 2' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Settings</h3>
          {(path || apkPath) && (
            <button 
              onClick={handleReset}
              style={{ 
                background: 'transparent', 
                border: '1px solid #444', 
                color: '#888', 
                borderRadius: '8px', 
                padding: '4px 8px',
                fontSize: '0.7rem',
                cursor: 'pointer'
              }}
            >
              Reset Setup
            </button>
          )}
        </div>
        <div style={{ fontSize: '0.7rem', wordBreak: 'break-all', opacity: 0.7, marginTop: '8px' }}>
          {path ? `Folder: ${path}` : 'No folder selected'}
          <br/>
          {apkPath ? `APK: ${apkPath.substring(0, 40)}...` : 'No APK selected'}
        </div>
      </div>
    </div>
  )
}

export default App
