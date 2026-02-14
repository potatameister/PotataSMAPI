import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    const init = async () => {
      try {
        setStatus("Initializing...");
        const result = await PotataBridge.initFolder();
        setPath(result.path);
        
        // Auto-scan for mods
        const modResult = await PotataBridge.getMods({ uri: result.path });
        setMods(modResult.mods);

        // Auto-locate game
        setStatus("Locating Stardew Valley...");
        const game = await PotataBridge.autoLocateGame();
        if (game.path) {
          setApkPath(game.path);
          setStatus(null);
        } else {
          setStatus("Game not found. Please install Stardew Valley.");
        }
      } catch (err) {
        console.error("Init failed", err);
        setStatus("Setup Required");
      }
    };
    init();
  }, []);

  const handlePickApk = async () => {
    try {
      const result = await PotataBridge.pickApk();
      setApkPath(result.path);
      setStatus(null);
    } catch (err) {
      console.error("Failed to pick APK", err);
    }
  };

  const handleLaunch = async () => {
    if (!apkPath) return;
    setIsPatching(true);
    setStatus("Starting Digital Surgery...");
    try {
      await PotataBridge.startPatching({ path: apkPath });
      setStatus("Patching Complete! Please install the modded APK.");
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
        <h2>{status || (apkPath ? 'Ready to Farm' : 'Game Not Found')}</h2>
        
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          {!apkPath ? (
            <button className='play-button' onClick={handlePickApk}>Select APK Manually</button>
          ) : (
            <button 
              className='play-button' 
              onClick={handleLaunch} 
              disabled={isPatching}
              style={{ opacity: isPatching ? 0.5 : 1 }}
            >
              {isPatching ? 'Patching...' : 'Patch & Launch'}
            </button>
          )}
        </div>
      </div>

      <div className='bento-card mod-count'>
        <h3>Mods</h3>
        <h2>{mods.length}</h2>
        <div style={{ fontSize: '0.8rem', color: '#888', marginTop: '4px', maxHeight: '100px', overflowY: 'auto' }}>
          {path && mods.length > 0 ? mods.join(', ') : 'Drop mods in PotataSMAPI/Mods'}
        </div>
      </div>

      <div className='bento-card smapi-status'>
        <h3>Engine</h3>
        <h2>4.5.1</h2>
        <div className='status-indicator'>
          <div className='dot' style={{ backgroundColor: apkPath ? '#4caf50' : '#f44336' }}></div>
          {apkPath ? 'Stable' : 'Offline'}
        </div>
      </div>

      <div className='bento-card logs' style={{ gridColumn: 'span 2' }}>
        <h3>Work Directory</h3>
        <p style={{ fontSize: '0.6rem', wordBreak: 'break-all', opacity: 0.6 }}>{path || 'Not initialized'}</p>
        {apkPath && (
          <p style={{ fontSize: '0.5rem', color: '#4caf50', marginTop: '4px' }}>Detected: {apkPath.substring(0, 50)}...</p>
        )}
      </div>
    </div>
  )
}

export default App
