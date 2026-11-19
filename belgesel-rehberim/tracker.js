(function(){
"use strict";
const KEY="filmlerim_izleme_durumu_v1",OTHER="filmlerim_kendi_linklerim_v1";
function load(k,d){try{return JSON.parse(localStorage.getItem(k)||JSON.stringify(d))}catch(e){return d}}
let status=load(KEY,{}),custom=load(OTHER,[]);
if(!status||Array.isArray(status)||typeof status!=="object")status={};
if(!Array.isArray(custom))custom=[];
const catalog=JSON.parse(document.getElementById("catalog").textContent).items;
function save(){try{localStorage.setItem(KEY,JSON.stringify(status));localStorage.setItem(OTHER,JSON.stringify(custom))}catch(e){alert("Kaydedilemedi. Depolamayı kontrol edin.")}}
function esc(s){return String(s==null?"":s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
const style=document.createElement("style");
style.textContent=".film-marks{display:flex;gap:6px;margin:7px 0 0}.film-marks button{flex:1;border:1px solid #ccdcd7;border-radius:9px;padding:8px 4px;background:white;color:#315d55;font-size:11px;cursor:pointer}.film-marks .yes{background:#def5e8;border-color:#83b99e}.film-marks .no{background:#fff0d5;border-color:#debf7d}.film-panel{display:none;position:fixed;inset:0;background:#102b27a8;z-index:120;align-items:flex-end;justify-content:center}.film-panel.show{display:flex}.film-sheet{width:100%;max-width:700px;height:92dvh;background:#f6faf8;border-radius:16px 16px 0 0;display:flex;flex-direction:column;overflow:hidden}.film-head{background:white;padding:14px;border-bottom:1px solid #dee9e5}.film-head h2{font-size:19px;margin:0}.film-head .top{display:flex;justify-content:space-between;align-items:center}.film-head button{border:0;background:#e3f1eb;border-radius:8px;padding:9px 12px}.film-head input,.film-head select{width:100%;padding:10px;border:1px solid #d4e3de;border-radius:9px;font-size:14px;margin-top:7px}.film-scroll{overflow:auto;flex:1;padding:12px}.film-form{padding:11px;background:white;border:1px solid #dce8e2;border-radius:12px;margin-bottom:12px}.film-form summary{font-weight:700;color:#145f55;font-size:13px;cursor:pointer}.film-form form{display:grid;gap:8px;margin-top:10px}.film-form input,.film-form select{width:100%;border:1px solid #d8e6df;border-radius:8px;padding:10px;font-size:14px}.film-form button{padding:10px;border:0;border-radius:8px;color:#fff;background:#135e5a;font-weight:700}.film-row{padding:11px;margin:8px 0;background:white;border:1px solid #dbe7e1;border-radius:11px}.film-row strong{font-size:13px;display:block}.film-row small{color:#678078}.film-row nav{margin-top:8px;display:flex;gap:6px;align-items:center}.film-row button{padding:8px 5px;border:1px solid #d4e6db;border-radius:8px;background:white;font-size:11px}.film-row button.on{background:#dff4e8;color:#176346}.film-row a{margin-left:auto;background:#145f55;color:white;text-decoration:none;border-radius:8px;padding:9px;font-size:11px;white-space:nowrap}.film-sites{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}.film-sites a{background:#fff;color:#135e5a;border:1px solid #d4e3dc;border-radius:8px;padding:8px;text-decoration:none;font-size:11px}.film-empty{font-size:13px;color:#789086;padding:20px;text-align:center}@media(min-width:750px){.film-panel{align-items:center}.film-sheet{height:85dvh;border-radius:16px}}";
document.head.appendChild(style);
const nav=document.querySelector(".bottom");
if(!nav)return;
const toggle=document.createElement("button");toggle.id="film-takip-open";toggle.type="button";toggle.textContent="📋 Takip";
nav.insertBefore(toggle,nav.children[1]||null);
const panel=document.createElement("div");panel.className="film-panel";panel.id="film-takip-panel";panel.setAttribute("aria-hidden","true");
panel.innerHTML='<div class="film-sheet" role="dialog" aria-modal="true" aria-label="İzleme takip listesi"><div class="film-head"><div class="top"><h2>📋 İzleme Takip Listem</h2><button id="film-close" type="button">✕</button></div><small>İzlediğin ve izlemediğin içerikler bu cihazda saklanır.</small><input id="film-search" placeholder="Takip listemde ara…" aria-label="Takip listesinde ara"><select id="film-filter"><option value="all">Tüm takip listem</option><option value="todo">İzlemediklerim</option><option value="watched">İzlediklerim</option></select></div><div class="film-scroll"><details class="film-form" id="film-form-panel"><summary>➕ Belgesel bağlantısı ekle</summary><form id="film-add"><input id="film-name" required maxlength="150" placeholder="Belgeselin adı"><input id="film-url" required type="url" placeholder="https://... içerik sayfası"><input id="film-type" type="hidden" value="Belgesel"><button type="submit">Takip listeme ekle</button></form></details><div class="film-sites"><a href="https://www.hdfilmcehennemi.nl/tur/belgesel-filmlerini-izle-2/" target="_blank" rel="noopener noreferrer">HDFilmCehennemi · Belgesel ↗</a><a href="https://www.fullhdfilmizlesene.now/filmizle/belgesel-filmleri" target="_blank" rel="noopener noreferrer">FullHDFilmizlesene · Belgesel ↗</a></div><div id="film-count" style="font-size:12px;color:#75867f"></div><div id="film-rows"></div></div></div>';
document.body.appendChild(panel);
const $=id=>document.getElementById(id);
let visible=false;
function draw(){
const q=$("film-search").value.toLocaleLowerCase("tr-TR").trim(),f=$("film-filter").value;
const all=[...custom.filter(x=>x.type!=="Film"&&x.type!=="Dizi"),...catalog],tracked=all.filter(x=>["todo","watched"].includes(status[x.id]));
const entries=tracked.filter(x=>(f==="all"||status[x.id]===f)&&(!q||(x.title+" "+x.source).toLocaleLowerCase("tr-TR").includes(q)));
$("film-count").textContent=tracked.length+" takip kaydı · "+tracked.filter(x=>status[x.id]==="watched").length+" izlendi · "+tracked.filter(x=>status[x.id]==="todo").length+" izlenmedi";
$("film-rows").innerHTML=entries.map(x=>'<div class="film-row"><strong>'+esc(x.title)+'</strong><small>'+esc(x.type||"Belgesel")+' · '+esc(x.source)+'</small><nav><button data-id="'+esc(x.id)+'" data-state="todo" class="'+(status[x.id]==="todo"?"on":"")+'">○ İzlemedim</button><button data-id="'+esc(x.id)+'" data-state="watched" class="'+(status[x.id]==="watched"?"on":"")+'">✓ İzledim</button>'+(x.custom?'<button data-remove="'+esc(x.id)+'">Sil</button>':'')+'<a target="_blank" rel="noopener noreferrer" href="'+esc(x.url)+'">▶ İzle</a></nav></div>').join("")||'<div class="film-empty">Henüz bu filtrede kayıt yok. Keşfet ekranında bir belgeseli işaretle veya bağlantı ekle.</div>';
}
function close(){visible=false;panel.classList.remove("show");panel.setAttribute("aria-hidden","true");document.body.style.overflow=""}
toggle.addEventListener("click",()=>{visible=true;panel.classList.add("show");panel.setAttribute("aria-hidden","false");document.body.style.overflow="hidden";draw();$("film-close").focus()});
$("film-close").addEventListener("click",close);panel.addEventListener("click",e=>{if(e.target===panel)close()});
document.addEventListener("keydown",e=>{if(e.key==="Escape"&&visible)close()});
function mark(id,val){status[id]=status[id]===val?"":val;save();decorate();if(visible)draw()}
function decorate(){
document.querySelectorAll("#cards article.card").forEach(card=>{
const b=card.querySelector(".heart[data-id]");if(!b)return;
const id=b.dataset.id,where=card.querySelector(".content");if(!where)return;
let el=card.querySelector(".film-marks");if(!el){el=document.createElement("div");el.className="film-marks";where.appendChild(el)}
el.innerHTML='<button type="button" data-item="'+esc(id)+'" data-state="todo" class="'+(status[id]==="todo"?"no":"")+'">○ İzlemedim</button><button type="button" data-item="'+esc(id)+'" data-state="watched" class="'+(status[id]==="watched"?"yes":"")+'">✓ İzledim</button>';
});
}
const cards=$("cards");
if(cards){cards.addEventListener("click",e=>{const b=e.target.closest("button[data-item]");if(b)mark(b.dataset.item,b.dataset.state)});new MutationObserver(decorate).observe(cards,{childList:true});decorate()}
$("film-rows").addEventListener("click",e=>{
const b=e.target.closest("button");if(!b)return;
if(b.dataset.id)mark(b.dataset.id,b.dataset.state);
else if(b.dataset.remove){if(!confirm("Kendi bağlantını kaldırmak istiyor musun?"))return;custom=custom.filter(x=>x.id!==b.dataset.remove);delete status[b.dataset.remove];save();draw()}
});
$("film-search").addEventListener("input",draw);$("film-filter").addEventListener("change",draw);
$("film-add").addEventListener("submit",e=>{
e.preventDefault();const name=$("film-name").value.trim(),raw=$("film-url").value.trim(),type=$("film-type").value;let u;
try{u=new URL(raw)}catch(err){alert("Geçerli adres gir.");return}
if(!["http:","https:"].includes(u.protocol))return alert("Web bağlantısı gir.");
if(!name)return;
const existing=[...custom,...catalog].find(x=>x.url===u.href);
if(existing)status[existing.id]="todo";
else {const item={id:"kendi-"+Date.now()+"-"+Math.random().toString(36).slice(2,7),title:name,url:u.href,type,source:"Kendi bağlantılarım",custom:true};custom.unshift(item);status[item.id]="todo"}
save();$("film-add").reset();$("film-form-panel").open=false;$("film-filter").value="all";$("film-search").value="";draw();
});
})();