import React, { useState } from 'react'
import './App.css'
import PotataBridge from './bridge'

function App() {
  const [path, setPath] = useState<string | null>(null);
  const [mods, setMods] = useState<string[]>([]);

  const handlePickFolder = async () => {
    try {
      const result = await PotataBridge.pickFolder();
      setPath(result.path);
      
      // Scan for mods immediately after picking
      const modResult = await PotataBridge.getMods({ uri: result.path });
      setMods(modResult.mods);
    } catch (err) {
      console.error("Failed to pick folder", err);
    }
  };

  return (
    <div className='dashboard'>
      {/* Main Status & Play */}
      <div className='bento-card hero'>
        <h3>Stardew Valley</h3>
        <h2>{path ? 'Ready to Farm' : 'Setup Required'}</h2>
        {!path ? (
          <button className='play-button' onClick={handlePickFolder}>Set Game Folder</button>
        ) : (
          <button className='play-button'>Launch Game</button>
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
