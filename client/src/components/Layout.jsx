import { NavLink, Outlet } from 'react-router-dom';

const navItems = [
  { label: 'Dashboard', to: '/' },
  { label: 'Nueva transacción', to: '/transactions/new' }
];

function Layout() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">FI</div>
          <div>
            <h1>Fraud Ingest</h1>
            <p>Console antifraude</p>
          </div>
        </div>

        <nav className="nav-links">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-card">
          <p className="eyebrow">Seguridad</p>
          <strong>IDs y datos sensibles</strong>
          <span>Se muestran en formato protegido para limitar la exposición.</span>
        </div>
      </aside>

      <main className="content">
        <header className="topbar">
          <div className="topbar-left">
            <input className="search" placeholder="Buscar transacción, cuenta o comercio" />
          </div>
          <div className="topbar-right">
            <button className="ghost-button">Notificaciones</button>
            <div className="avatar">JU</div>
          </div>
        </header>

        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
