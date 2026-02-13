import React, { useState } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const handlePickFolder = async () => {
    try {
      const result = await PotataBridge.pickFolder();
      setPath(result.path);
      setStatus("Scanning for mods...");
      
      const modResult = await PotataBridge.getMods({ uri: result.path });
      setMods(modResult.mods);
      setStatus("Ready to Farm");
    } catch (err) {
      console.error("Failed to pick folder", err);
      setStatus("Folder selection failed");
    }
  };

  const handleLaunch = async () => {
    if (!path) return;
    setIsPatching(true);
    setStatus("Starting Digital Surgery...");
    try {
      // In a real test, the user would provide the APK path
      // For now, we simulate the bridge call
      await PotataBridge.startPatching({ path: "dummy_internal_path" });
      setStatus("Patching Complete! Please install the modded APK.");
    } catch (err) {
      setStatus("Patching Failed. Check logs.");
    } finally {
      setIsPatching(false);
    }
  };

  return (
    <div className='dashboard'>
      {/* Main Status & Play */}
      <div className='bento-card hero'>
        <h3>Stardew Valley</h3>
        <h2>{status || (path ? 'Ready to Farm' : 'Setup Required')}</h2>
        {!path ? (
          <button className='play-button' onClick={handlePickFolder}>Set Game Folder</button>
        ) : (
          <button 
            className='play-button' 
            onClick={handleLaunch} 
            disabled={isPatching}
            style={{ opacity: isPatching ? 0.5 : 1 }}
          >
            {isPatching ? 'Patching...' : 'Launch Game'}
          </button>
        )}
      </div>

      {/* Mod Manager */}
      <div className='bento-card mod-count'>
        <h3>Mods</h3>
        <h2>{mods.length}</h2>
        <p>{path ? (mods.length > 0 ? mods.join(', ') : 'No mods found') : 'Set folder to start'}</p>
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

      {/* Path Info (New Card) */}
      {path && (
        <div className='bento-card logs' style={{ gridColumn: 'span 2' }}>
          <h3>Base Directory</h3>
          <p style={{ fontSize: '0.7rem', wordBreak: 'break-all', opacity: 0.7 }}>{path}</p>
        </div>
      )}
    </div>
  )
}

export default App
