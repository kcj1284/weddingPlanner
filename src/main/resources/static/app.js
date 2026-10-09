const $ = (s, root = document) => root.querySelector(s);
const app = $('#app'), modal = $('#modal'), content = $('#modal-content');
const state = { user: null, meetings: [], meeting: null, tab: 'vote', csrf: null, demo: false, providers: [] };
const draftVotes = new Map();
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const url = value => { try { const u = new URL(value); return ['https:', 'http:'].includes(u.protocol) ? esc(u.href) : ''; } catch { return ''; } };
const reserved = { UNKNOWN:'예약 미확인', AVAILABLE:'예약 가능 확인', BOOKED:'예약 완료' };
const dateLabel = value => { const date = new Date(value + '+09:00'); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('ko-KR', {timeZone:'Asia/Seoul',month:'long',day:'numeric',weekday:'short',hour:'2-digit',minute:'2-digit',hour12:false}).format(date); };
let toastTimer;
function toast(message) { $('#toast').textContent=message; $('#toast').classList.add('visible'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('#toast').classList.remove('visible'),3500); }
async function csrf() { state.csrf = await (await fetch('/api/csrf')).json(); }
async function api(path, options={}) {
  const method=options.method||'GET'; const headers={...(options.headers||{})};
  if(method!=='GET') { if(!state.csrf) await csrf(); headers[state.csrf.headerName]=state.csrf.token; }
  let body=options.body;
  if(body && !(body instanceof URLSearchParams)) { headers['Content-Type']='application/json'; body=JSON.stringify(body); }
  const response=await fetch('/api'+path,{...options,method,headers,body});
  if(response.status===204) return null;
  const data=await response.json().catch(()=>({message:'잠시 후 다시 시도해 주세요.'}));
  if(!response.ok) { const error=new Error(data.message||'요청을 처리하지 못했어요.'); error.status=response.status; throw error; }
  return data;
}
function dialog(html) { content.innerHTML=html; const title=$('h2',content); if(title){title.id='dialog-title';modal.setAttribute('aria-labelledby',title.id);} if(!modal.open)modal.showModal(); }
function hero(loggedIn=false) { return `<section class="hero"><div class="hero-copy"><span class="eyebrow">GOOD NEWS, GOOD COMPANY</span><h1>좋은 소식은,<br>좋은 사람들과 함께.</h1><p>언제 만날지, 어디서 만날지.<br>우리의 청첩장 모임을 가볍게 준비해요.</p><button class="button" data-action="${loggedIn?'new':'signup'}">${loggedIn?'새로운 청모 만들기':'우리의 청모 시작하기'} <span>↗</span></button></div><div class="hero-art" aria-hidden="true"><div class="envelope"></div><div class="invitation"><span class="invite-flower">✳</span><p class="invite-script">You're invited</p><div class="invite-line"></div><span class="invite-text">WITH LOVE, ALWAYS</span></div><span class="sprig">❧</span></div></section>`; }
function accountView() { document.querySelectorAll('nav [data-action="new"]').forEach(b=>b.hidden=Boolean(state.user?.guest)); $('#account').innerHTML=state.user ? `<div class="user-chip"><span class="avatar">${esc(state.user.displayName.slice(0,1))}</span><span>${esc(state.user.displayName)}님</span><button class="text-button" data-action="logout">로그아웃</button></div>` : '<button class="text-button" data-action="login">로그인 ↗</button>'; }
function welcome() {
  accountView(); app.innerHTML=`${new URLSearchParams(location.search).has('invite')?'<div class="join-banner"><div><h3>청모에 초대받으셨네요!</h3><p>로그인하거나 가입한 뒤 초대받은 모임에 참여해 주세요.</p></div><button class="button" data-action="login">참여하러 가기 ↗</button></div>':''}${hero()}<div class="section-top"><h2>만남까지, 세 걸음이면 충분해요.</h2><span class="eyebrow">HOW IT WORKS</span></div><div class="welcome-steps"><article class="step"><span class="step-number">01</span><h3>소중한 사람들 초대</h3><p>모임을 만들고<br>링크 하나로 초대해요.</p></article><article class="step"><span class="step-number">02</span><h3>함께 정하는 약속</h3><p>가능한 시간과 장소에<br>마음을 모아 투표해요.</p></article><article class="step"><span class="step-number">03</span><h3>기분 좋은 만남</h3><p>호스트가 장소를 확인하고<br>우리의 약속을 완성해요.</p></article></div>${state.demo?'<div class="demo-panel"><div><h3>먼저 둘러보고 싶다면</h3><p>예시 모임으로 호스트와 게스트의 화면을 체험해 보세요.</p></div><div class="demo-actions"><button class="button secondary" data-action="demo-host">호스트로 체험</button><button class="button secondary" data-action="demo-guest">게스트로 체험</button></div></div>':''}`;
}
async function loadMeetings(selectId) {
  state.meetings=await api('/meetings');
  const selected=selectId||state.meeting?.id||new URLSearchParams(location.search).get('meeting')||state.meetings[0]?.id;
  state.meeting=selected && state.meetings.some(m=>m.id===selected) ? await api('/meetings/'+selected) : null;
  if(state.meeting?.status==='CONFIRMED')draftVotes.delete(state.meeting.id);
  if(state.meeting?.role!=='HOST')state.tab='vote'; render();
}
function cards() {return state.meetings.map(m=>`<button class="meeting-card ${state.meeting?.id===m.id?'selected':''}" aria-pressed="${state.meeting?.id===m.id}" data-action="select" data-id="${esc(m.id)}"><div class="card-header"><span class="badge ${m.role==='HOST'?'coral':'neutral'}">${m.role==='HOST'?'호스트 · 내가 여는 모임':'게스트 · 초대받은 모임'}</span><span class="badge">${m.status==='OPEN'?'● 투표 중':'✓ 약속 확정'}</span></div><h3>${esc(m.title)}</h3><div class="meta"><span>⌖ ${esc(m.area)}</span><span>♧ ${m.memberCount}명 참여</span><span>↗</span></div></button>`).join('');}
function render() {
  accountView();
  app.innerHTML=`<div class="intro-head"><div><span class="eyebrow">MY GATHERINGS</span><h1>${esc(state.user.displayName)}님의 청모</h1><p class="muted">설레는 소식을 전할 만남을 준비해요.</p></div>${state.user.guest?'':'<button class="button secondary" data-action="new">＋ 모임 만들기</button>'}</div><div class="section-top"><h2>함께하는 모임 <span class="small">${state.meetings.length}개의 소중한 약속</span></h2><button class="text-button" data-action="refresh">↻ 새로고침</button></div>${state.meetings.length?`<div class="meeting-list">${cards()}</div>`:'<div class="empty">아직 준비 중인 청모가 없어요.<br>새 모임을 만들거나 초대 링크로 참여해 보세요.<br><button class="button soft" data-action="join">초대받은 모임 참여</button></div>'}${state.meeting?detail():''}`; updateVoteSummary();
  if(new URLSearchParams(location.search).has('invite'))joinDialog(new URLSearchParams(location.search).get('invite'));
}
function detail() {
  const m=state.meeting, host=m.role==='HOST', open=m.status==='OPEN';
  const finalTime=m.options.find(o=>o.id===m.finalTimeId), finalPlace=m.options.find(o=>o.id===m.finalPlaceId);
  return `<section class="detail" id="meeting-detail"><div class="detail-heading"><div><span class="eyebrow">${host?'호스트 · 내가 준비하는 청모':'게스트 · 함께하는 청모'}</span><h2>${esc(m.title)}</h2><p class="muted">${esc(m.description)}</p></div>${host?'<button class="button secondary" data-action="share">↗ 초대하기</button>':''}</div><div class="stats"><div class="stat"><strong>${m.memberCount}<span>명</span></strong>함께하는 사람</div><div class="stat"><strong>${m.responseCount}<span>/ ${m.memberCount}</span></strong>투표에 응답한 사람</div><div class="stat"><strong>${m.expectedCount}<span>명</span></strong>예상 모임 인원</div></div>${!open?`<div class="confirmed"><div><span class="eyebrow">IT'S A DATE!</span><h3>우리의 약속이 정해졌어요.</h3><p class="muted">${esc(dateLabel(finalTime?.title))}<br>${esc(finalPlace?.title)} · ${reserved[finalPlace?.reservation]||'예약 미확인'}</p></div>${host?'<button class="button secondary" data-action="reopen">투표 다시 열기</button>':''}</div>`:''}<div class="tabs" role="tablist" aria-label="모임 기능"><button class="tab ${state.tab==='vote'?'active':''}" id="tab-vote" role="tab" aria-controls="meeting-panel" tabindex="${state.tab==='vote'?0:-1}" aria-selected="${state.tab==='vote'}" data-action="tab" data-tab="vote">약속 정하기</button>${host?`<button class="tab ${state.tab==='places'?'active':''}" id="tab-places" role="tab" aria-controls="meeting-panel" tabindex="${state.tab==='places'?0:-1}" aria-selected="${state.tab==='places'}" data-action="tab" data-tab="places">장소 찾기</button><button class="tab ${state.tab==='manage'?'active':''}" id="tab-manage" role="tab" aria-controls="meeting-panel" tabindex="${state.tab==='manage'?0:-1}" aria-selected="${state.tab==='manage'}" data-action="tab" data-tab="manage">모임 관리</button>`:''}</div><div id="meeting-panel" role="tabpanel" aria-labelledby="tab-${state.tab}">${state.tab==='vote'?poll():state.tab==='places'?places():manage()}</div></section>`;
}
function optionCard(o) {
  const m=state.meeting, selected=(draftVotes.get(m.id)||m.myVotes).includes(o.id);
  return `<article class="option-card ${selected?'chosen':''}"><label class="option-label"><input type="checkbox" name="optionIds" value="${esc(o.id)}" ${selected?'checked':''} ${m.status!=='OPEN'?'disabled':''}><span class="option-content"><strong>${esc(o.kind==='TIME'?dateLabel(o.title):o.title)}</strong><p>${esc(o.detail)}</p></span></label>${o.kind==='PLACE'?`<div class="option-links">${url(o.mapUrl)?`<a href="${url(o.mapUrl)}" target="_blank" rel="noopener noreferrer">⌖ 지도 보기 ↗</a>`:''}${o.phone?`<a href="tel:${esc(o.phone.replace(/[^0-9+]/g,''))}">☏ 예약 문의</a>`:''}</div>`:''}<div class="option-footer"><span>${o.kind==='PLACE'?reserved[o.reservation]:'가능한 사람'}</span><span class="votes-count">${o.votes}명 선택</span></div>${o.reservationNote?`<p class="reservation-note">${esc(o.reservationNote)}</p>`:''}<progress class="vote-progress" max="${Math.max(1,m.memberCount)}" value="${o.votes}" aria-label="${o.votes}명 선택"></progress></article>`;
}
function poll() {
  const m=state.meeting,host=m.role==='HOST',open=m.status==='OPEN';
  return `${open?'<div class="hint">가능한 날짜와 마음에 드는 장소를 모두 선택해 주세요. 투표는 언제든 수정할 수 있어요.</div>':''}<form id="vote-form"><div class="poll-grid">${['TIME','PLACE'].map(kind=>`<section class="panel"><div class="panel-head"><h3><span class="icon">${kind==='TIME'?'▦':'⌖'}</span> ${kind==='TIME'?'언제 만날까요?':'어디서 만날까요?'}</h3>${host&&open?`<button type="button" class="add-link" data-action="add-${kind.toLowerCase()}">＋ 후보 추가</button>`:''}</div><p class="muted">${kind==='TIME'?'함께할 수 있는 날짜와 시간에 투표해요.':'좋은 이야기를 나눌 장소를 골라요.'}</p>${m.options.filter(o=>o.kind===kind).map(optionCard).join('')||`<div class="empty">${kind==='TIME'?'일정':'장소'} 후보를 기다리고 있어요.${host?'<br>위의 후보 추가로 시작해 주세요.':''}</div>`}</section>`).join('')}</div>${open?'<div class="save-bar"><div><h3>나의 가능한 약속</h3><p id="vote-summary" role="status"></p><p id="vote-hint">선택을 바꾸면 투표 저장을 눌러 주세요.</p></div><button class="button" type="submit">나의 투표 저장 ✓</button></div>':''}</form><p class="fine-print">예약 가능 확인은 호스트가 직접 확인한 정보예요. 실제 방문 인원과 날짜로 다시 확인해 주세요.${state.demo&&m.title==='우리, 청첩장 나오는 날'?'<br>현재 모임의 장소 후보는 기능 체험용 예시입니다.':''}</p>`;
}
function places() {
  const m=state.meeting;
  return `<div class="panel places-panel"><span class="eyebrow">FIND A PLACE</span><div class="section-top"><h2>우리 모임에 어울리는 곳</h2><button class="button secondary" data-action="add-place" ${m.status!=='OPEN'?'disabled':''}>＋ 직접 추가</button></div><p class="muted">지역과 장소를 검색하고, 전화나 지도 페이지에서 예약을 확인해요.</p><form id="search-form" class="search-form"><label>지역·검색어<input name="q" value="${esc(m.area)}" maxlength="100" required placeholder="예: 성수 이탈리안"></label><label class="type-label">장소 종류<select name="type"><option value="FD6">식당</option><option value="CE7">카페</option></select></label><button class="button" type="submit">장소 검색 ↗</button></form><div class="hint">장소 검색은 예약 잔여석 조회가 아니에요. ${m.expectedCount}명이 함께할 수 있는지, 원하는 날짜에 예약할 수 있는지 문의해 주세요.</div><div id="search-results"><div class="empty">어떤 동네에서 만나고 싶으세요?<br>검색 결과를 투표 후보에 추가할 수 있어요.</div></div></div>`;
}
function manage() {
  const m=state.meeting;
  return `<section class="panel"><span class="eyebrow">HOST'S NOTE</span><h2 class="management-title">우리의 약속 완성하기</h2><p class="muted">득표수를 참고해 최종 날짜와 장소를 정해 주세요.</p>${m.status==='OPEN'?`<form id="confirm-form" class="management-form"><label>최종 날짜·시간<select name="timeId" required><option value="">날짜와 시간을 선택해 주세요</option>${m.options.filter(o=>o.kind==='TIME').map(o=>`<option value="${esc(o.id)}">${esc(dateLabel(o.title))} · ${o.votes}표</option>`).join('')}</select></label><label>최종 장소<select name="placeId" required><option value="">장소를 선택해 주세요</option>${m.options.filter(o=>o.kind==='PLACE').map(o=>`<option value="${esc(o.id)}">${esc(o.title)} · ${o.votes}표</option>`).join('')}</select></label><p class="fine-print">확정하면 투표가 마감돼요. 예약 상태는 별도로 확인하고 기록해 주세요. 필요하면 투표를 다시 열 수 있어요.</p><button class="button" type="submit">날짜와 장소 확정하기 ✓</button></form>`:'<div class="hint">약속이 확정되었어요. 일정 변경이 필요하면 투표를 다시 열어 주세요.</div>'}<div class="section-top"><h3>장소별 예약 확인</h3><span class="small">호스트가 직접 확인한 상태</span></div>${m.options.filter(o=>o.kind==='PLACE').map(o=>`<div class="place-status"><div><h3>${esc(o.title)}</h3><p class="small">${reserved[o.reservation]} ${o.reservationNote?'· '+esc(o.reservationNote):''}</p></div><button class="button secondary" aria-label="${esc(o.title)} 예약 상태 기록" data-action="reservation" data-id="${esc(o.id)}">상태 기록</button></div>`).join('')||'<div class="empty">장소 후보를 먼저 추가해 주세요.</div>'}</section>`;
}
function authDialog(signup=false) {
  dialog(`${socialButtons()}<h2>${signup?'반가워요, 함께 준비해요.':'다시 만나 반가워요.'}</h2><p class="muted">${signup?'내 모임을 열거나 초대받은 청모에 참여해 보세요.':'로그인하고 우리의 약속을 이어가세요.'}</p><form id="auth-form" data-signup="${signup}">${signup?'<label>이름<input name="displayName" autocomplete="name" maxlength="30" required placeholder="모임에서 사용할 이름"></label>':''}<label>아이디<input name="username" autocomplete="username" pattern="[a-z0-9_]{3,30}" minlength="3" maxlength="30" required placeholder="영문 소문자·숫자·밑줄 3–30자"></label><label>비밀번호<input name="password" type="password" autocomplete="${signup?'new-password':'current-password'}" minlength="8" maxlength="72" required placeholder="8자 이상"></label><p class="form-error" role="alert"></p><button class="button" type="submit">${signup?'가입하고 시작하기':'로그인'} ↗</button></form><button class="auth-switch" data-action="${signup?'login':'signup'}">${signup?'이미 계정이 있어요 · 로그인':'처음 오셨나요? · 회원가입'}</button>`);
}
function newDialog() {
  dialog('<h2>새로운 청모를 열어요.</h2><p class="muted">소중한 사람들과 함께할 만남을 준비해요.</p><form id="new-form"><label>모임 이름<input name="title" required maxlength="80" placeholder="예: 지은과 도윤의 청첩장 모임"></label><label>초대의 한마디<textarea name="description" maxlength="500" placeholder="좋은 소식을 전하고 싶어요. 함께 만나요!"></textarea></label><div class="row"><label>만나고 싶은 지역<input name="area" required maxlength="80" placeholder="서울 성수"></label><label>예상 인원<input name="expectedCount" type="number" min="2" max="100" value="6" required></label></div><p class="form-error" role="alert"></p><button class="button" type="submit">모임 만들고 초대하기 ↗</button></form>');
}
function joinDialog(token='') {dialog(`<h2>초대코드로 참여해요.</h2><p class="muted">호스트에게 받은 개인 초대코드를 입력해 주세요. 가입 없이 참여할 수 있어요.</p><form id="join-form"><label>초대코드<input name="token" value="${esc(token)}" required maxlength="500" autocomplete="off" spellcheck="false" placeholder="예: A7K3M9" autocapitalize="characters"></label><p class="fine-print">대문자 영문·숫자 6자리예요. 전용 초대 링크를 붙여 넣어도 돼요.</p><p class="form-error" role="alert"></p><button class="button" type="submit">초대코드 확인하기 ↗</button></form>`);}
function guestJoinDialog(token,preview) {dialog(`<h2>${esc(preview.title)}</h2><p class="muted">초대코드를 확인했어요. 가입 없이 참여해요.</p><form id="guest-form" data-token="${esc(token)}">${preview.joined?'<p>기존 이름과 투표로 이어서 참여할 수 있어요.</p>':'<label>모임에서 사용할 이름<input name="displayName" autocomplete="nickname" required maxlength="30" placeholder="친구들이 알아볼 수 있는 이름"></label>'}<p class="fine-print">코드는 한 사람의 참여 권한을 담고 있으니 다른 사람에게 공유하지 마세요.${state.user&&!state.user.guest?' 참여하면 호스트 계정에서 게스트 화면으로 전환됩니다.':''}</p><p class="form-error" role="alert"></p><button class="button" type="submit">${preview.joined?'이어서 참여하기':'이름으로 참여하기'} ↗</button></form><button class="auth-switch" data-action="join">다른 초대코드 입력하기</button>`);}

function addDialog(kind, prefill={}) {
  dialog(`<h2>${kind==='TIME'?'언제 만나면 좋을까요?':'좋은 장소를 제안해요.'}</h2><p class="muted">${kind==='TIME'?'날짜와 시간을 함께 선택해 주세요. 한국 시간 기준이에요.':'예약 가능 여부는 먼저 미확인으로 표시해요.'}</p><form id="option-form" data-kind="${kind}"><label>${kind==='TIME'?'날짜·시간':'장소 이름'}<input name="title" ${kind==='TIME'?'type="datetime-local"':'maxlength="120"'} value="${esc(prefill.name||'')}" required></label><label>${kind==='TIME'?'한마디 (선택)':'주소·장소 설명 (선택)'}<input name="detail" maxlength="300" value="${esc(prefill.address||'')}" placeholder="${kind==='TIME'?'예: 퇴근 후 저녁':'예: 성수역 3번 출구 근처'}"></label>${kind==='PLACE'?`<label>지도 상세 링크 (선택)<input name="mapUrl" type="url" maxlength="500" value="${esc(prefill.url||'')}" placeholder="https://…"></label><label>문의 전화번호 (선택)<input name="phone" maxlength="40" value="${esc(prefill.phone||'')}" placeholder="02-000-0000"></label>`:''}<p class="form-error" role="alert"></p><button class="button" type="submit">투표 후보에 추가하기 ＋</button></form>`);
}
async function login(username,password) {await api('/login',{method:'POST',body:new URLSearchParams({username,password})});await csrf();state.user=await api('/me');draftVotes.clear();state.meeting=null;state.tab='vote';await loadMeetings();}
async function refreshed(message) {await loadMeetings(state.meeting?.id);if(message)toast(message);}
document.addEventListener('click',async event=>{
  const button=event.target.closest('[data-action]');if(!button)return;
  const action=button.dataset.action;
  try {
    if(action==='login'||action==='signup'){authDialog(action==='signup');return;}
    if(action==='demo-host'||action==='demo-guest'){button.disabled=true;await login(action==='demo-host'?'demo_host':'demo_guest','cheongmo-demo');modal.close();toast('예시 모임을 체험하고 있어요.');return;}
    if(action==='logout'){await api('/logout',{method:'POST'});await csrf();state.user=null;state.meeting=null;draftVotes.clear();welcome();return;}
    if(action==='join'){joinDialog();return;}
    if(!state.user){authDialog(action==='new');return;}
    if(action==='home'){state.tab='vote';await refreshed();window.scrollTo({top:0,behavior:'smooth'});}
    if(action==='new'){if(state.user.guest)authDialog(true);else newDialog();}
    if(action==='refresh')await refreshed('최신 투표 현황을 불러왔어요.');
    if(action==='select'){state.meeting=await api('/meetings/'+button.dataset.id);state.tab='vote';render();$('#meeting-detail').scrollIntoView({behavior:'smooth',block:'start'});}
    if(action==='tab'){state.tab=button.dataset.tab;render();document.getElementById('tab-'+state.tab).focus({preventScroll:true});$('#meeting-detail').scrollIntoView({behavior:'smooth',block:'start'});}
    if(action==='add-time')addDialog('TIME');
    if(action==='add-place')addDialog('PLACE');
    if(action==='share')await shareDialog();
    if(action==='revoke-link'){dialog(`<h2>초대를 취소할까요?</h2><p>이 게스트의 접근 권한과 기존 투표가 삭제됩니다.</p><button class="button" data-action="revoke-confirm" data-id="${esc(button.dataset.id)}">초대 취소하기</button>`);}
    if(action==='revoke-confirm'){await api('/meetings/'+state.meeting.id+'/guest-links/'+button.dataset.id,{method:'DELETE'});await refreshed();await shareDialog();}
    if(action==='copy'){const input=document.getElementById(button.dataset.target||'invite-link');try{await navigator.clipboard.writeText(input.value);toast('초대 정보를 복사했어요.');}catch{input.select();toast('선택된 초대 정보를 복사해 주세요.');}}
    if(action==='reopen'){dialog('<h2>투표를 다시 열까요?</h2><p class="muted">확정된 날짜와 장소가 해제돼요. 기존 후보와 투표는 유지됩니다.</p><button class="button" data-action="reopen-confirm">투표 다시 열기</button>');}
    if(action==='reopen-confirm'){await api('/meetings/'+state.meeting.id+'/reopen',{method:'POST'});modal.close();await refreshed('투표를 다시 열었어요.');}
    if(action==='reservation'){
      const o=state.meeting.options.find(o=>o.id===button.dataset.id);
      dialog(`<h2>예약 확인을 기록해요.</h2><p class="muted">${esc(o.title)}</p><form id="reservation-form" data-id="${esc(o.id)}"><label>확인 상태<select name="status">${Object.entries(reserved).map(([k,v])=>`<option value="${k}" ${k===o.reservation?'selected':''}>${v}</option>`).join('')}</select></label><label>확인 메모<textarea name="note" maxlength="300" placeholder="예: 10월 8일 전화 확인 / 6명 예약">${esc(o.reservationNote)}</textarea></label><p class="form-error" role="alert"></p><button class="button" type="submit">확인 내용 저장 ✓</button></form>`);
    }
    if(action==='result-add')addDialog('PLACE',JSON.parse(button.dataset.place));
  }catch(error){toast(error.message);}finally{button.disabled=false;}
});
document.addEventListener('submit',async event=>{
  const form=event.target;if(!(form instanceof HTMLFormElement))return;event.preventDefault();
  const fields=Object.fromEntries(new FormData(form));const submit=$('button[type="submit"]',form);const failure=$('.form-error',form);
  form.setAttribute('aria-busy','true');if(submit)submit.disabled=true;if(failure)failure.textContent='';
  try{
    if(form.id==='guest-form'){const token=form.dataset.token||new URLSearchParams(location.search).get('guest');const joined=await api('/guest-session',{method:'POST',body:{token,displayName:fields.displayName||''}});await csrf();state.user=await api('/me');draftVotes.clear();state.meeting=null;history.replaceState(null,'','/?guest='+encodeURIComponent(token)+'&meeting='+joined.meetingId);modal.close();await loadMeetings(joined.meetingId);toast('모임에 참여했어요. 가능한 약속을 골라 주세요.');}
    if(form.id==='guest-link-form'){const created=await api('/meetings/'+state.meeting.id+'/guest-links',{method:'POST',body:fields});await shareDialog(location.origin+'/?guest='+encodeURIComponent(created.token),created.code);}
    if(form.id==='auth-form') {if(form.dataset.signup==='true')await api('/register',{method:'POST',body:fields});await login(fields.username,fields.password);if(!new URLSearchParams(location.search).has('invite'))modal.close();}
    if(form.id==='new-form'){const created=await api('/meetings',{method:'POST',body:{...fields,expectedCount:Number(fields.expectedCount)}});modal.close();await loadMeetings(created.id);toast('청모를 만들었어요. 친구들을 초대해 주세요.');$('#meeting-detail').scrollIntoView({behavior:'smooth'});}
    if(form.id==='join-form'){let token=fields.token.trim();if(token.includes('?')){try{token=new URL(token).searchParams.get('guest')||'';}catch{throw new Error('초대코드 또는 전용 링크를 확인해 주세요.');}}const preview=await api('/guest-preview/'+encodeURIComponent(token));guestJoinDialog(token,preview);}
    if(form.id==='option-form'){await api('/meetings/'+state.meeting.id+'/options',{method:'POST',body:{kind:form.dataset.kind,title:fields.title,detail:fields.detail,mapUrl:fields.mapUrl||'',phone:fields.phone||''}});modal.close();await refreshed('투표 후보를 추가했어요.');}
    if(form.id==='vote-form'){await api('/meetings/'+state.meeting.id+'/votes',{method:'PUT',body:{optionIds:new FormData(form).getAll('optionIds')}});draftVotes.delete(state.meeting.id);await refreshed('나의 투표를 저장했어요.');$('#meeting-detail').scrollIntoView({block:'start'});}
    if(form.id==='confirm-form'){await api('/meetings/'+state.meeting.id+'/confirm',{method:'POST',body:fields});state.tab='vote';await refreshed('우리의 약속이 정해졌어요!');}
    if(form.id==='reservation-form'){await api('/meetings/'+state.meeting.id+'/options/'+form.dataset.id+'/reservation',{method:'PUT',body:fields});modal.close();await refreshed('예약 확인 내용을 기록했어요.');}
    if(form.id==='search-form'){
      const result=$('#search-results');result.innerHTML='<div class="loading">함께할 장소를 찾고 있어요…</div>';
      try {const data=await api('/meetings/'+state.meeting.id+'/places?'+new URLSearchParams(fields));
        result.innerHTML=`<div class="hint">${esc(data.message)}</div>${data.places.length?`<div class="results">${data.places.map(p=>`<article class="panel result"><span class="badge neutral">${fields.type==='FD6'?'식당':'카페'} · 예약 미확인</span><h3>${esc(p.name)}</h3><p>${esc(p.address)}<br>${esc(p.phone||'전화번호 정보 없음')}</p><div class="result-actions">${url(p.url)?`<a class="text-button" href="${url(p.url)}" target="_blank" rel="noopener noreferrer">지도에서 확인 ↗</a>`:''}<button class="button soft" type="button" data-action="result-add" data-place="${esc(JSON.stringify(p))}" ${state.meeting.status!=='OPEN'?'disabled':''}>후보 추가 ＋</button></div></article>`).join('')}</div>`:`<div class="empty">${data.configured?'검색 결과가 없거나 연결이 원활하지 않아요. 다른 검색어로 다시 찾아보세요.':'지도에서 장소를 확인한 뒤 직접 추가할 수 있어요.'}<br><a class="button secondary" href="https://map.kakao.com/?q=${encodeURIComponent(fields.q)}" target="_blank" rel="noopener noreferrer">카카오맵에서 찾아보기 ↗</a><br><button type="button" class="add-link" data-action="add-place" ${state.meeting.status!=='OPEN'?'disabled':''}>장소 직접 추가</button></div>`}`;
      }catch(error){result.innerHTML=`<div class="empty">${esc(error.message)}</div>`;}
    }
  }catch(error){if(failure)failure.textContent=error.message;else toast(error.message);}
  finally{form.setAttribute('aria-busy','false');if(submit)submit.disabled=false;}
});
function updateVoteSummary() {
  const summary=$('#vote-summary'), hint=$('#vote-hint'), m=state.meeting;
  if(!summary||!m)return;
  const ids=draftVotes.get(m.id)||m.myVotes;
  const count=kind=>m.options.filter(o=>o.kind===kind&&ids.includes(o.id)).length;
  summary.textContent=`일정 ${count('TIME')}개 · 장소 ${count('PLACE')}개 선택`;
  const dirty=ids.length!==m.myVotes.length||ids.some(id=>!m.myVotes.includes(id));
  if(!dirty)draftVotes.delete(m.id);
  hint.textContent=dirty?'아직 저장하지 않았어요. 투표 저장을 눌러 주세요.':ids.length?'저장된 선택이에요. 언제든 수정할 수 있어요.':'모두 해제하고 저장하면 투표가 철회돼요.';
  summary.closest('.save-bar').classList.toggle('unsaved',dirty);
}
document.addEventListener('change',event=>{
  if(event.target.name==='optionIds'){
    event.target.closest('.option-card').classList.toggle('chosen',event.target.checked);
    draftVotes.set(state.meeting.id,new FormData($('#vote-form')).getAll('optionIds'));
    updateVoteSummary();
  }
});
document.addEventListener('keydown',event=>{
  if(!event.target.matches('[role="tab"]')||!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
  event.preventDefault();
  const tabs=[...document.querySelectorAll('[role="tab"]')], index=tabs.indexOf(event.target);
  const next=event.key==='Home'?0:event.key==='End'?tabs.length-1:(index+(event.key==='ArrowRight'?1:-1)+tabs.length)%tabs.length;
  state.tab=tabs[next].dataset.tab;render();document.getElementById('tab-'+state.tab).focus({preventScroll:true});
});
window.addEventListener('beforeunload',event=>{
  if([...draftVotes].some(([id,ids])=>{const m=state.meeting;return id!==m?.id||ids.length!==m.myVotes.length||ids.some(v=>!m.myVotes.includes(v));})){event.preventDefault();event.returnValue='';}
});
$('#close-modal').onclick=()=>modal.close();
modal.addEventListener('click',event=>{if(event.target===modal){const rect=modal.getBoundingClientRect();if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)modal.close();}});

function socialButtons() {
  const names={kakao:'카카오',google:'구글',naver:'네이버'};
  return `<div class="social-login"><p>호스트는 소셜 계정으로 가입·로그인할 수 있어요.</p>${state.providers.map(p=>p.enabled?`<a class="button secondary" href="/oauth2/authorization/${p.id}">${names[p.id]}로 계속하기</a>`:`<button class="button secondary" disabled>${names[p.id]} · 연동 준비 중</button>`).join('')}<p class="fine-print">${state.providers.some(p=>p.enabled)?'처음 로그인하면 호스트 계정이 만들어집니다.':'소셜 연동 키가 설정되면 활성화됩니다. 아래 계정으로도 시작할 수 있어요.'}</p></div>`;
}
async function guestEntry() {
  accountView();
  try {
    const preview=await api('/guest-preview/'+encodeURIComponent(new URLSearchParams(location.search).get('guest')));
    app.innerHTML=`<section class="panel guest-entry"><span class="eyebrow">YOU'RE INVITED</span><h1>${esc(preview.title)}</h1><p>가입 없이 초대받은 청모에 참여해요.</p><form id="guest-form">${preview.joined?'<p>다시 오셨네요. 기존 이름과 투표로 이어서 참여해요.</p>':'<label>모임에서 사용할 이름<input name="displayName" autocomplete="nickname" required maxlength="30" placeholder="친구들이 알아볼 수 있는 이름"></label>'}<p class="fine-print">이 링크는 한 사람의 참여 권한을 담고 있어요. 다른 사람에게 공유하지 마세요. ${state.user?'참여하면 현재 계정에서 게스트 화면으로 전환됩니다.':''}</p><p class="form-error" role="alert"></p><button class="button" type="submit">${preview.joined?'이어서 참여하기':'이름으로 참여하기'} ↗</button></form></section>`;
  } catch(error) {app.innerHTML=`<section class="panel guest-entry"><h1>초대 링크를 확인해 주세요.</h1><p>${esc(error.message)}</p><p>호스트에게 새 전용 링크를 요청해 주세요.</p></section>`;}
}
async function shareDialog(link='',code='') {
  const links=await api('/meetings/'+state.meeting.id+'/guest-links');
  dialog(`<h2>한 사람씩 초대해요.</h2><p class="muted">친구마다 초대코드와 전용 링크를 만들어요. 가입 없이 이름만 입력하면 참여할 수 있어요.</p><form id="guest-link-form"><label>초대 대상 이름<input name="label" required maxlength="30" placeholder="예: 민지에게"></label><p class="form-error" role="alert"></p><button class="button" type="submit">초대코드·링크 만들기</button></form>${link?`<label for="invite-code">방금 만든 초대코드</label><div class="copy-row"><input id="invite-code" value="${esc(code)}" readonly><button class="button" data-action="copy" data-target="invite-code">코드 복사</button></div><label for="invite-link">방금 만든 전용 링크</label><div class="copy-row"><input id="invite-link" value="${esc(link)}" readonly><button class="button" data-action="copy">복사</button></div><p class="fine-print">지금 복사해 전달해 주세요. 보안을 위해 코드와 링크 원문은 다시 표시되지 않습니다. 30일 동안 접속할 수 있어요.</p>`:''}<h3>초대 목록</h3>${links.map(l=>`<div class="guest-link-row"><div><strong>${esc(l.label)}</strong><p class="small">${l.revoked?'초대 취소됨':l.displayName?esc(l.displayName)+' · 참여 중':'아직 참여 전'} · ${esc(l.expiresAt.slice(0,10))} 만료</p></div>${l.revoked?'':`<button class="text-button" data-action="revoke-link" data-id="${esc(l.id)}">초대 취소</button>`}</div>`).join('')||'<p class="muted">아직 만든 초대가 없어요.</p>'}`);
}

try{
  await csrf();state.demo=(await api('/config')).demoEnabled;state.providers=await api('/auth/providers');
  try{state.user=await api('/me');}catch(error){if(error.status!==401)throw error;}
  if(new URLSearchParams(location.search).has('guest'))await guestEntry();else if(state.user)await loadMeetings();else welcome();
  if(new URLSearchParams(location.search).has('authError'))toast('소셜 로그인에 실패했어요. 다시 시도해 주세요.');
}catch(error){app.innerHTML=`<div class="empty">${esc(error.message)}<br><button class="button secondary" data-action="reload">다시 불러오기</button></div>`;document.querySelector('[data-action="reload"]').onclick=()=>location.reload();}
finally{app.setAttribute('aria-busy','false');}
