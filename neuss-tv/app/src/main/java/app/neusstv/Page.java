package app.neusstv;

/** The page a phone on the same Wi-Fi gets at http://&lt;tv-ip&gt;:8080 — write, draw, or send a photo. */
final class Page {
    private Page() {
    }

    static final String HTML = """
            <!doctype html><html dir=rtl lang=ar><head><meta charset=utf-8>
            <meta name=viewport content='width=device-width,initial-scale=1,user-scalable=no'>
            <title>Neuss TV</title><style>
            :root{color-scheme:dark}
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0d1117;font-family:-apple-system,system-ui,sans-serif;color:#fff}
            .c{width:min(94vw,560px);padding:18px 0}
            h1{font-size:22px;margin:0 0 14px;text-align:center;letter-spacing:.5px}
            .tabs{display:flex;gap:8px;margin-bottom:12px}
            .tabs button{flex:1;margin:0;padding:12px 0;font-size:16px;background:#161b22;border:1px solid #30363d;color:#fff}
            .tabs button.on{background:#1f6feb;border-color:#1f6feb}
            .sw{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px;padding:14px 16px;
            background:#161b22;border:1px solid #30363d;border-radius:16px;font-size:17px}
            .sw input{display:none}
            .sw i{flex:none;width:52px;height:30px;border-radius:15px;background:#30363d;position:relative;transition:background .15s}
            .sw i:after{content:'';position:absolute;top:3px;left:3px;width:24px;height:24px;border-radius:50%;background:#fff;transition:transform .15s}
            .sw input:checked + i{background:#1f6feb}
            .sw input:checked + i:after{transform:translateX(22px)}
            .adv{margin-top:14px;background:#161b22;border:1px solid #30363d;border-radius:16px;padding:14px 16px}
            .adv summary{font-size:16px;cursor:pointer}
            .adv textarea{height:92px;font-size:16px;margin-top:12px;direction:ltr;text-align:left}
            .hint{font-size:14px;opacity:.7;margin-top:8px}
            .inp{position:relative}
            
            .x{position:absolute;top:50%;left:auto;right:8px;transform:translateY(-50%);margin:0;padding:0;width:38px;height:38px;
            border-radius:50%;background:transparent;color:#f87171;font-size:22px;font-weight:700;line-height:1;border:0}
            .x:active{background:#2a1416}
            input[type=url]{direction:ltr;text-align:left;width:100%;box-sizing:border-box;font-size:20px;padding:16px 54px 16px 16px;border-radius:16px;border:1px solid #30363d;background:#161b22;color:#fff}
            textarea{width:100%;box-sizing:border-box;height:140px;font-size:22px;padding:14px;border-radius:16px;border:1px solid #30363d;background:#161b22;color:#fff;resize:none}
            canvas{width:100%;aspect-ratio:16/9;background:#161b22;border-radius:16px;border:1px solid #30363d;touch-action:none;display:block}
            img#pv{width:100%;border-radius:16px;border:1px solid #30363d;display:none;margin-bottom:10px}
            label.file{display:block;padding:28px 14px;text-align:center;border:1px dashed #30363d;border-radius:16px;background:#161b22;font-size:17px}
            input[type=file]{display:none}
            .row{display:flex;gap:10px;margin-top:12px;align-items:center}
            .dot{width:38px;height:38px;border-radius:50%;border:3px solid transparent}
            .dot.on{border-color:#fff}
            button{margin-top:14px;width:100%;padding:16px;font-size:20px;font-weight:700;border:0;border-radius:16px;background:#1f6feb;color:#fff}
            .row button{margin:0;width:auto;flex:1;background:#30363d}
            p{opacity:.7;text-align:center;min-height:1.4em;margin:12px 0 0}
            .hide{display:none}
            </style></head><body><div class=c>
            <h1>📺 Neuss TV</h1>
            <div class=tabs id=modes>
              <button id=mf class=on onclick=mode(0)>🖤 شاشة سودا</button>
              <button id=mo onclick=mode(1)>🎬 فوق اللي شغّال</button>
            </div>
            <div class=tabs>
              <button id=bw class=on onclick=tab(0)>✍️ اكتب</button>
              <button id=bd onclick=tab(1)>🎨 ارسم</button>
              <button id=bi onclick=tab(2)>🖼️ ميديا</button>
              <button id=bl onclick=tab(3)>🌐 رابط</button>
            </div>
            <div id=w><textarea id=t maxlength=200 placeholder='اكتب هني...'></textarea>
              <label class=sw><span>🔊 اقرأها بصوت عالي</span><input id=say type=checkbox><i></i></label>
              <button onclick=send()>أرسل للتلفزيون</button>
              <div class=row><button onclick=clearAll()>شيل اللي على الشاشة</button></div></div>
            <div id=d class=hide><canvas id=cv></canvas><div class=row id=colors></div><div class=row><button onclick=clr()>امسح</button></div></div>
            <div id=im class=hide>
              <img id=pv>
              <label class=file for=f id=fl>📷 اختار صورة أو فيديو من جوالك</label>
              <input id=f type=file accept='image/*,video/*'>
              <button onclick=sendImg()>اعرضه على التلفزيون</button>
              <div class=row><button onclick=clearAll()>شيلها من الشاشة</button></div>
            </div>
            <div id=lk class=hide>
              <div class=inp><input id=url type=url inputmode=url dir=ltr spellcheck=false autocapitalize=off autocorrect=off placeholder='example.com'><button class=x onclick=clearUrl() aria-label='امسح الرابط'>✕</button></div>
              <button onclick=sendUrl()>افتحه على التلفزيون</button>
              <label class=sw><span>⭐ يفتح لحاله عند تشغيل التلفزيون</span><input id=auto type=checkbox onchange=toggleAuto()><i></i></label>
              <details class=adv><summary>🔑 توكن / هيدر للصفحات اللي بدها تسجيل دخول</summary>
                <textarea id=hdr dir=ltr spellcheck=false autocapitalize=off placeholder='Authorization: Bearer xxxxxxxx'></textarea>
                <div id=hs class=hint></div>
                <div class=row><button onclick=saveHdr()>احفظ التوكن</button><button onclick=clearHdr()>شيله</button></div>
              </details>
              <div class=row><button onclick=clearAll()>سكّر الصفحة</button></div>
            </div>
            <p id=s></p></div>
            <script>
            function $(i){return document.getElementById(i)}
            function tab(n){$('w').className=n==0?'':'hide';$('d').className=n==1?'':'hide';$('im').className=n==2?'':'hide';$('lk').className=n==3?'':'hide';
            $('bw').className=n==0?'on':'';$('bd').className=n==1?'on':'';$('bi').className=n==2?'on':'';$('bl').className=n==3?'on':'';
            $('modes').className=n==3?'tabs hide':'tabs';try{localStorage.setItem('neussTab',n)}catch(e){}if(n==1)fit()}
            function say(m){$('s').textContent=m}
            var over=0;
            function mode(n){over=n;$('mf').className=n?'':'on';$('mo').className=n?'on':'';
            say(n?'رح يطلع فوق اللي شغّال':'رح ياخذ الشاشة كلها')}
            function u(p){return over?p+'?over=1':p}
            function send(){var t=$('t');if(!t.value.trim())return;var p=u('/send');
            if($('say').checked)p+=(p.indexOf('?')<0?'?':'&')+'say=1';fetch(p,{method:'POST',body:t.value})
            .then(function(){say('وصلت ✅');t.value=''}).catch(function(){say('ما وصلت، جرّب مرة ثانية')})}
            function sendUrl(){var v=$('url').value.trim();if(!v){say('اكتب الرابط أول');return}say('عمّال أفتحه...');
            fetch('/url',{method:'POST',body:v}).then(function(){say('الصفحة فتحت على التلفزيون ✅');
            if($('auto').checked)fetch('/startup',{method:'POST',body:v})}).catch(function(){say('ما وصلت، جرّب مرة ثانية')})}
            function saveHdr(){var v=$('hdr').value.trim();
            fetch('/headers',{method:'POST',body:v}).then(function(){$('hdr').value='';say(v?'التوكن انحفظ 🔑':'شلت التوكن');hdrState()})
            .catch(function(){say('ما وصلت، جرّب مرة ثانية')})}
            function clearHdr(){$('hdr').value='';fetch('/headers',{method:'POST',body:''}).then(function(){say('شلت التوكن');hdrState()})}
            function hdrState(){fetch('/status').then(function(r){return r.json()}).then(function(j){
            $('hs').textContent=(j.headers&&j.headers.length)?('محفوظ: '+j.headers.join('، ')+' (القيمة ما بتنعرض)'):'ما في توكن محفوظ'}).catch(function(){})}
            function clearUrl(){$('url').value='';$('url').focus();say('')}
            function toggleAuto(){var on=$('auto').checked,v=$('url').value.trim();
            if(on&&!v){$('auto').checked=false;say('اكتب الرابط أول');return}
            fetch('/startup',{method:'POST',body:on?v:''}).then(function(){say(on?'رح يفتح لحاله كل ما يشتغل التلفزيون ⭐':'وقّفت الفتح التلقائي')})
            .catch(function(){$('auto').checked=!on;say('ما وصلت، جرّب مرة ثانية')})}
            function resetFields(){$('url').value=$('auto').checked?$('url').value:'';
            $('f').value='';$('pv').removeAttribute('src');$('pv').style.display='none';$('fl').textContent='📷 اختار صورة أو فيديو من جوالك'}
            function clearAll(){fetch('/clear',{method:'POST',body:''}).then(function(){resetFields();say('انمسحت من الشاشة')})}
            fetch('/status').then(function(r){return r.json()}).then(function(j){
            if(j.startup){$('url').value=j.startup;$('auto').checked=true}
            $('hs').textContent=(j.headers&&j.headers.length)?('محفوظ: '+j.headers.join('، ')+' (القيمة ما بتنعرض)'):'ما في توكن محفوظ'}).catch(function(){});
            $('f').onchange=function(){var f=this.files[0];if(!f)return;
            var vid=(f.type||'').indexOf('video')===0;
            if(vid){$('pv').style.display='none'}else{$('pv').src=URL.createObjectURL(f);$('pv').style.display='block'}
            $('fl').textContent=(vid?'🎬 ':'📷 ')+f.name;say('')}
            function sendImg(){var f=$('f').files[0];if(!f){say('اختار صورة أو فيديو أول');return}
            var vid=(f.type||'').indexOf('video')===0;say(vid?'عمّال أرفع الفيديو... (ممكن تاخذ شوي)':'عمّال أرسل...');
            fetch(vid?'/video':u('/image'),{method:'POST',body:f})
            .then(function(){say(vid?'الفيديو شغّال على التلفزيون ✅':'الصورة على التلفزيون ✅')})
            .catch(function(){say('ما وصلت، جرّب مرة ثانية')})}
            var cv=$('cv'),cx=cv.getContext('2d'),col='#ffffff',q=[],last=null,cont=false;
            ['#ffffff','#f5c542','#4ade80','#f87171','#60a5fa'].forEach(function(c,i){var b=document.createElement('div');
            b.className='dot'+(i?'':' on');b.style.background=c;b.onclick=function(){col=c;
            Array.prototype.forEach.call(document.querySelectorAll('.dot'),function(x){x.className='dot'});b.className='dot on'};$('colors').appendChild(b)});
            function fit(){var r=cv.getBoundingClientRect();cv.width=r.width*2;cv.height=r.height*2}
            function pt(e){var r=cv.getBoundingClientRect();return[(e.clientX-r.left)/r.width,(e.clientY-r.top)/r.height]}
            function line(a,b){cx.strokeStyle=col;cx.lineWidth=6;cx.lineCap='round';cx.beginPath();
            cx.moveTo(a[0]*cv.width,a[1]*cv.height);cx.lineTo(b[0]*cv.width,b[1]*cv.height);cx.stroke()}
            cv.onpointerdown=function(e){cv.setPointerCapture(e.pointerId);last=pt(e);cont=false;q=[last]};
            cv.onpointermove=function(e){if(!last)return;var p=pt(e);line(last,p);last=p;q.push(p)};
            cv.onpointerup=cv.onpointercancel=function(){flush();last=null};
            function flush(){if(q.length<1)return;var s=(cont?'+':'')+col+' '+q.map(function(p){return p[0].toFixed(4)+','+p[1].toFixed(4)}).join(' ');
            if(last){q=[last];cont=true}else q=[];fetch(u('/draw'),{method:'POST',body:s})}
            setInterval(function(){if(last&&q.length>1)flush()},80);
            function clr(){cx.clearRect(0,0,cv.width,cv.height);fetch('/clear',{method:'POST',body:''})}
            (function(){var n=0;try{n=parseInt(localStorage.getItem('neussTab'),10)}catch(e){}
            if(!(n>=0&&n<=3))n=0;tab(n)})();
            </script></body></html>
            """;
}
