/*
 * Interfaz de REDXela. Consulta GET /api/estado cada INTERVALO ms y vuelve a dibujar los paneles.
 * Los formularios no se redibujan (solo se llenan una vez), asi no se pierde lo que el usuario esta escribiendo.
 */
'use strict';

const INTERVALO = 700;

const estadoUI = {
    catalogo: null,
    clientes: [],
    loteSeleccionado: null,
    ultimo: null,
};

/** Texto de la columna "clave" segun la politica. */
const CLAVES = {
    PRIORIDAD_ESTRICTA: 'Nivel',
    ENVEJECIMIENTO: 'Prioridad efectiva',
    MENOS_ARTICULOS: 'Artículos',
    PLAZO_MAS_CERCANO: 'Min. para vencer',
    FIFO: 'Llegada (min)',
};

const ESTADOS = {
    EN_COLA: 'En cola', ESPERANDO_RECURSOS: 'Esperando recursos', ESPERANDO_STOCK: 'Esperando stock',
    EN_PROCESO: 'En proceso', COMPLETADO: 'Completado', CANCELADO: 'Cancelado',
    CREADA: 'Creada', EJECUTANDO: 'Ejecutando', ESPERANDO_RECURSO: 'Esperando recurso', BLOQUEADA: 'Bloqueada',
    EN_LISTA_ESPERA: 'En lista de espera', COMPLETADA: 'Completada', CANCELADA: 'Cancelada',
};

// ---------- Utilidades ----------

const $ = (sel) => document.querySelector(sel);

function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
}

const fmt = (n, d = 1) => Number(n).toLocaleString('es-GT', { minimumFractionDigits: d, maximumFractionDigits: d });
const pct = (n) => `${Math.round(n * 100)}%`;

function reloj(min) {
    const total = Math.floor(min);
    const h = Math.floor(total / 60);
    const m = total % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

const nivel = (n, nombre) => `<span class="nivel n${n}" title="${esc(nombre)}">${n}</span>`;
const chip = (estado) => `<span class="chip ${esc(estado)}">${esc(ESTADOS[estado] ?? estado)}</span>`;

/** Color estable por codigo de producto, para el mapa del almacen. */
function colorProducto(codigo) {
    let h = 0;
    for (const c of codigo) h = (h * 31 + c.charCodeAt(0)) % 360;
    return `hsl(${(h * 7) % 360} 52% 46%)`;
}

/** Etiqueta corta del duenio de un recurso: "Pedido #12" -> "#12", "OP-3 Recepcion..." -> "OP-3". */
function corto(duenio) {
    if (!duenio) return '';
    if (duenio.startsWith('Pedido ')) return duenio.slice(7);
    if (duenio.startsWith('OP-')) return duenio.split(' ')[0];
    return duenio;
}

function aviso(texto, error = false) {
    const div = document.createElement('div');
    div.className = `aviso${error ? ' error' : ''}`;
    div.textContent = texto;
    $('#avisos').appendChild(div);
    setTimeout(() => div.remove(), error ? 6000 : 3500);
}

async function api(metodo, url, cuerpo) {
    const opciones = { method: metodo, headers: {} };
    if (cuerpo !== undefined) {
        opciones.headers['Content-Type'] = 'application/json';
        opciones.body = JSON.stringify(cuerpo);
    }
    const r = await fetch(url, opciones);
    const datos = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(datos.error || `Error ${r.status}`);
    return datos;
}

async function accion(promesa, exito) {
    try {
        const r = await promesa;
        if (exito) aviso(typeof exito === 'function' ? exito(r) : exito);
        await refrescar();
        return r;
    } catch (e) {
        aviso(e.message, true);
        return null;
    }
}

// ---------- Pestanas ----------

function irA(tab) {
    document.querySelectorAll('.pestanas button').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === tab)));
    document.querySelectorAll('.tab').forEach((s) => s.classList.toggle('activa', s.id === `tab-${tab}`));
}

document.querySelectorAll('.pestanas button').forEach((b) => b.addEventListener('click', () => irA(b.dataset.tab)));
$('#alertaConflicto').addEventListener('click', () => irA('conflictos'));

// ---------- Carga inicial de catalogos y formularios ----------

async function cargarCatalogo() {
    estadoUI.catalogo = await api('GET', '/api/catalogo');
    const { productos, niveles, politicas } = estadoUI.catalogo;

    $('#politica').innerHTML = politicas.map((p) => `<option value="${p.id}">${esc(p.nombre)}</option>`).join('');
    const opcNiveles = niveles.map((n) => `<option value="${n.nivel}">${n.nivel} · ${esc(n.nombre)} (máx. ${n.maxEsperaMinutos} min)</option>`).join('');
    $('#selNivel').innerHTML = `<option value="">El contratado por el cliente</option>${opcNiveles}`;
    $('#selNivelCliente').innerHTML = opcNiveles;
    $('#selNivelCliente').value = '3';
    $('#selProductoLlegada').innerHTML = opcionesProducto(productos);
    agregarLinea();
}

function opcionesProducto(productos) {
    return productos.map((p) => `<option value="${p.codigo}">${p.codigo} · ${esc(p.nombre)} (${esc(p.tipo)})</option>`).join('');
}

async function cargarClientes(seleccionar) {
    estadoUI.clientes = await api('GET', '/api/clientes');
    const sel = $('#selCliente');
    const previo = seleccionar ?? sel.value;
    sel.innerHTML = estadoUI.clientes.map((c) =>
        `<option value="${c.id}">${esc(c.nombre)} · ${esc(c.nivelContratado)}</option>`).join('');
    if (previo) sel.value = String(previo);
}

function agregarLinea() {
    const div = document.createElement('div');
    div.className = 'linea';
    div.innerHTML = `
        <select aria-label="Producto">${opcionesProducto(estadoUI.catalogo.productos)}</select>
        <input type="number" min="1" max="200" value="3" aria-label="Unidades">
        <button type="button" class="fantasma" title="Quitar producto">×</button>`;
    div.querySelector('button').addEventListener('click', () => {
        if ($('#lineasPedido').children.length > 1) div.remove();
    });
    $('#lineasPedido').appendChild(div);
}

$('#btnAgregarLinea').addEventListener('click', agregarLinea);

$('#formPedido').addEventListener('submit', (ev) => {
    ev.preventDefault();
    const productos = {};
    document.querySelectorAll('#lineasPedido .linea').forEach((l) => {
        const codigo = l.querySelector('select').value;
        const cant = parseInt(l.querySelector('input').value, 10) || 0;
        productos[codigo] = (productos[codigo] || 0) + cant;
    });
    const nivelSel = $('#selNivel').value;
    accion(api('POST', '/api/pedidos', {
        clienteId: Number($('#selCliente').value),
        nivel: nivelSel ? Number(nivelSel) : null,
        productos,
    }), (r) => r.mensaje);
});

$('#formCliente').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const f = ev.target;
    const c = await accion(api('POST', '/api/clientes', {
        nombre: f.nombre.value, correo: f.correo.value, nivel: Number(f.nivel.value),
    }), (r) => `Cuenta creada para ${r.nombre}`);
    if (c) {
        f.reset();
        $('#selNivelCliente').value = '3';
        await cargarClientes(c.id);
    }
});

$('#formLlegada').addEventListener('submit', (ev) => {
    ev.preventDefault();
    const f = ev.target;
    accion(api('POST', '/api/recepcion', { codigo: f.codigo.value, cantidad: Number(f.cantidad.value) }),
        'Camión registrado en el patio');
});

$('#btnRafaga').addEventListener('click', () =>
    accion(api('POST', '/api/recepcion/rafaga?camiones=12'), (r) => r.mensaje));

$('#politica').addEventListener('change', (ev) =>
    accion(api('PUT', `/api/politica/${ev.target.value}`), (r) => `Política: ${r.nombre}`));

$('#btnGenPedidos').addEventListener('click', () => {
    const activo = estadoUI.ultimo?.generadores.pedidosActivo;
    accion(api('PUT', '/api/generador/pedidos', { activo: !activo, intervaloMs: Number($('#velPedidos').value) }));
});
$('#velPedidos').addEventListener('change', (ev) =>
    accion(api('PUT', '/api/generador/pedidos', { intervaloMs: Number(ev.target.value) })));
$('#btnGenCamiones').addEventListener('click', () => {
    const activo = estadoUI.ultimo?.generadores.camionesActivo;
    accion(api('PUT', '/api/generador/camiones', { activo: !activo }));
});

$('#btnProvocar').addEventListener('click', () =>
    accion(api('POST', '/api/conflicto/provocar?prevencion=false'), 'Operaciones lanzadas sin prevención'));
$('#btnPrevencion').addEventListener('click', () =>
    accion(api('POST', '/api/conflicto/provocar?prevencion=true'), 'Operaciones lanzadas con orden global'));
$('#btnLimpiarOps').addEventListener('click', () => accion(api('POST', '/api/conflicto/limpiar')));

// Delegacion: botones de resolver conflicto (se redibujan en cada ciclo).
$('#conflictoActivo').addEventListener('click', (ev) => {
    const b = ev.target.closest('button[data-ganadora]');
    if (!b) return;
    accion(api('POST', `/api/conflicto/${b.dataset.conflicto}/resolver`, { ganadora: b.dataset.ganadora }),
        (r) => r.mensaje);
});

// Seleccion de lote en el almacen.
function seleccionarLote(id) {
    estadoUI.loteSeleccionado = estadoUI.loteSeleccionado === id ? null : id;
    if (estadoUI.ultimo) dibujarInventario(estadoUI.ultimo.inventario);
}
$('#lotes').addEventListener('click', (ev) => {
    const li = ev.target.closest('li[data-lote]');
    if (li) seleccionarLote(Number(li.dataset.lote));
});
$('#almacen').addEventListener('click', (ev) => {
    const c = ev.target.closest('.celda[data-lote]');
    if (c) seleccionarLote(Number(c.dataset.lote));
});

// ---------- Dibujo ----------

function dibujar(e) {
    estadoUI.ultimo = e;
    $('#reloj').textContent = reloj(e.minutos);
    if (document.activeElement !== $('#politica')) $('#politica').value = e.politica.id;

    const bp = $('#btnGenPedidos');
    bp.setAttribute('aria-pressed', String(e.generadores.pedidosActivo));
    bp.textContent = e.generadores.pedidosActivo ? 'Activo' : 'Detenido';
    const bc = $('#btnGenCamiones');
    bc.setAttribute('aria-pressed', String(e.generadores.camionesActivo));
    bc.textContent = e.generadores.camionesActivo ? 'Activo' : 'Detenido';

    const hayConflicto = e.conflictos.conflictos.some((c) => !c.resuelto);
    $('#alertaConflicto').hidden = !hayConflicto;
    $('#badgeConflicto').hidden = !hayConflicto;

    dibujarOperacion(e);
    dibujarInventario(e.inventario);
    dibujarRecepcion(e.recepcion);
    dibujarConflictos(e.conflictos);
    dibujarEstadisticas(e);
    dibujarEventos(e.eventos);
}

function kpi(valor, etiqueta) {
    return `<div class="kpi"><div class="valor">${valor}</div><div class="etiqueta">${esc(etiqueta)}</div></div>`;
}

function dibujarOperacion(e) {
    const actual = e.estadisticas.porPolitica.find((p) => p.politica === e.politica.id);
    $('#kpisOperacion').innerHTML = [
        kpi(e.estadisticas.enProceso, 'En proceso'),
        kpi(e.estadisticas.enEspera, 'En lista de espera'),
        kpi(e.estadisticas.completados, 'Completados'),
        kpi(fmt(actual?.esperaPromedioMin ?? 0), 'Espera promedio (min)'),
        kpi(pct(actual?.cumplimientoSla ?? 0), 'SLA cumplido (política actual)'),
    ].join('');

    $('#recursos').innerHTML = e.recursos.map((r) => {
        const unidades = r.unidades.map((d, i) => {
            const clase = d ? (d.startsWith('OP-') || d === 'Recepcion' ? 'unidad op' : 'unidad ocupada') : 'unidad';
            return `<span class="${clase}" title="${esc(d ? `${r.nombre} ${i + 1}: ${d}` : `${r.nombre} ${i + 1}: disponible`)}">${esc(d ? corto(d) : i + 1)}</span>`;
        }).join('');
        const esperan = Object.entries(r.esperando);
        return `<div class="recurso">
            <div class="recurso-cab"><h3>${esc(r.nombre)}</h3><span class="num">${r.ocupados}/${r.capacidad} en uso</span></div>
            <div class="unidades">${unidades}</div>
            ${esperan.length ? `<div class="esperan">Esperando: ${esperan.map(([d, n]) => `${esc(corto(d))} (${n})`).join(', ')}</div>` : ''}
        </div>`;
    }).join('');

    $('#cuentaProceso').textContent = e.enProceso.length;
    $('#enProceso').innerHTML = e.enProceso.length ? e.enProceso.map((p) => `<tr>
            <td><b>#${p.id}</b>${p.manual ? '<span class="chip manual">manual</span>' : ''}<div class="secundario-txt">${esc(p.cliente)}</div></td>
            <td>${nivel(p.nivel, p.nivelNombre)}</td>
            <td class="der">${p.articulos}</td>
            <td>${esc(p.etapa)}<div class="secundario-txt">${esc(p.tipos.join(', '))}</div></td>
            <td><span class="mono">${pct(p.progreso)}</span><div class="barra-progreso"><span style="width:${(p.progreso * 100).toFixed(1)}%"></span></div></td>
            <td class="der">${fmt(p.restanteSeg)} s</td>
            <td class="secundario-txt">${esc(p.recursos.join(', ') || '—')}</td>
        </tr>`).join('') : '<tr><td colspan="7" class="vacio">No hay pedidos en proceso. Activa el generador o ingresa un pedido manual.</td></tr>';

    $('#cuentaEspera').textContent = e.espera.length;
    $('#colClave').textContent = CLAVES[e.politica.id] ?? 'Clave';
    $('#criterioEspera').innerHTML = `<b>${esc(e.politica.nombre)}:</b> ${esc(e.politica.descripcion)} Se ordena por la columna «${esc(CLAVES[e.politica.id])}» (menor = primero).`;
    $('#espera').innerHTML = e.espera.length ? e.espera.slice(0, 150).map((p) => `<tr class="${p.slaVencido ? 'vencido' : ''}">
            <td class="der">${p.posicion}</td>
            <td><b>#${p.id}</b>${p.manual ? '<span class="chip manual">manual</span>' : ''}<div class="secundario-txt">${esc(p.cliente)}</div></td>
            <td>${nivel(p.nivel, p.nivelNombre)}</td>
            <td class="der">${p.articulos}</td>
            <td class="der"><b>${fmt(p.clave, 2)}</b></td>
            <td class="der ${p.slaVencido ? 'sla-mal' : ''}">${fmt(p.esperaMin)} / ${p.limiteMin}</td>
            <td>${chip(p.estado)}</td>
            <td class="secundario-txt">${esc(p.motivo ?? '')}</td>
        </tr>`).join('') : '<tr><td colspan="8" class="vacio">La lista de espera está vacía.</td></tr>';

    $('#terminados').innerHTML = e.terminados.length ? e.terminados.slice(0, 15).map((t) => `<li>
            <span><b>#${t.id}</b> ${esc(t.nivelNombre)} · ${t.articulos} art.<div class="secundario-txt">esperó ${fmt(t.esperaMin)} min · ${esc(t.politica)}</div></span>
            <span class="${t.cumplioSla ? 'sla-ok' : 'sla-mal'}">${t.estado === 'COMPLETADO' ? (t.cumplioSla ? 'SLA ✓' : 'SLA ✗') : esc(ESTADOS[t.estado])}</span>
        </li>`).join('') : '<li class="vacio">Aún no hay pedidos terminados.</li>';
}

function dibujarInventario(inv) {
    $('#libresAlmacen').textContent = `${inv.ubicacionesLibres}/${inv.totalUbicaciones} ubicaciones libres`;
    const sel = estadoUI.loteSeleccionado;
    if (sel !== null && !inv.lotes.some((l) => l.id === sel)) estadoUI.loteSeleccionado = null;

    const almacen = $('#almacen');
    almacen.classList.toggle('con-seleccion', estadoUI.loteSeleccionado !== null);
    const porPasillo = inv.niveles * inv.posiciones;
    let html = '';
    for (let p = 0; p < inv.pasillos; p++) {
        let niveles = '';
        for (let n = 0; n < inv.niveles; n++) {
            let celdas = '';
            for (let pos = 0; pos < inv.posiciones; pos++) {
                const c = inv.mapa[p * porPasillo + n * inv.posiciones + pos];
                const codigo = `P${p + 1}-N${n + 1}-${String(pos + 1).padStart(2, '0')}`;
                if (c.lote) {
                    const res = c.lote === estadoUI.loteSeleccionado ? ' resaltada' : '';
                    celdas += `<span class="celda${res}" data-lote="${c.lote}" style="background:${colorProducto(c.producto)}" title="${codigo} · ${c.producto} · lote ${c.lote} · ${c.unidades} u.">${c.unidades}</span>`;
                } else {
                    celdas += `<span class="celda" title="${codigo} · libre"></span>`;
                }
            }
            niveles += `<div class="nivel-fila"><span class="n">N${n + 1}</span>${celdas}</div>`;
        }
        html += `<div class="pasillo"><h3>Pasillo ${p + 1}</h3><div class="niveles">${niveles}</div></div>`;
    }
    almacen.innerHTML = html;

    $('#existencias').innerHTML = inv.productos.map((p) => `<tr>
            <td><span class="mono">${p.codigo}</span><div class="secundario-txt">${esc(p.nombre)}</div></td>
            <td class="secundario-txt">${esc(p.tipo)}</td>
            <td class="der ${p.existencias === 0 ? 'sla-mal' : ''}">${p.existencias}</td>
            <td class="der">${p.pendientes || ''}</td>
        </tr>`).join('');

    $('#lotes').innerHTML = inv.lotes.length ? [...inv.lotes].reverse().slice(0, 60).map((l) => `<li data-lote="${l.id}" class="${l.id === estadoUI.loteSeleccionado ? 'sel' : ''}">
            <span><b>Lote ${l.id}</b> · ${esc(l.nombre)} · ${l.unidades} u. en ${l.ubicaciones.length} ubicación(es)</span>
            <span class="ubic">${esc(l.ubicaciones.join('  '))}</span>
        </li>`).join('') : '<li class="vacio">Sin lotes.</li>';
}

function dibujarRecepcion(r) {
    const celdas = Array.from({ length: r.capacidadBuzon }, (_, i) => `<span class="${i < r.buzon ? 'lleno' : ''}"></span>`).join('');
    const lleno = r.buzon >= r.capacidadBuzon;
    $('#flujoRecepcion').innerHTML = `
        <div class="etapa-flujo">
            <h3>Patio · ${r.patio.length} camión(es)</h3>
            <div class="patio">${r.patio.length ? r.patio.map(esc).join('<br>') : 'Sin camiones esperando'}</div>
        </div>
        <div class="flecha">→</div>
        <div class="etapa-flujo">
            <h3>Recepción (productor)</h3>
            <div class="estado">${esc(r.actividadRecepcion)}</div>
            <div class="buzon ${lleno ? 'saturado' : ''}" title="Buzón ${r.buzon}/${r.capacidadBuzon}">${celdas}</div>
            <div class="secundario-txt">Buzón ${r.buzon}/${r.capacidadBuzon} · enviados <b>${r.avisosEnviados}</b> · veces lleno <b>${r.esperasBuzonLleno}</b></div>
        </div>
        <div class="flecha">→</div>
        <div class="etapa-flujo">
            <h3>Inventario (consumidor)</h3>
            <div class="estado">${esc(r.actividadInventario)}</div>
            <div class="cifra">${r.avisosProcesados}</div>
            <div class="secundario-txt">avisos procesados</div>
        </div>`;
}

function dibujarConflictos(c) {
    const activo = c.conflictos.find((x) => !x.resuelto);
    $('#conflictoActivo').innerHTML = activo ? `<div class="conflicto">
            <h2>Deadlock detectado · conflicto #${activo.id} (minuto ${fmt(activo.detectadoMin)})</h2>
            <p class="explicacion">${esc(activo.explicacion)}</p>
            <div class="tabla-envoltura"><table>
                <thead><tr><th>Operación</th><th>Retiene</th><th>Espera</th><th>Que tiene</th></tr></thead>
                <tbody>${activo.participantes.map((p) => `<tr>
                    <td><b>${esc(p.operacion)}</b></td><td>${esc(p.retiene.join(', '))}</td>
                    <td>${esc(p.espera)}</td><td>${esc(p.esperaDe)}</td></tr>`).join('')}</tbody>
            </table></div>
            <div class="resolver"><b>Elegir quién continúa con los recursos disputados:</b>
                ${activo.participantes.map((p) => `<button class="primario" data-conflicto="${activo.id}" data-ganadora="${esc(p.operacion)}">Continúa ${esc(p.operacion)}</button>`).join('')}
            </div>
            <p class="ayuda">La otra operación será interrumpida: soltará sus recursos y regresará a la lista de espera hasta que la elegida termine.</p>
        </div>` : '';

    $('#operaciones').innerHTML = c.operaciones.length ? c.operaciones.map((o) => `<div class="operacion ${o.estado}">
            <h3>${esc(o.nombre)}</h3>
            ${chip(o.estado)} <span class="secundario-txt">${o.conPrevencion ? 'con prevención' : 'sin prevención'} · intento ${o.intentos}</span>
            <div class="detalle">${esc(o.detalle)}</div>
            <ol>${o.pasos.map((p) => `<li>${esc(p)}</li>`).join('')}</ol>
        </div>`).join('') : '<p class="vacio">No hay operaciones. Usa los botones de arriba para lanzar el escenario.</p>';

    dibujarGrafo(c.grafo);

    $('#historialConflictos').innerHTML = c.conflictos.length ? c.conflictos.map((x) => `<li>
            <span><b>#${x.id}</b> ${esc(x.participantes.map((p) => p.operacion.split(' ')[0]).join(' ↔ '))}<div class="secundario-txt">${esc(x.recursos.join(' + '))}</div></span>
            <span class="${x.resuelto ? 'sla-ok' : 'sla-mal'}">${x.resuelto ? `continuó ${esc(x.ganador.split(' ')[0])}` : 'activo'}</span>
        </li>`).join('') : '<li class="vacio">Sin conflictos detectados.</li>';
}

/** Dibuja el grafo de espera con los nodos en circulo y flechas A -> B. */
function dibujarGrafo(aristas) {
    const cont = $('#grafo');
    if (!aristas.length) {
        cont.innerHTML = '<p class="vacio">Nadie está bloqueado esperando un recurso.</p>';
        return;
    }
    const nodos = [...new Set(aristas.flatMap((a) => [a.espera, a.retiene]))];
    const W = 320, H = 240, R = Math.min(W, H) / 2 - 46, cx = W / 2, cy = H / 2;
    const pos = Object.fromEntries(nodos.map((n, i) => {
        const ang = -Math.PI / 2 + (2 * Math.PI * i) / nodos.length;
        return [n, { x: cx + R * Math.cos(ang), y: cy + R * Math.sin(ang) }];
    }));
    const lineas = aristas.map((a, i) => {
        const p = pos[a.espera], q = pos[a.retiene];
        const dx = q.x - p.x, dy = q.y - p.y, d = Math.hypot(dx, dy) || 1;
        // Curva para que A->B y B->A no se encimen.
        const nx = -dy / d * 18, ny = dx / d * 18;
        const mx = (p.x + q.x) / 2 + nx, my = (p.y + q.y) / 2 + ny;
        const x1 = p.x + dx / d * 22, y1 = p.y + dy / d * 22, x2 = q.x - dx / d * 24, y2 = q.y - dy / d * 24;
        return `<path d="M${x1},${y1} Q${mx},${my} ${x2},${y2}" fill="none" stroke="var(--peligro)" stroke-width="1.8" marker-end="url(#punta)"/>
                <text class="etq-arista" x="${mx}" y="${my - 4}" text-anchor="middle">${esc(a.recurso)}</text>`;
    }).join('');
    const circulos = nodos.map((n) => `<circle cx="${pos[n].x}" cy="${pos[n].y}" r="20" fill="var(--superficie-2)" stroke="var(--texto-2)"/>
            <text x="${pos[n].x}" y="${pos[n].y + 4}" text-anchor="middle">${esc(corto(n))}</text>`).join('');
    cont.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Grafo de espera">
        <defs><marker id="punta" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M0,0 L10,5 L0,10 z" fill="var(--peligro)"/></marker></defs>
        ${lineas}${circulos}</svg>`;
}

function dibujarEstadisticas(e) {
    const actual = e.estadisticas.porPolitica.find((p) => p.politica === e.politica.id);
    $('#kpisEstadisticas').innerHTML = [
        kpi(e.estadisticas.ingresados, 'Pedidos ingresados'),
        kpi(e.estadisticas.completados, 'Completados (todas)'),
        kpi(fmt(actual?.esperaPromedioMin ?? 0), 'Espera prom. (min)'),
        kpi(fmt(actual?.procesoPromedioMin ?? 0), 'Procesamiento prom. (min)'),
        kpi(pct(actual?.cumplimientoSla ?? 0), 'SLA cumplido'),
    ].join('');

    $('#comparacion').innerHTML = e.estadisticas.porPolitica.map((p) => {
        const niveles = Object.values(p.porNivel).map((n) => n.completados
            ? `<td class="der">${fmt(n.esperaPromedioMin)}<div class="secundario-txt ${n.cumplimientoSla >= 0.9 ? 'sla-ok' : 'sla-mal'}">${pct(n.cumplimientoSla)}</div></td>`
            : '<td class="der secundario-txt">—</td>').join('');
        return `<tr class="${p.politica === e.politica.id ? 'actual' : ''}">
            <td>${esc(p.nombre)}</td><td class="der">${p.completados}</td>
            <td class="der">${p.completados ? fmt(p.esperaPromedioMin) : '—'}</td>
            <td class="der">${p.completados ? fmt(p.procesoPromedioMin) : '—'}</td>
            <td class="der">${p.completados ? pct(p.cumplimientoSla) : '—'}</td>${niveles}</tr>`;
    }).join('');

    const prom = actual?.ocupacionPromedio ?? {};
    $('#ocupacion').innerHTML = e.recursos.map((r) => {
        const ahora = e.estadisticas.ocupacionActual[r.tipo] ?? 0;
        const media = prom[r.tipo] ?? 0;
        return `<div class="ocup-fila"><span>${esc(r.nombre)}</span>
            <div class="barra-ocup" title="Actual ${pct(ahora)} · promedio ${pct(media)}"><span style="width:${ahora * 100}%"></span><i style="left:calc(${media * 100}% - 1px)"></i></div>
            <span class="mono der">${pct(media)}</span></div>`;
    }).join('');
}

function dibujarEventos(eventos) {
    $('#eventos').innerHTML = eventos.map((ev) => `<tr>
            <td class="mono">${fmt(ev.minuto)}</td><td class="mono secundario-txt">${esc(ev.hilo)}</td><td>${esc(ev.mensaje)}</td>
        </tr>`).join('');
}

// ---------- Ciclo de actualizacion ----------

let consultando = false;

async function refrescar() {
    if (consultando) return;
    consultando = true;
    try {
        dibujar(await api('GET', '/api/estado'));
    } catch (e) {
        console.error(e);
    } finally {
        consultando = false;
    }
}

(async function iniciar() {
    try {
        await cargarCatalogo();
        await cargarClientes();
    } catch (e) {
        aviso(`No se pudo conectar con el servidor: ${e.message}`, true);
    }
    await refrescar();
    setInterval(refrescar, INTERVALO);
})();
