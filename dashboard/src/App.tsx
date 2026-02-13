import React from 'react'

function App() {
  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center', 
      height: '100vh',
      fontFamily: 'sans-serif',
      backgroundColor: '#1a1a1a',
      color: 'white'
    }}>
      <h1>🥔 PotataSMAPI</h1>
      <p>Mod Launcher Dashboard Initialized</p>
      <div style={{ 
        padding: '20px', 
        backgroundColor: '#333', 
        borderRadius: '12px',
        border: '1px solid #444'
      }}>
        <p>Current SMAPI Core: <b>4.5.1</b></p>
      </div>
    </div>
  )
}

export default App
