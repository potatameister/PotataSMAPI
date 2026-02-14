import React, { useState, useEffect } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [apkPath, setApkPath] = useState<string | null>(null);
  const [manualPath, setManualPath] = useState('');
  const [mods, setMods] = useState<string[]>([]);
  const [isPatching, setIsPatching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  // ... (auto-load code remains the same)

  const handleManualPath = async () => {
    if (!manualPath) return;
    setPath(manualPath);
    // Note: Manual path might need permission handling, but it's a good UI fallback
    setStatus("Using manual path. Mods will be scanned.");
    const modResult = await PotataBridge.getMods({ uri: manualPath });
    setMods(modResult.mods);
  };

  return (
    <div className='dashboard'>
      {/* ... Hero Card remains the same */}

      {/* Mod Manager */}
      <div className='bento-card mod-count'>
        <h3>Mods</h3>
        <h2>{mods.length}</h2>
        <div style={{ maxHeight: '60px', overflowY: 'auto', fontSize: '0.8rem', color: '#888', marginTop: '4px' }}>
          {path ? (mods.length > 0 ? mods.join(', ') : 'No mods found') : (
            <div style={{ display: 'flex', gap: '4px', marginTop: '4px' }}>
              <input 
                type="text" 
                placeholder="/sdcard/StardewValley"
                value={manualPath}
                onChange={(e) => setManualPath(e.target.value)}
                style={{ background: '#222', border: '1px solid #444', color: 'white', fontSize: '0.7rem', flex: 1, padding: '4px' }}
              />
              <button onClick={handleManualPath} style={{ fontSize: '0.7rem', background: '#444', border: 'none', color: 'white' }}>Set</button>
            </div>
          )}
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
