import {api} from '/js/shared/api-client.js';
import {escapeHtml as escape} from '/js/shared/view-helpers.js';
import {enhanceEditor,syncStudioStatus,revealInvalid} from '/invitation-ux.js';
import {enhanceAddressSearch} from '/invitation-address.js';
import {FreeDesigner,designMarkup} from '/invitation-designer.js';
import {accountControls,collectAccounts,accountMarkup,copyAccount} from '/invitation-accounts.js';
import {newspaperControls,collectNewspaper,syncNewspaperControls,newspaperMarkup,newspaperAction,fitNewspaper} from '/invitation-newspaper.js';
const $=s=>document.querySelector(s), root=$('#studio');
const templates=[['CLASSIC','01','클래식 레터','기본 디자인 · 차분한 종이 위에 담는 약속'],['GARDEN','02','그린 가든','싱그러운 정원처럼 다정하게'],['PHOTO','03','포토 스토리','우리의 가장 아름다운 순간'],['NEWSPAPER','04','웨딩 타임즈','추가 선택 · 피그마 신문형 디자인']];
const blank=()=>({template:'CLASSIC',groom:'',bride:'',groomParents:'',brideParents:'',greeting:'서로의 하루에 가장 다정한 사람이 되겠습니다.\n저희의 새로운 시작에 함께해 주세요.',dateTime:'',venue:'',address:'',transport:'',groomPhone:'',bridePhone:'',cover:'',gallery:[]});
let data=blank(), card=null, dirty=false, busy=false, photoBusy=false;
const selfUrl=()=>publicToken?location.origin+'/invitation.html?card='+publicToken:card?location.origin+'/invitation.html?card='+card.token:'';
const designer=new FreeDesigner(()=>data,selfUrl,dragging=>{dirty=true;if(!dragging)preview();status();});
const publicToken=new URLSearchParams(location.search).get('card');
let noticeTimer;
function notice(message,error=false){clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>{document.querySelector('#notice').classList.remove('visible');},error?8000:4000);$('#notice').textContent=message;$('#notice').className=error?'error visible':'visible';}
function dateParts(value){const d=new Date(value+'+09:00');if(Number.isNaN(d.getTime()))return {full:'소중한 날을 기다립니다',month:'OUR WEDDING DAY',day:'♡'};return {full:new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'long',day:'numeric',weekday:'long',hour:'numeric',minute:'2-digit'}).format(d),month:new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Seoul',month:'long',year:'numeric'}).format(d),day:new Intl.DateTimeFormat('en-US',{timeZone:'Asia/Seoul',day:'numeric'}).format(d)};}
function image(src,alt,cls=''){return src?`<img class="${cls}" src="${escape(src)}" alt="${alt}" loading="lazy">`:'';}
function cardMarkup(d){
  const date=dateParts(d.dateTime),g=escape(d.groom||'신랑'),b=escape(d.bride||'신부');
  const phone=(value,name)=>value?`<a class="contact" href="tel:${escape(value.replace(/[ -]/g,''))}">${name}에게 연락하기 ↗</a>`:'';
  let markup=`<article class="wedding-card theme-${d.template.toLowerCase()} font-${d.design?.font||'SERIF'}"><section class="card-cover"><div class="cover-kicker">TOGETHER, ALWAYS</div><div class="cover-ornament" aria-hidden="true">${d.template==='GARDEN'?'❧':'✳'}</div><p class="script">We're getting married</p><h1>${g}<span>&amp;</span>${b}</h1>${d.cover?image(d.cover,'신랑 신부 대표 사진','cover-photo'):'<div class="photo-placeholder" aria-hidden="true"><span>♡</span><p>OUR BEAUTIFUL MOMENT</p></div>'}<p class="cover-date">${escape(date.full)}</p><p>${escape(d.venue||'우리의 약속이 시작되는 곳')}</p></section><section class="card-section"><span class="section-caption">INVITATION</span><h2>마음을 담아 초대합니다</h2><p class="poem">${escape(d.greeting)}</p><div class="family">${d.groomParents?`<p>${escape(d.groomParents)}</p>`:''}<p>신랑 <strong>${g}</strong></p>${d.brideParents?`<p>${escape(d.brideParents)}</p>`:''}<p>신부 <strong>${b}</strong></p></div>${phone(d.groomPhone,'신랑')}${phone(d.bridePhone,'신부')}</section>${d.gallery?.length?`<section class="card-section"><span class="section-caption">OUR MOMENTS</span><h2>함께한 순간들</h2><div class="photo-grid">${d.gallery.map((p,i)=>`<button type="button" class="gallery-photo" data-act="photo" data-index="${i}" aria-label="우리의 추억 사진 ${i+1} 크게 보기">${image(p,`우리의 추억 사진 ${i+1}`)}</button>`).join('')}</div></section>`:''}<section class="card-section date-section"><span class="section-caption">SAVE THE DATE</span><p class="calendar-month">${escape(date.month)}</p><div class="calendar-day">${escape(date.day)}</div><p>${escape(date.full)}</p>${d.dateTime?'<button class="card-button" type="button" data-act="calendar">내 캘린더에 저장 ＋</button>':''}</section><section class="card-section"><span class="section-caption">LOCATION</span><h2>오시는 길</h2><h3>${escape(d.venue||'예식장 이름')}</h3><p>${escape(d.address||'예식장 주소를 입력해 주세요')}</p>${d.address?`<a class="card-button" href="https://map.kakao.com/?q=${encodeURIComponent(d.address+' '+d.venue)}" target="_blank" rel="noopener noreferrer">카카오맵에서 보기 ↗</a><a class="card-button" href="https://map.naver.com/p/search/${encodeURIComponent(d.address+' '+d.venue)}" target="_blank" rel="noopener noreferrer">네이버지도에서 보기 ↗</a>`:''}${d.transport?`<div class="transport"><h3>교통·주차 안내</h3><p class="poem">${escape(d.transport)}</p></div>`:''}</section><footer class="card-footer"><span aria-hidden="true">♡</span><p>우리의 시작을 함께해 주셔서 감사합니다.</p><small>MADE WITH LOVE · 청모</small></footer></article>`;
  if(d.template==='NEWSPAPER')markup=markup.replace(/<section class="card-cover">[\s\S]*?<\/section>/,`<h1 class="sr-only">${g} · ${b}의 결혼식에 초대합니다</h1>`+newspaperMarkup(d));
  else if(d.design?.enabled)markup=markup.replace(/<section class="card-cover">[\s\S]*?<\/section>/,designMarkup(d,selfUrl(),!publicToken,designer.selected));
  if(publicToken&&d.design?.enabled&&d.template!=='NEWSPAPER')markup=markup.replace('<svg class="free-design-canvas"',`<h1 class="sr-only">${g} · ${b}의 결혼식에 초대합니다</h1><svg class="free-design-canvas"`);
  return markup.replace('<footer class="card-footer">',accountMarkup(d)+'<footer class="card-footer">');
}
function preview(){const scroll=$('#preview').scrollTop;$('#preview').innerHTML=cardMarkup(data);$('#preview').scrollTop=scroll;$('#selected-theme').textContent=templates.find(t=>t[0]===data.template)[2];fitNewspaper();}
function input(name,label,max=120,type='text',required=false){return `<label>${label}<input name="${name}" type="${type}" maxlength="${max}" ${required?'required':''} value="${escape(data[name])}"></label>`;}
function editor(){root.innerHTML=`<section class="studio-intro"><span class="eyebrow">A LITTLE LETTER, A BIG BEGINNING</span><h1>우리의 이야기가<br>청첩장이 되는 순간.</h1><p>디자인을 고르고, 마음을 담고, 링크로 전하세요.</p></section><div class="studio-layout"><section class="editor-panel"><div class="collection-head"><h2>나의 청첩장</h2><button type="button" class="secondary" data-act="new">새로 만들기 ＋</button></div><div id="collection" class="collection"></div><form id="editor-form"><fieldset><legend><span>01</span> 디자인 선택</legend><p class="hint">새 청첩장은 클래식 레터로 시작해요. 피그마 신문형 디자인은 웨딩 타임즈를 선택하면 적용됩니다.</p><div class="templates">${templates.map(([id,num,name,desc])=>`<label class="template-choice"><input type="radio" name="template" value="${id}" ${data.template===id?'checked':''}><span class="mini-card ${id.toLowerCase()}"><small>${num} / INVITATION</small><span>Our<br>Wedding</span><i>✳</i></span><strong>${name}</strong><small>${desc}</small></label>`).join('')}</div>${newspaperControls(data)}</fieldset><fieldset><legend><span>02</span> 두 사람과 초대의 글</legend><div class="field-row">${input('groom','신랑 이름',30,'text',true)}${input('bride','신부 이름',30,'text',true)}</div>${input('groomParents','신랑 부모님 소개 · 선택',120)}${input('brideParents','신부 부모님 소개 · 선택',120)}<label>초대의 글<textarea name="greeting" rows="5" maxlength="1500" required>${escape(data.greeting)}</textarea></label><div class="field-row">${input('groomPhone','신랑 연락처 · 선택',25,'tel')}${input('bridePhone','신부 연락처 · 선택',25,'tel')}</div><p class="hint">연락처는 공유하고 싶은 경우에만 입력하세요.</p></fieldset><fieldset><legend><span>03</span> 예식 안내</legend>${input('dateTime','예식 날짜와 시간 · 한국 시간',25,'datetime-local',true)}${input('venue','예식장 · 홀 이름',120,'text',true)}${input('address','예식장 주소',200,'text',true)}<label>교통·주차 안내 · 선택<textarea name="transport" rows="3" maxlength="1000">${escape(data.transport)}</textarea></label></fieldset><fieldset><legend><span>04</span> 우리의 사진</legend><label class="upload-label">대표 사진<input id="cover-file" type="file" accept="image/jpeg,image/png"></label><div id="cover-thumb" class="thumbs"></div><label class="upload-label">갤러리 사진 · 최대 6장<input id="gallery-files" type="file" accept="image/jpeg,image/png" multiple></label><div id="gallery-thumbs" class="thumbs"></div><p class="hint">JPEG·PNG를 선택하면 모바일용 크기로 줄여 저장해요. 원본은 변경하지 않습니다.</p></fieldset>${designer.controls()}${accountControls(data)}<div class="save-controls"><p id="save-status" role="status"></p><button type="submit">청첩장 저장</button><button class="secondary" type="button" data-act="publish">저장하고 발행하기 ↗</button><button class="secondary" type="button" data-act="unpublish" hidden>발행 취소</button><p class="hint">발행한 청첩장은 링크를 가진 누구나 볼 수 있어요. 청모 참여용 초대코드와는 별개의 링크입니다.</p><div id="share-controls" hidden><label>청첩장 공유 링크<input id="share-url" readonly></label><div class="actions"><button type="button" class="secondary" data-act="copy">링크 복사</button><button type="button" class="secondary" data-act="share">공유하기</button><a id="public-link" target="_blank" rel="noopener">공개 화면 보기 ↗</a></div></div><p id="form-error" role="alert"></p></div></form></section><aside class="preview-panel"><div class="preview-heading"><span>LIVE PREVIEW</span><strong id="selected-theme"></strong><span>모바일 화면</span></div><div class="phone-frame" id="preview"></div></aside></div>`; preview();thumbs();status();enhanceEditor({isNew:!card,selected:()=>designer.selected});enhanceAddressSearch();status();loadCollection().catch(e=>notice(e.message,true));}
function status(){syncStudioStatus({dirty,card,data});
  $('#save-status').textContent=dirty?'● 아직 저장하지 않은 변경이 있어요.':card?card.published?'● 발행 중 · 수정 후 저장하면 공개 화면에 반영돼요.':'✓ 저장됨 · 아직 비공개예요.':'새 청첩장 · 정보를 입력하고 저장해 주세요.';
  $('[data-act="unpublish"]').hidden=!card?.published;
  $('#share-controls').hidden=!card?.published;
  if(card){const link=location.origin+'/invitation.html?card='+card.token;$('#share-url').value=link;$('#public-link').href=link;}
}
async function loadCollection(){const list=await api('/invitations');$('#collection').innerHTML=list.length?list.map(c=>`<button type="button" class="collection-card ${card?.id===c.id?'active':''}" data-act="open" data-id="${escape(c.id)}">${escape(c.groom)} &amp; ${escape(c.bride)} <small>${c.published?'발행 중':'비공개'}</small></button>`).join(''):'<p class="hint">첫 번째 청첩장을 만들어 보세요.</p>';}
function thumbs(){
  $('#cover-thumb').innerHTML=data.cover?image(data.cover,'선택한 대표 사진')+'<button type="button" class="secondary" data-act="remove-cover">대표 사진 빼기</button>':'';
  $('#gallery-thumbs').innerHTML=data.gallery.map((src,i)=>`<div>${image(src,`갤러리 사진 ${i+1}`)}<button class="secondary" type="button" data-act="remove-photo" data-index="${i}" aria-label="갤러리 사진 ${i+1} 삭제">빼기</button></div>`).join('');
}
function collect(){const fields=Object.fromEntries(new FormData($('#editor-form')));Object.keys(blank()).filter(k=>!['cover','gallery'].includes(k)).forEach(k=>data[k]=String(fields[k]??'').trim());collectAccounts(data);collectNewspaper(data);syncNewspaperControls(data);}
async function compress(file){
  if(!['image/jpeg','image/png'].includes(file.type)||file.size>20*1024*1024)throw new Error('20MB 이하의 JPEG·PNG 사진을 선택해 주세요.');
  const bitmap=await createImageBitmap(file);
  try {const scale=Math.min(1,1600/Math.max(bitmap.width,bitmap.height)),canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));const ctx=canvas.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);for(const q of [.85,.7,.55,.4]){const src=canvas.toDataURL('image/jpeg',q);if(src.length<680000)return src;}throw new Error('사진 용량이 커요. 더 작은 사진을 선택해 주세요.');} finally {bitmap.close();}
}
async function save(){
  if(photoBusy)throw new Error('사진을 처리하고 있어요. 잠시 후 저장해 주세요.');
  const form=$('#editor-form');const invalid=form.querySelector('input:invalid,textarea:invalid,select:invalid');
  if(invalid){root.inert=false;revealInvalid(invalid);invalid.reportValidity();return false;}collect();
  card=await api(card?'/invitations/'+card.id:'/invitations',{method:card?'PUT':'POST',body:card?{version:card.version,data}:data});data=card.data;dirty=false;preview();status();await loadCollection();return true;
}
root.addEventListener('input',e=>{if(designer.input(e))return;if(e.target.closest('#editor-form')&&!['file'].includes(e.target.type)){collect();dirty=true;preview();status();}});
root.addEventListener('change',async e=>{
  if(!['cover-file','gallery-files'].includes(e.target.id))return;
  photoBusy=true;notice('사진을 준비하고 있어요…');
  try {const files=[...e.target.files];if(e.target.id==='gallery-files'&&data.gallery.length+files.length>6)throw new Error('갤러리는 최대 6장까지 담을 수 있어요.');const photos=[];for(const file of files)photos.push(await compress(file));if(e.target.id==='cover-file'){if(photos[0])data.cover=photos[0];}else data.gallery.push(...photos);dirty=true;thumbs();preview();status();notice('사진을 추가했어요. 저장하면 청첩장에 반영됩니다.');}catch(err){notice(err.message,true);}finally{photoBusy=false;e.target.value='';}
});
root.addEventListener('submit',async e=>{if(e.target.id!=='editor-form')return;e.preventDefault();await operation(async()=>{if(await save())notice('청첩장을 저장했어요.');});});
async function operation(fn){if(busy)return;busy=true;root.inert=true;root.setAttribute('aria-busy','true');try{$('#form-error')&&($('#form-error').textContent='');await fn();}catch(e){if($('#form-error'))$('#form-error').textContent=e.message;notice(e.message,true);}finally{busy=false;root.inert=false;root.setAttribute('aria-busy','false');}}
root.addEventListener('click',async e=>{
  try{if(designer.action(e))return;}catch(error){notice(error.message,true);return;}
  const button=e.target.closest('[data-act]');if(!button)return;const act=button.dataset.act;
  if(newspaperAction(button)){
    const focusSelector=button.dataset.act==='newspaper-page'?`.np-navigation [data-index="${button.dataset.index}"]`:'[data-act="newspaper-reading"]';
    const feature=button.closest('.newspaper-feature');feature.outerHTML=newspaperMarkup(data);fitNewspaper();root.querySelector('.newspaper-feature')?.querySelector(focusSelector)?.focus({preventScroll:true});return;
  }
  if(act==='newspaper-photo'){document.querySelector('#photo-full').src=button.src;document.querySelector('#photo-viewer').showModal();return;}
  if(act==='calendar'){downloadCalendar();return;}
  if(act==='copy-account'){await copyAccount(data,button,notice);return;}
  if(act==='add-account'||act==='remove-account'){
    collect();const gift=data.giftAccounts;
    if(act==='add-account'){
      if(gift.accounts.filter(a=>a.side===button.dataset.side).length>=4)return;
      gift.accounts.push({side:button.dataset.side,relation:button.dataset.side==='GROOM'?'신랑':'신부',bank:'',holder:'',number:''});
    }else gift.accounts.splice(Number(button.dataset.index),1);
    $('#gift-controls').outerHTML=accountControls(data);dirty=true;preview();status();
    if(act==='add-account')document.querySelector(`[data-gift-index="${gift.accounts.length-1}"][data-gift-field="bank"]`)?.focus();return;
  }
  if(act==='photo'){document.querySelector('#photo-full').src=data.gallery[Number(button.dataset.index)];document.querySelector('#photo-viewer').showModal();return;}
  await operation(async()=>{
    if(act==='new'||act==='open'){if(photoBusy)throw new Error('사진 처리 후 이동해 주세요.');if(dirty&&!confirm('저장하지 않은 변경이 있어요. 변경을 버리고 이동할까요?'))return;card=act==='open'?await api('/invitations/'+button.dataset.id):null;data=card?card.data:blank();dirty=false;designer.selected='';editor();}
    if(act==='remove-cover'){data.cover='';dirty=true;thumbs();preview();status();}
    if(act==='remove-photo'){data.gallery.splice(Number(button.dataset.index),1);dirty=true;thumbs();preview();status();}
    if(act==='save'&&await save())notice('청첩장을 저장했어요.');
    if(act==='publish'&&await save())$('#publish-dialog').showModal();
    if(act==='unpublish'){const result=await api('/invitations/'+card.id+'/publication',{method:'PUT',body:{version:card.version,published:false}});card.version=result.version;card.published=false;status();await loadCollection();notice('발행을 취소했어요. 공유 링크로 더 이상 볼 수 없습니다.');}
    if(act==='copy')await copyLink();
    if(act==='share')await share();
  });
});
function shareUrl(){return publicToken?location.href:$('#share-url').value;}
async function copyLink(){try{await navigator.clipboard.writeText(shareUrl());notice('청첩장 링크를 복사했어요.');}catch{if($('#share-url')){$('#share-url').focus();$('#share-url').select();}else $('#public-share-url').hidden=false;notice('링크를 선택해 직접 복사해 주세요.');}}
async function share(){if(navigator.share){try{await navigator.share({title:`${data.groom} ♥ ${data.bride} 결혼합니다`,url:shareUrl()});}catch(e){if(e.name!=='AbortError')await copyLink();}}else await copyLink();}
function downloadCalendar(){
  if(!data.dateTime)return;
  const start=new Date(data.dateTime+'+09:00'),end=new Date(start.getTime()+60*60*1000),stamp=d=>d.toISOString().replace(/[-:]/g,'').replace(/\.\d{3}/,'');
  const text=v=>String(v).replace(/\\/g,'\\\\').replace(/\r?\n/g,'\\n').replace(/,/g,'\\,').replace(/;/g,'\\;');
  // Fold by UTF-8 octets so long Korean venue/address lines remain valid iCalendar.
  const fold=line=>{let out='',row='',size=0;for(const c of line){const bytes=new TextEncoder().encode(c).length;if(size+bytes>73){out+=row+'\r\n';row=' ';size=1;}row+=c;size+=bytes;}return out+row;};
  const lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Cheongmo//Wedding//KO','BEGIN:VEVENT','UID:'+(card?.token||publicToken||'preview')+'@cheongmo','DTSTAMP:'+stamp(new Date()),'DTSTART:'+stamp(start),'DTEND:'+stamp(end),'SUMMARY:'+text(data.groom+' ♥ '+data.bride+' 결혼식'),'LOCATION:'+text(data.venue+' '+data.address),'DESCRIPTION:'+text(data.transport||''),'END:VEVENT','END:VCALENDAR'];
  const href=URL.createObjectURL(new Blob([lines.map(fold).join('\r\n')+'\r\n'],{type:'text/calendar;charset=utf-8'})),a=document.createElement('a');a.href=href;a.download='wedding.ics';a.click();setTimeout(()=>URL.revokeObjectURL(href),1000);
}
$('#cancel-publish').onclick=()=>$('#publish-dialog').close();
$('#confirm-publish').onclick=()=>operation(async()=>{card=await api('/invitations/'+card.id+'/publication',{method:'PUT',body:{version:card.version,published:true}});$('#publish-dialog').close();status();await loadCollection();notice('청첩장을 발행했어요. 링크를 공유해 보세요.');});
window.addEventListener('beforeunload',e=>{if(dirty||photoBusy){e.preventDefault();e.returnValue='';}});
try{
  if(publicToken){document.body.classList.add('public-page');$('#studio-header').hidden=true;data=await api('/public-invitations/'+encodeURIComponent(publicToken));document.title=data.groom+' ♥ '+data.bride+' 결혼합니다';root.innerHTML=`${cardMarkup(data)}<div class="public-share"><button data-act="copy">링크 복사</button><button data-act="share">청첩장 공유 ↗</button><input id="public-share-url" aria-label="청첩장 링크" readonly hidden value="${escape(location.href)}"></div>`;}
  else {const user=await api('/me');if(user.guest)throw new Error('청첩장은 호스트 계정으로 만들 수 있어요.');editor();

  }
}catch(e){root.innerHTML=`<section class="empty"><span>✳</span><h1>${publicToken?'청첩장을 확인해 주세요':'호스트로 시작해 주세요'}</h1><p>${escape(e.message)}</p><a href="/">${publicToken?'청모 홈으로':'청모에서 가입·로그인하기'} ↗</a></section>`;}
finally{root.setAttribute('aria-busy','false');}
fitNewspaper();

root.addEventListener('pointerdown',e=>designer.down(e));
root.addEventListener('pointermove',e=>designer.move(e));
root.addEventListener('pointerup',e=>designer.up(e));
root.addEventListener('pointercancel',e=>designer.up(e));
root.addEventListener('keydown',e=>designer.key(e));
document.fonts.addEventListener('loadingdone',()=>{if(designer.drag)return;if(publicToken){const canvas=document.querySelector('.free-design-canvas');if(canvas)canvas.outerHTML=designMarkup(data,selfUrl());}else if(document.querySelector('#preview'))preview();});
root.addEventListener('keydown',event=>{if(event.target.dataset.act==='newspaper-photo'&&['Enter',' '].includes(event.key)){event.preventDefault();event.target.click();}});

document.querySelector('#photo-close').onclick=()=>document.querySelector('#photo-viewer').close();
