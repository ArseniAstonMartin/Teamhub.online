import { BrowserRouter, Routes, Route } from 'react-router-dom';
import DesignSystemPage from '@/pages/design-system/ui/DesignSystemPage';

function HomePage() {
  return (
    <div className="text-slate-900 bg-white min-h-screen flex items-center justify-center">
      <h1 className="text-2xl font-semibold">Teamhub</h1>
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/design-system" element={<DesignSystemPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
