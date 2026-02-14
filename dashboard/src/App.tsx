import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>("Initializing...");

  const scanMods = async (folderPath: string) => {
    try {
      const modResult = await PotataBridge.getMods({ uri: folderPath });
      setMods(modResult.mods);
    } catch (err) {
      console.error("Scan failed", err);
    }
  };

  useEffect(() => {
    const init = async () => {
      try {
        const result = await PotataBridge.initFolder();
        setPath(result.path);
        await scanMods(result.path);

        const game = await PotataBridge.autoLocateGame();
        if (game.path) setApkPath(game.path);
        
        setStatus(null);
      } catch (err) {
        setStatus("Setup Required");
      }
    };
    init();
  }, []);

  const handlePickApk = async () => {
    try {
      const result = await PotataBridge.pickApk();
      if (result.path) setApkPath(result.path);
    } catch (err) {
      console.error("Manual APK selection failed", err);
    }
  };

  const handleLaunch = async () => {
    if (!apkPath) return;
    setIsPatching(true);
    setStatus("Digital Surgery in Progress...");
    try {
      const res = await PotataBridge.startPatching({ path: apkPath });
      if (res.success) setStatus("Complete! Please install the new APK.");
    } catch (err: any) {
      setStatus("Patching failed: " + (err.message || err));
    } finally {
      setIsPatching(false);
    }
  };

  const handleReset = async () => {
    setPath(null);
    setApkPath(null);
    setMods([]);
    setStatus("Reset Complete");
    setTimeout(() => window.location.reload(), 1000);
  };

  return (
    <div className='dashboard'>
      {/* Hero: Action Center */}
      <div className='bento-card hero'>
        <h3>PotataSMAPI v1.0</h3>
        <h2>{status || (apkPath ? 'Ready to Farm' : 'Game Not Detected')}</h2>
        
        <button 
          className='play-button' 
          onClick={apkPath ? handleLaunch : handlePickApk} 
          disabled={isPatching}
        >
          {isPatching ? 'SURGERY...' : (apkPath ? 'PATCH & LAUNCH' : 'FIND GAME APK')}
        </button>
        
        {apkPath && !isPatching && (
          <p onClick={handlePickApk} style={{ fontSize: '0.6rem', color: '#555', marginTop: '12px', cursor: 'pointer', textDecoration: 'underline' }}>
            Change target APK
          </p>
        )}
      </div>

      {/* Stats: Mods */}
      <div className='bento-card mod-count'>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <h3 style={{ color: '#aaa', fontSize: '0.7rem' }}>MODS</h3>
          <span className='chip'>{mods.length}</span>
        </div>
        <div style={{ marginTop: 'auto' }}>
          <div style={{ maxHeight: '40px', overflow: 'hidden', fontSize: '0.75rem', color: '#666' }}>
            {mods.length > 0 ? mods.join(', ') : 'No mods found in folder'}
          </div>
        </div>
      </div>

      {/* Stats: Engine */}
      <div className='bento-card smapi-status'>
        <h3 style={{ color: '#aaa', fontSize: '0.7rem' }}>ENGINE</h3>
        <h4 style={{ margin: '4px 0 0 0', fontSize: '1.1rem' }}>4.5.1</h4>
        <div className='status-indicator' style={{ background: apkPath ? 'rgba(76, 175, 80, 0.1)' : 'rgba(244, 67, 54, 0.1)', color: apkPath ? '#4caf50' : '#f44336' }}>
          <div className='dot' style={{ background: apkPath ? '#4caf50' : '#f44336' }}></div>
          {apkPath ? 'CORE LINKED' : 'OFFLINE'}
        </div>
      </div>

      {/* Workspace: Path Info */}
      <div className='bento-card path-card'>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <h3>WORKSPACE</h3>
          <span onClick={handleReset} style={{ fontSize: '0.6rem', color: '#444', cursor: 'pointer' }}>RESET</span>
        </div>
        <p className='path-text'>{path || 'Creating /sdcard/PotataSMAPI...'}</p>
      </div>

      {/* Instructions */}
      <div style={{ gridColumn: 'span 2', textAlign: 'center', padding: '10px' }}>
        <p style={{ fontSize: '0.6rem', color: '#333' }}>
          Mods Folder: /sdcard/PotataSMAPI/Mods
        </p>
      </div>
    </div>
  )
}

export default App
