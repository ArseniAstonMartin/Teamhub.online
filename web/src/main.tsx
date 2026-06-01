import React from 'react';
import ReactDOM from 'react-dom/client';
import { initSentry } from '@app/sentry';
import App from './App';
import './index.css';

initSentry();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
