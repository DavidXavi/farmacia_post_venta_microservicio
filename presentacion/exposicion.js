(function () {
  var laminas = Array.prototype.slice.call(document.querySelectorAll('.lamina'));
  var actual = 0;

  var contador = document.getElementById('contador');
  var barra = document.getElementById('barra');
  var panelNotas = document.getElementById('notas');
  var btnNotas = document.getElementById('btnNotas');
  var guion = document.getElementById('guion');
  var puntos = document.getElementById('puntos');
  var tiempo = document.getElementById('tiempo');

  function pintarNotas() {
    var l = laminas[actual];
    guion.textContent = l.dataset.guion || 'Sin guion para esta diapositiva.';
    tiempo.textContent = l.dataset.tiempo ? '· ' + l.dataset.tiempo : '';
    puntos.innerHTML = '';
    (l.dataset.puntos || '').split('|').filter(Boolean).forEach(function (p) {
      var li = document.createElement('li');
      li.textContent = p;
      puntos.appendChild(li);
    });
  }

  function ir(n) {
    actual = Math.max(0, Math.min(laminas.length - 1, n));
    laminas.forEach(function (l, i) { l.classList.toggle('activa', i === actual); });
    contador.textContent = (actual + 1) + ' / ' + laminas.length;
    barra.style.width = ((actual + 1) / laminas.length * 100) + '%';
    laminas[actual].scrollTop = 0;
    pintarNotas();
    // El hash permite abrir directo en una diapositiva (#8) y sobrevive a un F5
    // en medio de la exposicion, que es cuando mas duele perder el sitio.
    if (location.hash !== '#' + (actual + 1)) {
      history.replaceState(null, '', '#' + (actual + 1));
    }
    try { localStorage.setItem('expo-lamina', String(actual)); } catch (e) { /* modo privado */ }
  }

  function alternarNotas() {
    var abierto = panelNotas.classList.toggle('abierto');
    btnNotas.setAttribute('aria-pressed', abierto ? 'true' : 'false');
    try { localStorage.setItem('expo-notas', abierto ? '1' : '0'); } catch (e) { /* modo privado */ }
  }

  document.getElementById('btnSig').onclick = function () { ir(actual + 1); };
  document.getElementById('btnAnt').onclick = function () { ir(actual - 1); };
  btnNotas.onclick = alternarNotas;

  document.getElementById('btnTema').onclick = function () {
    var raiz = document.documentElement;
    var oscuro = raiz.getAttribute('data-theme') === 'dark'
      || (!raiz.getAttribute('data-theme') && matchMedia('(prefers-color-scheme: dark)').matches);
    raiz.setAttribute('data-theme', oscuro ? 'light' : 'dark');
    try { localStorage.setItem('pos-tema', oscuro ? 'light' : 'dark'); } catch (e) { /* modo privado */ }
  };

  document.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowRight' || e.key === 'PageDown' || e.key === ' ') { e.preventDefault(); ir(actual + 1); }
    else if (e.key === 'ArrowLeft' || e.key === 'PageUp') { e.preventDefault(); ir(actual - 1); }
    else if (e.key === 'Home') { ir(0); }
    else if (e.key === 'End') { ir(laminas.length - 1); }
    else if (e.key === 'n' || e.key === 'N') { alternarNotas(); }
  });

  window.addEventListener('hashchange', function () {
    var n = parseInt(location.hash.slice(1), 10);
    if (!isNaN(n) && n - 1 !== actual) { ir(n - 1); }
  });

  // El hash manda sobre lo guardado: si alguien comparte el enlace de la
  // diapositiva 8, tiene que abrir en la 8 aunque el navegador recuerde otra.
  var inicial = parseInt(location.hash.slice(1), 10);
  try {
    var t = localStorage.getItem('pos-tema');
    if (t) { document.documentElement.setAttribute('data-theme', t); }
    if (localStorage.getItem('expo-notas') === '1') { alternarNotas(); }
    if (isNaN(inicial)) {
      inicial = parseInt(localStorage.getItem('expo-lamina') || '0', 10) + 1;
    }
  } catch (e) {
    // Ventana privada o almacenamiento bloqueado: se arranca en la primera.
  }
  ir(isNaN(inicial) ? 0 : inicial - 1);
})();
